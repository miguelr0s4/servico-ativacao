package com.ativacao.api;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.SolicitacaoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SolicitacaoMetricasTest {

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private SolicitacaoRepository repository;

    @Test
    void expoeContagemAtualPorStatus() {
        repository.save(Solicitacao.nova("EXT-METRICA-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", Instant.now()));

        double pendentes = registry.get("solicitacoes.por.status").tag("status", "PENDING").gauge().value();

        assertThat(pendentes).isGreaterThanOrEqualTo(1);
    }
}
