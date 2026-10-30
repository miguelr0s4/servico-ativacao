package com.ativacao.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarSolicitacaoRequest(
        @NotBlank @Size(max = 100) String externalId,
        @NotBlank @Size(max = 100) String customerId,
        @NotBlank @Size(max = 100) String productCode
) {
}
