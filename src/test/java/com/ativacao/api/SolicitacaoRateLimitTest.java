package com.ativacao.api;

import com.ativacao.aplicacao.ResultadoCriacao;
import com.ativacao.aplicacao.SolicitacaoService;
import com.ativacao.dominio.Solicitacao;
import com.ativacao.suporte.TokenTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "resilience4j.ratelimiter.instances.criarSolicitacao.limit-for-period=2",
        "resilience4j.ratelimiter.instances.criarSolicitacao.limit-refresh-period=10s"
})
class SolicitacaoRateLimitTest {

    private static final String CORPO_VALIDO =
            "{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"PROD-1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolicitacaoService service;

    @Test
    void criar_excedeLimiteDeRequisicoes_retorna429() throws Exception {
        when(service.criar(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoCriacao.Criada(
                        Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", "sistema-cliente-teste", Instant.now())));
        String token = "Bearer " + TokenTeste.comEscopos("requests:write");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/requests")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CORPO_VALIDO))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().is(429));
    }
}
