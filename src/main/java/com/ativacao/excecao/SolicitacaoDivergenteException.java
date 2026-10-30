package com.ativacao.excecao;

public final class SolicitacaoDivergenteException extends NegocioException {

    public SolicitacaoDivergenteException(String externalId) {
        super("Ja existe uma solicitacao com externalId=%s, mas com customerId ou productCode diferentes"
                .formatted(externalId));
    }
}
