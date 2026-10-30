package com.ativacao.api;

import com.ativacao.api.security.SecurityConfig;
import com.ativacao.aplicacao.ResultadoCriacao;
import com.ativacao.aplicacao.SolicitacaoService;
import com.ativacao.dominio.Solicitacao;
import com.ativacao.dominio.StatusSolicitacao;
import com.ativacao.excecao.SolicitacaoDivergenteException;
import com.ativacao.excecao.SolicitacaoNaoEncontradaException;
import com.ativacao.suporte.TokenTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ativacao.suporte.TokenTeste.SISTEMA_PADRAO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SolicitacaoController.class)
@Import(SecurityConfig.class)
class SolicitacaoControllerTest {

    private static final String CORPO_VALIDO =
            "{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"PROD-1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolicitacaoService service;

    @Test
    void criar_semToken_retorna401() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void criar_comEscopoDeLeitura_retorna403() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isForbidden());
    }

    @Test
    void criar_comDadosValidosEEscopoDeEscrita_retorna201() throws Exception {
        Solicitacao nova = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", SISTEMA_PADRAO, Instant.now());
        when(service.criar(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoCriacao.Criada(nova));

        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isCreated());
    }

    @Test
    void criar_comDadosInvalidos_retorna400ComProblemDetailCompleto() throws Exception {
        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"\",\"customerId\":\"CUST-1\",\"productCode\":\"PROD-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.instance").value("/api/v1/requests"))
                .andExpect(jsonPath("$.erros[0].field").value("externalId"));
    }

    @Test
    void criar_corpoMuitoGrande_retorna413() throws Exception {
        String produtoGigante = "P".repeat(20_000);
        String corpoGrande = "{\"externalId\":\"EXT-1\",\"customerId\":\"CUST-1\",\"productCode\":\"" + produtoGigante + "\"}";

        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoGrande))
                .andExpect(status().is(413));
    }

    @Test
    void criar_comExternalIdDivergente_retorna409() throws Exception {
        when(service.criar(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new SolicitacaoDivergenteException("EXT-1"));

        mockMvc.perform(post("/api/v1/requests")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CORPO_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    void buscarPorId_semToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/requests/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void buscarPorId_existente_retorna200() throws Exception {
        Solicitacao existente = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", SISTEMA_PADRAO, Instant.now());
        when(service.buscarPorId(eq(existente.getId()), eq(SISTEMA_PADRAO))).thenReturn(existente);

        mockMvc.perform(get("/api/v1/requests/" + existente.getId())
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:read")))
                .andExpect(status().isOk());
    }

    @Test
    void buscarPorId_inexistente_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(eq(id), anyString())).thenThrow(new SolicitacaoNaoEncontradaException(id));

        mockMvc.perform(get("/api/v1/requests/" + id)
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:read")))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscarPorId_deOutroSistema_retorna404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.buscarPorId(eq(id), eq(SISTEMA_PADRAO))).thenThrow(new SolicitacaoNaoEncontradaException(id));

        mockMvc.perform(get("/api/v1/requests/" + id)
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:read")))
                .andExpect(status().isNotFound());
    }

    @Test
    void listar_semToken_retorna401() throws Exception {
        mockMvc.perform(get("/api/v1/requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listar_comFiltrosEPaginacao_retorna200ComConteudoEMetadadosDePagina() throws Exception {
        Solicitacao existente = Solicitacao.nova("EXT-1", "CUST-1", "PROD-1", SISTEMA_PADRAO, Instant.now());
        Page<Solicitacao> pagina = new PageImpl<>(List.of(existente), PageRequest.of(0, 20), 1);
        when(service.listar(eq(SISTEMA_PADRAO), eq(StatusSolicitacao.PENDING), eq("CUST-1"), any()))
                .thenReturn(pagina);

        mockMvc.perform(get("/api/v1/requests?status=PENDING&customerId=CUST-1")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].externalId").value("EXT-1"))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listar_semFiltros_passaNullParaOServico() throws Exception {
        when(service.listar(eq(SISTEMA_PADRAO), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer " + TokenTeste.comEscopos("requests:read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void listar_filtraSempreApenasPeloSistemaDoToken() throws Exception {
        when(service.listar(eq("outro-sistema"), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/requests")
                        .header("Authorization", "Bearer "
                                + TokenTeste.comEscoposComoSistema("outro-sistema", "requests:read")))
                .andExpect(status().isOk());
    }
}
