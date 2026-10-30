package com.ativacao.integracao;

import com.ativacao.dominio.Solicitacao;

public interface ProviderClient {

    RespostaProvedor ativar(Solicitacao solicitacao);
}
