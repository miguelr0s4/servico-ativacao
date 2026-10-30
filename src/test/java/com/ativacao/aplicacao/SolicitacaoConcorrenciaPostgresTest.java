package com.ativacao.aplicacao;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.SolicitacaoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "provider.worker.enabled=false")
class SolicitacaoConcorrenciaPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private SolicitacaoService service;

    @Autowired
    private SolicitacaoRepository repository;

    @Test
    void requisicoesIdenticasEmParalelo_resultamEmExatamenteUmRegistroNoPostgres() throws InterruptedException {
        String externalId = "EXT-CONCORRENTE-POSTGRES";
        int numeroDeChamadas = 10;
        CountDownLatch partida = new CountDownLatch(1);
        AtomicInteger criadas = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(numeroDeChamadas);

        try {
            for (int i = 0; i < numeroDeChamadas; i++) {
                pool.submit(() -> executarCriacaoConcorrente(partida, criadas, externalId));
            }
            partida.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(criadas.get()).isEqualTo(1);
        List<Solicitacao> registros = repository.findAll().stream()
                .filter(solicitacao -> solicitacao.getExternalId().equals(externalId))
                .toList();
        assertThat(registros).hasSize(1);
    }

    private void executarCriacaoConcorrente(CountDownLatch partida, AtomicInteger criadas, String externalId) {
        try {
            partida.await();
            ResultadoCriacao resultado = service.criar(externalId, "CUST-1", "PROD-1", "SISTEMA-TESTE");
            if (resultado instanceof ResultadoCriacao.Criada) {
                criadas.incrementAndGet();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
