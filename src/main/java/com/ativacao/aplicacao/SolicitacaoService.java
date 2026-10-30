package com.ativacao.aplicacao;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.SolicitacaoRepository;
import com.ativacao.dominio.StatusSolicitacao;
import com.ativacao.excecao.SolicitacaoNaoEncontradaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
public class SolicitacaoService {

    private final SolicitacaoRepository repository;
    private final TransactionTemplate transactionTemplate;

    public SolicitacaoService(SolicitacaoRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Solicitacao buscarPorId(UUID id, String criadoPorSistema) {
        return repository.findById(id)
                .filter(s -> s.getCriadoPorSistema().equals(criadoPorSistema))
                .orElseThrow(() -> new SolicitacaoNaoEncontradaException(id));
    }

    public Page<Solicitacao> listar(String criadoPorSistema, StatusSolicitacao status, String customerId,
                                     Pageable pageable) {
        return repository.buscarComFiltros(criadoPorSistema, status, customerId, pageable);
    }

    public ResultadoCriacao criar(String externalId, String customerId, String productCode, String criadoPorSistema) {
        try {
            Solicitacao nova = inserirEmNovaTransacao(externalId, customerId, productCode, criadoPorSistema);
            return new ResultadoCriacao.Criada(nova);
        } catch (DataIntegrityViolationException e) {
            return resolverConflito(externalId, customerId, productCode);
        }
    }

    private Solicitacao inserirEmNovaTransacao(String externalId, String customerId, String productCode,
                                                String criadoPorSistema) {
        return transactionTemplate.execute(status -> {
            Solicitacao nova = Solicitacao.nova(externalId, customerId, productCode, criadoPorSistema, Instant.now());
            return repository.saveAndFlush(nova);
        });
    }

    private ResultadoCriacao resolverConflito(String externalId, String customerId, String productCode) {
        Solicitacao existente = repository.findByExternalId(externalId)
                .orElseThrow(() -> new IllegalStateException(
                        "Constraint de unicidade violada para externalId=%s, mas o registro nao foi encontrado na releitura"
                                .formatted(externalId)));

        boolean mesmoConteudo = existente.getCustomerId().equals(customerId)
                && existente.getProductCode().equals(productCode);

        return mesmoConteudo
                ? new ResultadoCriacao.JaExistenteIdentica(existente)
                : new ResultadoCriacao.ConflitoDivergente(existente);
    }
}
