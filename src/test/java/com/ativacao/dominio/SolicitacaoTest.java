package com.ativacao.dominio;

import com.ativacao.excecao.TransicaoDeStatusInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolicitacaoTest {

    private final Instant agora = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void novaSolicitacaoComecaPendente() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.PENDING);
        assertThat(s.getTentativas()).isZero();
        assertThat(s.getCreatedAt()).isEqualTo(agora);
    }

    @Test
    void iniciarProcessamentoAPartirDePendenteIncrementaTentativasEMudaStatus() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);

        s.iniciarProcessamento(agora.plusSeconds(1));

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.PROCESSING);
        assertThat(s.getTentativas()).isEqualTo(1);
    }

    @Test
    void concluirAPartirDeProcessandoMudaParaConcluida() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);
        s.iniciarProcessamento(agora.plusSeconds(1));

        s.concluir(agora.plusSeconds(2));

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.COMPLETED);
    }

    @Test
    void concluirAPartirDePendenteLancaExcecao() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);

        assertThatThrownBy(() -> s.concluir(agora))
                .isInstanceOf(TransicaoDeStatusInvalidaException.class);
    }

    @Test
    void falharDefinitivamenteAPartirDeConcluidaLancaExcecao() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);
        s.iniciarProcessamento(agora);
        s.concluir(agora);

        assertThatThrownBy(() -> s.falharDefinitivamente(agora))
                .isInstanceOf(TransicaoDeStatusInvalidaException.class);
    }

    @Test
    void registrarFalhaTemporariaDefineProximaTentativa() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);
        s.iniciarProcessamento(agora);
        Instant proxima = agora.plusSeconds(30);

        s.registrarFalhaTemporaria(proxima, agora.plusSeconds(1));

        assertThat(s.getStatus()).isEqualTo(StatusSolicitacao.PROCESSING);
        assertThat(s.getProximaTentativaEm()).isEqualTo(proxima);
    }

    @Test
    void iniciarProcessamentoAPartirDeFalhouLancaExcecao() {
        Solicitacao s = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "SISTEMA-TESTE", agora);
        s.iniciarProcessamento(agora);
        s.falharDefinitivamente(agora);

        assertThatThrownBy(() -> s.iniciarProcessamento(agora))
                .isInstanceOf(TransicaoDeStatusInvalidaException.class);
    }

    @Test
    void concluidaNuncaVoltaParaProcessando() {
        assertThat(StatusSolicitacao.COMPLETED.podeTransicionarPara(StatusSolicitacao.PROCESSING)).isFalse();
    }

    @Test
    void falhouNaoTemNenhumaTransicaoValida() {
        assertThat(StatusSolicitacao.FAILED.podeTransicionarPara(StatusSolicitacao.PROCESSING)).isFalse();
        assertThat(StatusSolicitacao.FAILED.podeTransicionarPara(StatusSolicitacao.COMPLETED)).isFalse();
    }
}
