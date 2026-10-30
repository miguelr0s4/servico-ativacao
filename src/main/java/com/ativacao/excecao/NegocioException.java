package com.ativacao.excecao;

public abstract sealed class NegocioException extends ServicoAtivacaoException
        permits SolicitacaoNaoEncontradaException, SolicitacaoDivergenteException,
                TransicaoDeStatusInvalidaException {

    protected NegocioException(String mensagem) {
        super(mensagem);
    }
}
