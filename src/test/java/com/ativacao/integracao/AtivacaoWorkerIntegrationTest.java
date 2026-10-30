package com.ativacao.integracao;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.SolicitacaoRepository;
import com.ativacao.dominio.StatusSolicitacao;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "provider.worker.enabled=true",
        "provider.worker.intervalo=1h",
        "provider.base-url=http://localhost:8089",
        "provider.connect-timeout=2s",
        "provider.read-timeout=500ms",
        "provider.backoff-base=10ms",
        "provider.backoff-max=50ms",
        "provider.max-tentativas=2",
        "provider.reconciliacao-timeout=2s",
        "resilience4j.circuitbreaker.instances.provider.sliding-window-size=1000",
        "resilience4j.circuitbreaker.instances.provider.failure-rate-threshold=100"
})
@WireMockTest(httpPort = 8089)
class AtivacaoWorkerIntegrationTest {

    @Autowired
    private AtivacaoWorker worker;

    @Autowired
    private SolicitacaoRepository repository;

    @BeforeEach
    void limparBanco() {
        repository.deleteAll();
    }

    @Test
    void sucesso_marcaComoConcluida() {
        stubFor(post(urlEqualTo("/provider/v1/activate")).willReturn(ok()));
        Solicitacao solicitacao = repository.save(Solicitacao.nova("EXT-W-OK", "CUST-1", "PROD-1", "SISTEMA-TESTE", Instant.now()));

        worker.processarLote();

        Solicitacao atualizada = repository.findById(solicitacao.getId()).orElseThrow();
        assertThat(atualizada.getStatus()).isEqualTo(StatusSolicitacao.COMPLETED);
    }

    @Test
    void erro400_falhaDefinitivamenteSemRetentativa() {
        stubFor(post(urlEqualTo("/provider/v1/activate")).willReturn(badRequest()));
        Solicitacao solicitacao = repository.save(Solicitacao.nova("EXT-W-400", "CUST-1", "PROD-1", "SISTEMA-TESTE", Instant.now()));

        worker.processarLote();

        Solicitacao atualizada = repository.findById(solicitacao.getId()).orElseThrow();
        assertThat(atualizada.getStatus()).isEqualTo(StatusSolicitacao.FAILED);
        assertThat(atualizada.getTentativas()).isEqualTo(1);
    }

    @Test
    void erro500_fazRetentativaEDepoisEsgotaTentativas() throws InterruptedException {
        stubFor(post(urlEqualTo("/provider/v1/activate")).willReturn(serverError()));
        Solicitacao solicitacao = repository.save(Solicitacao.nova("EXT-W-500", "CUST-1", "PROD-1", "SISTEMA-TESTE", Instant.now()));

        worker.processarLote();
        Solicitacao apos1aTentativa = repository.findById(solicitacao.getId()).orElseThrow();
        assertThat(apos1aTentativa.getStatus()).isEqualTo(StatusSolicitacao.PROCESSING);
        assertThat(apos1aTentativa.getTentativas()).isEqualTo(1);

        Thread.sleep(300);
        worker.processarLote();

        Solicitacao apos2aTentativa = repository.findById(solicitacao.getId()).orElseThrow();
        assertThat(apos2aTentativa.getStatus()).isEqualTo(StatusSolicitacao.FAILED);
        assertThat(apos2aTentativa.getTentativas()).isEqualTo(2);
    }

    @Test
    void timeout_depoisRetentativaComMesmaIdempotencyKeyTemSucesso() throws InterruptedException {
        stubFor(post(urlEqualTo("/provider/v1/activate")).willReturn(ok().withFixedDelay(2000)));

        Solicitacao solicitacao = repository.save(Solicitacao.nova("EXT-W-TIMEOUT", "CUST-1", "PROD-1", "SISTEMA-TESTE", Instant.now()));

        worker.processarLote();
        Solicitacao apos1aTentativa = repository.findById(solicitacao.getId()).orElseThrow();
        assertThat(apos1aTentativa.getStatus()).isEqualTo(StatusSolicitacao.PROCESSING);
        assertThat(apos1aTentativa.getTentativas()).isEqualTo(1);

        stubFor(post(urlEqualTo("/provider/v1/activate")).willReturn(ok()));
        Thread.sleep(300);
        worker.processarLote();

        Solicitacao apos2aTentativa = repository.findById(solicitacao.getId()).orElseThrow();
        assertThat(apos2aTentativa.getStatus()).isEqualTo(StatusSolicitacao.COMPLETED);

        verify(2, postRequestedFor(urlEqualTo("/provider/v1/activate"))
                .withHeader("Idempotency-Key", equalTo(solicitacao.getId().toString())));
    }

    @Test
    void reconciliacao_retomaSolicitacaoPresaEmProcessingSemRetentativaAgendada() {
        stubFor(post(urlEqualTo("/provider/v1/activate")).willReturn(ok()));
        Solicitacao presa = Solicitacao.nova("EXT-W-PRESA", "CUST-1", "PROD-1", "SISTEMA-TESTE", Instant.now().minusSeconds(120));
        presa.iniciarProcessamento(Instant.now().minusSeconds(120));
        repository.save(presa);

        worker.processarLote();

        Solicitacao atualizada = repository.findById(presa.getId()).orElseThrow();
        assertThat(atualizada.getStatus()).isEqualTo(StatusSolicitacao.COMPLETED);
    }
}
