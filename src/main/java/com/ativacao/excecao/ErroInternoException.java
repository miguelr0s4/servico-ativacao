package com.ativacao.excecao;

public abstract class ErroInternoException extends ServicoAtivacaoException {

    protected ErroInternoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
