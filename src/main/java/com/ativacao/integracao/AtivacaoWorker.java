package com.ativacao.integracao;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.SolicitacaoRepository;
import com.ativacao.dominio.StatusSolicitacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "provider.worker", name = "enabled", matchIfMissing = true)
public class AtivacaoWorker {

    private static final Logger log = LoggerFactory.getLogger(AtivacaoWorker.class);

    private final SolicitacaoRepository repository;
    private final ProviderClient providerClient;
    private final ProviderProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Random random = new Random();

    public AtivacaoWorker(SolicitacaoRepository repository, ProviderClient providerClient,
                           ProviderProperties properties, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.providerClient = providerClient;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${provider.worker.intervalo:2s}")
    public void processarLote() {
        List<UUID> ids = buscarIdsElegiveis();
        for (UUID id : ids) {
            reivindicar(id).ifPresent(this::ativarEAtualizar);
        }
    }

    private List<UUID> buscarIdsElegiveis() {
        Instant agora = Instant.now();
        Instant limiteReconciliacao = agora.minus(properties.reconciliacaoTimeout());
        return transactionTemplate.execute(status -> repository.buscarIdsElegiveisParaProcessar(
                agora, limiteReconciliacao, PageRequest.of(0, properties.worker().tamanhoLote())));
    }

    private Optional<Solicitacao> reivindicar(UUID id) {
        try {
            return Optional.ofNullable(transactionTemplate.execute(status -> {
                Solicitacao solicitacao = repository.findById(id).orElse(null);
                if (solicitacao == null || !elegivel(solicitacao)) {
                    return null;
                }
                solicitacao.iniciarProcessamento(Instant.now());
                return repository.saveAndFlush(solicitacao);
            }));
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Solicitacao {} ja foi reivindicada por outra execucao/instancia", id);
            return Optional.empty();
        }
    }

    private boolean elegivel(Solicitacao solicitacao) {
        if (solicitacao.getStatus() == StatusSolicitacao.PENDING) {
            return true;
        }
        if (solicitacao.getStatus() != StatusSolicitacao.PROCESSING) {
            return false;
        }
        if (solicitacao.getProximaTentativaEm() != null) {
            return !solicitacao.getProximaTentativaEm().isAfter(Instant.now());
        }
        Instant limiteReconciliacao = Instant.now().minus(properties.reconciliacaoTimeout());
        return solicitacao.getUpdatedAt().isBefore(limiteReconciliacao);
    }

    private void ativarEAtualizar(Solicitacao solicitacaoReivindicada) {
        RespostaProvedor resposta = providerClient.ativar(solicitacaoReivindicada);
        transactionTemplate.executeWithoutResult(status -> {
            Solicitacao atual = repository.findById(solicitacaoReivindicada.getId()).orElseThrow();
            aplicarResultado(atual, resposta);
            repository.save(atual);
        });
    }

    private void aplicarResultado(Solicitacao solicitacao, RespostaProvedor resposta) {
        Instant agora = Instant.now();
        switch (resposta) {
            case RespostaProvedor.Sucesso ignored -> solicitacao.concluir(agora);
            case RespostaProvedor.ErroCliente ignored -> solicitacao.falharDefinitivamente(agora);
            case RespostaProvedor.ErroServidor ignored ->
                    aplicarFalhaOuEsgotarTentativas(solicitacao, agora, calcularBackoff(solicitacao.getTentativas()));
            case RespostaProvedor.Timeout ignored ->
                    aplicarFalhaOuEsgotarTentativas(solicitacao, agora, calcularBackoff(solicitacao.getTentativas()));
            case RespostaProvedor.Indisponivel indisponivel ->
                    aplicarFalhaOuEsgotarTentativas(solicitacao, agora, indisponivel.retryAfter());
        }
    }

    private void aplicarFalhaOuEsgotarTentativas(Solicitacao solicitacao, Instant agora, Duration proximoDelay) {
        if (solicitacao.getTentativas() >= properties.maxTentativas()) {
            solicitacao.falharDefinitivamente(agora);
        } else {
            solicitacao.registrarFalhaTemporaria(agora.plus(proximoDelay), agora);
        }
    }

    private Duration calcularBackoff(int tentativas) {
        long baseMillis = properties.backoffBase().toMillis();
        long expoente = Math.min(tentativas - 1, 10);
        long semJitter = Math.min(baseMillis * (1L << expoente), properties.backoffMax().toMillis());
        long jitter = (long) (random.nextDouble() * baseMillis);
        return Duration.ofMillis(semJitter + jitter);
    }
}
