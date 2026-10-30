package com.ativacao.excecao;

public abstract class ServicoAtivacaoException extends RuntimeException {

    protected ServicoAtivacaoException(String mensagem) {
        super(mensagem);
    }

    protected ServicoAtivacaoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
