package com.ativacao.excecao;

public final class TransicaoDeStatusInvalidaException extends NegocioException {

    public TransicaoDeStatusInvalidaException(String mensagem) {
        super(mensagem);
    }
}
