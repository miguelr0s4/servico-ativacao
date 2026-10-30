package com.ativacao.aplicacao;

import com.ativacao.dominio.Solicitacao;

public sealed interface ResultadoCriacao {

    record Criada(Solicitacao solicitacao) implements ResultadoCriacao {
    }

    record JaExistenteIdentica(Solicitacao solicitacao) implements ResultadoCriacao {
    }

    record ConflitoDivergente(Solicitacao existente) implements ResultadoCriacao {
    }
}
