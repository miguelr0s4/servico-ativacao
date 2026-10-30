package com.ativacao.excecao;

import java.util.UUID;

public final class SolicitacaoNaoEncontradaException extends NegocioException {

    public SolicitacaoNaoEncontradaException(UUID id) {
        super("Solicitacao nao encontrada: " + id);
    }
}
