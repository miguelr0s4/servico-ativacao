package com.ativacao.aplicacao;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.SolicitacaoRepository;
import com.ativacao.excecao.SolicitacaoNaoEncontradaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class SolicitacaoServiceIntegrationTest {

    @Autowired
    private SolicitacaoService service;

    @Autowired
    private SolicitacaoRepository repository;

    @Test
    void criarNovaSolicitacao_quandoNaoExiste_retornaCriada() {
        ResultadoCriacao resultado = service.criar("EXT-NOVA-1", "CUST-1", "PROD-1", "SISTEMA-TESTE");

        assertThat(resultado).isInstanceOf(ResultadoCriacao.Criada.class);
    }

    @Test
    void criarComMesmoExternalIdEMesmoConteudo_retornaJaExistenteIdentica() {
        service.criar("EXT-RETRY-1", "CUST-1", "PROD-1", "SISTEMA-TESTE");

        ResultadoCriacao segundaChamada = service.criar("EXT-RETRY-1", "CUST-1", "PROD-1", "SISTEMA-TESTE");

        assertThat(segundaChamada).isInstanceOf(ResultadoCriacao.JaExistenteIdentica.class);
        assertThat(repository.findByExternalId("EXT-RETRY-1")).isPresent();
    }

    @Test
    void criarComMesmoExternalIdEConteudoDiferente_retornaConflitoDivergente() {
        service.criar("EXT-DIVERGE-1", "CUST-1", "PROD-1", "SISTEMA-TESTE");

        ResultadoCriacao segundaChamada = service.criar("EXT-DIVERGE-1", "CUST-1", "PROD-DIFERENTE", "SISTEMA-TESTE");

        assertThat(segundaChamada).isInstanceOf(ResultadoCriacao.ConflitoDivergente.class);
    }

    @Test
    void buscarPorId_criadaPeloMesmoSistema_retornaASolicitacao() {
        ResultadoCriacao.Criada criada =
                (ResultadoCriacao.Criada) service.criar("EXT-BUSCA-1", "CUST-1", "PROD-1", "SISTEMA-TESTE");

        Solicitacao encontrada = service.buscarPorId(criada.solicitacao().getId(), "SISTEMA-TESTE");

        assertThat(encontrada.getId()).isEqualTo(criada.solicitacao().getId());
    }

    @Test
    void buscarPorId_inexistente_lancaSolicitacaoNaoEncontrada() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.buscarPorId(id, "SISTEMA-TESTE"))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void buscarPorId_criadaPorOutroSistema_lancaSolicitacaoNaoEncontrada() {
        ResultadoCriacao.Criada criada =
                (ResultadoCriacao.Criada) service.criar("EXT-BUSCA-2", "CUST-1", "PROD-1", "SISTEMA-TESTE");

        assertThatThrownBy(() -> service.buscarPorId(criada.solicitacao().getId(), "OUTRO-SISTEMA"))
                .isInstanceOf(SolicitacaoNaoEncontradaException.class);
    }

    @Test
    void listar_filtraApenasPeloSistemaInformado() {
        service.criar("EXT-LISTAR-1", "CUST-1", "PROD-1", "SISTEMA-TESTE");
        service.criar("EXT-LISTAR-2", "CUST-1", "PROD-1", "OUTRO-SISTEMA");

        var pagina = service.listar("SISTEMA-TESTE", null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent())
                .extracting(Solicitacao::getExternalId)
                .contains("EXT-LISTAR-1")
                .doesNotContain("EXT-LISTAR-2");
    }

}
