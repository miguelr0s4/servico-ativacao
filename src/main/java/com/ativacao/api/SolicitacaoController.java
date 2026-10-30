package com.ativacao.api;

import com.ativacao.api.dto.CriarSolicitacaoRequest;
import com.ativacao.api.dto.SolicitacaoResponse;
import com.ativacao.aplicacao.ResultadoCriacao;
import com.ativacao.aplicacao.SolicitacaoService;
import com.ativacao.dominio.StatusSolicitacao;
import com.ativacao.excecao.SolicitacaoDivergenteException;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/requests")
public class SolicitacaoController {

    private final SolicitacaoService service;

    public SolicitacaoController(SolicitacaoService service) {
        this.service = service;
    }

    @PostMapping
    @RateLimiter(name = "criarSolicitacao")
    public ResponseEntity<SolicitacaoResponse> criar(@Valid @RequestBody CriarSolicitacaoRequest request,
                                                       @AuthenticationPrincipal Jwt jwt) {
        ResultadoCriacao resultado = service.criar(
                request.externalId(), request.customerId(), request.productCode(), jwt.getSubject());

        return switch (resultado) {
            case ResultadoCriacao.Criada c -> ResponseEntity
                    .created(URI.create("/api/v1/requests/" + c.solicitacao().getId()))
                    .body(SolicitacaoResponse.de(c.solicitacao()));
            case ResultadoCriacao.JaExistenteIdentica j ->
                    ResponseEntity.ok(SolicitacaoResponse.de(j.solicitacao()));
            case ResultadoCriacao.ConflitoDivergente cd ->
                    throw new SolicitacaoDivergenteException(cd.existente().getExternalId());
        };
    }

    @GetMapping("/{id}")
    public ResponseEntity<SolicitacaoResponse> buscarPorId(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        SolicitacaoResponse resposta = SolicitacaoResponse.de(service.buscarPorId(id, jwt.getSubject()));
        return ResponseEntity.ok(resposta);
    }

    @GetMapping
    public PagedModel<SolicitacaoResponse> listar(
            @RequestParam(required = false) StatusSolicitacao status,
            @RequestParam(required = false) String customerId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        Page<SolicitacaoResponse> pagina = service
                .listar(jwt.getSubject(), status, customerId, pageable)
                .map(SolicitacaoResponse::de);
        return new PagedModel<>(pagina);
    }
}
