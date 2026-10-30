package com.ativacao.api;

import com.ativacao.dominio.SolicitacaoRepository;
import com.ativacao.dominio.StatusSolicitacao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SolicitacaoMetricas {

    public SolicitacaoMetricas(MeterRegistry registry, SolicitacaoRepository repository) {
        for (StatusSolicitacao status : StatusSolicitacao.values()) {
            Gauge.builder("solicitacoes.por.status", () -> repository.countByStatus(status))
                    .tag("status", status.name())
                    .description("Quantidade atual de solicitacoes em cada status")
                    .register(registry);
        }
    }
}
