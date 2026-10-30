package com.ativacao.api;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilidadeConfig {

    @Bean
    public SpanExporter spanExporter() {
        return LoggingSpanExporter.create();
    }
}
