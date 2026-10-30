package com.ativacao.api;

import com.ativacao.excecao.NegocioException;
import com.ativacao.excecao.SolicitacaoDivergenteException;
import com.ativacao.excecao.SolicitacaoNaoEncontradaException;
import com.ativacao.excecao.TransicaoDeStatusInvalidaException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_URI = "https://api.servico-ativacao.com/problemas/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<Map<String, String>> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(this::paraMapaDeErro)
                .toList();

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Um ou mais campos nao passaram na validacao.");
        problema.setType(URI.create(BASE_URI + "validacao"));
        problema.setTitle("Dados invalidos");
        problema.setProperty("erros", erros);
        return comTraceId(problema, request);
    }

    @ExceptionHandler(NegocioException.class)
    public ProblemDetail handleNegocio(NegocioException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex) {
            case SolicitacaoNaoEncontradaException e -> HttpStatus.NOT_FOUND;
            case SolicitacaoDivergenteException e -> HttpStatus.CONFLICT;
            case TransicaoDeStatusInvalidaException e -> HttpStatus.CONFLICT;
        };

        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problema.setType(URI.create(BASE_URI + tipoDe(ex)));
        problema.setTitle(tituloDe(ex));
        return comTraceId(problema, request);
    }

    @ExceptionHandler(RequestNotPermitted.class)
    public ProblemDetail handleLimiteDeTaxa(RequestNotPermitted ex, HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS, "Limite de requisicoes excedido. Tente novamente em breve.");
        problema.setType(URI.create(BASE_URI + "limite-de-taxa-excedido"));
        problema.setTitle("Muitas requisicoes");
        return comTraceId(problema, request);
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handlePersistencia(DataAccessException ex, HttpServletRequest request) {
        log.error("Falha de persistencia", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro ao acessar o banco de dados. Consulte o suporte informando o traceId.");
        problema.setType(URI.create(BASE_URI + "erro-persistencia"));
        problema.setTitle("Erro de persistencia");
        return comTraceId(problema, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleInesperado(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao processar requisicao", ex);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Consulte o suporte informando o traceId.");
        problema.setTitle("Erro interno");
        return comTraceId(problema, request);
    }

    private Map<String, String> paraMapaDeErro(FieldError erro) {
        return Map.of("field", erro.getField(), "message", erro.getDefaultMessage());
    }

    private String tipoDe(NegocioException ex) {
        return switch (ex) {
            case SolicitacaoNaoEncontradaException e -> "solicitacao-nao-encontrada";
            case SolicitacaoDivergenteException e -> "solicitacao-divergente";
            case TransicaoDeStatusInvalidaException e -> "transicao-invalida";
        };
    }

    private String tituloDe(NegocioException ex) {
        return switch (ex) {
            case SolicitacaoNaoEncontradaException e -> "Solicitacao nao encontrada";
            case SolicitacaoDivergenteException e -> "Solicitacao ja existe com dados diferentes";
            case TransicaoDeStatusInvalidaException e -> "Transicao de status invalida";
        };
    }

    private ProblemDetail comTraceId(ProblemDetail problema, HttpServletRequest request) {
        problema.setInstance(URI.create(request.getRequestURI()));
        problema.setProperty("traceId", MDC.get("traceId"));
        return problema;
    }
}
