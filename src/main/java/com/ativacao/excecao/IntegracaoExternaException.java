package com.ativacao.excecao;

public final class IntegracaoExternaException extends ErroInternoException {

    public IntegracaoExternaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
