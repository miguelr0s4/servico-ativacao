package com.ativacao.integracao;

import java.time.Duration;

public sealed interface RespostaProvedor {

    record Sucesso() implements RespostaProvedor {
    }

    record ErroCliente(String detalhe) implements RespostaProvedor {
    }

    record ErroServidor(String detalhe) implements RespostaProvedor {
    }

    record Timeout() implements RespostaProvedor {
    }

    record Indisponivel(Duration retryAfter) implements RespostaProvedor {
    }
}
