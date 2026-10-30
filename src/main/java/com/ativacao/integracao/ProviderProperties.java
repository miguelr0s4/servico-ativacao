package com.ativacao.integracao;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "provider")
public record ProviderProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxTentativas,
        Duration backoffBase,
        Duration backoffMax,
        Duration reconciliacaoTimeout,
        Worker worker
) {

    public record Worker(boolean enabled, Duration intervalo, int tamanhoLote) {
    }
}
