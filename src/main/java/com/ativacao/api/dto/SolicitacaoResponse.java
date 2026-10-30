package com.ativacao.api.dto;

import com.ativacao.dominio.Solicitacao;

import java.time.Instant;
import java.util.UUID;

public record SolicitacaoResponse(
        UUID id,
        String externalId,
        String customerId,
        String productCode,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static SolicitacaoResponse de(Solicitacao solicitacao) {
        return new SolicitacaoResponse(
                solicitacao.getId(),
                solicitacao.getExternalId(),
                solicitacao.getCustomerId(),
                solicitacao.getProductCode(),
                solicitacao.getStatus().name(),
                solicitacao.getCreatedAt(),
                solicitacao.getUpdatedAt()
        );
    }
}
