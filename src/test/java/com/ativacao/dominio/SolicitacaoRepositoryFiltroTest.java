package com.ativacao.dominio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SolicitacaoRepositoryFiltroTest {

    @Autowired
    private SolicitacaoRepository repository;

    @Test
    void filtraPorStatusECustomerIdCombinados() {
        String sistema = "SISTEMA-FILTRO-1";
        repository.save(Solicitacao.nova("EXT-FILTRO-1", "CUST-FILTRO-A", "PROD-1", sistema, Instant.now()));
        Solicitacao doCustomerB = repository.save(
                Solicitacao.nova("EXT-FILTRO-2", "CUST-FILTRO-B", "PROD-1", sistema, Instant.now()));

        Page<Solicitacao> resultado = repository.buscarComFiltros(
                sistema, StatusSolicitacao.PENDING, "CUST-FILTRO-B", PageRequest.of(0, 20));

        assertThat(resultado.getContent())
                .extracting(Solicitacao::getExternalId)
                .containsExactly(doCustomerB.getExternalId());
    }

    @Test
    void semFiltrosRetornaApenasDoMesmoSistemaPaginado() {
        String sistema = "SISTEMA-FILTRO-2";
        repository.save(Solicitacao.nova("EXT-FILTRO-3", "CUST-FILTRO-C", "PROD-1", sistema, Instant.now()));
        repository.save(Solicitacao.nova("EXT-FILTRO-4", "CUST-FILTRO-C", "PROD-1", sistema, Instant.now()));
        repository.save(Solicitacao.nova("EXT-FILTRO-X", "CUST-FILTRO-C", "PROD-1", "OUTRO-SISTEMA", Instant.now()));

        Page<Solicitacao> resultado = repository.buscarComFiltros(sistema, null, null, PageRequest.of(0, 1));

        assertThat(resultado.getContent()).hasSize(1);
        assertThat(resultado.getTotalElements()).isEqualTo(2);
    }

    @Test
    void filtraApenasPorCustomerId() {
        String sistema = "SISTEMA-FILTRO-3";
        repository.save(Solicitacao.nova("EXT-FILTRO-5", "CUST-FILTRO-D", "PROD-1", sistema, Instant.now()));
        repository.save(Solicitacao.nova("EXT-FILTRO-6", "CUST-FILTRO-OUTRO", "PROD-1", sistema, Instant.now()));

        Page<Solicitacao> resultado = repository.buscarComFiltros(
                sistema, null, "CUST-FILTRO-D", PageRequest.of(0, 20));

        assertThat(resultado.getContent())
                .extracting(Solicitacao::getCustomerId)
                .containsOnly("CUST-FILTRO-D");
    }

    @Test
    void naoRetornaSolicitacoesDeOutroSistema() {
        repository.save(Solicitacao.nova("EXT-FILTRO-7", "CUST-FILTRO-E", "PROD-1", "OUTRO-SISTEMA", Instant.now()));

        Page<Solicitacao> resultado = repository.buscarComFiltros(
                "SISTEMA-FILTRO-4", null, "CUST-FILTRO-E", PageRequest.of(0, 20));

        assertThat(resultado.getContent()).isEmpty();
    }
}
