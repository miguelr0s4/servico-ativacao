package com.ativacao.integracao;

import com.ativacao.dominio.Solicitacao;
import com.ativacao.excecao.IntegracaoExternaException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class RestClientProviderClient implements ProviderClient {

    private static final Logger log = LoggerFactory.getLogger(RestClientProviderClient.class);
    private static final Duration RETRY_AFTER_PADRAO = Duration.ofSeconds(5);

    private final RestClient restClient;

    public RestClientProviderClient(RestClient.Builder builder, ProviderProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings))
                .build();
    }

    @Override
    @CircuitBreaker(name = "provider", fallbackMethod = "circuitoAberto")
    public RespostaProvedor ativar(Solicitacao solicitacao) {
        try {
            restClient.post()
                    .uri("/provider/v1/activate")
                    .header("Idempotency-Key", solicitacao.getId().toString())
                    .body(new AtivarRequestDto(
                            solicitacao.getExternalId(),
                            solicitacao.getCustomerId(),
                            solicitacao.getProductCode()))
                    .retrieve()
                    .toBodilessEntity();
            return new RespostaProvedor.Sucesso();
        } catch (HttpClientErrorException.TooManyRequests e) {
            return new RespostaProvedor.Indisponivel(retryAfterDe(e));
        } catch (HttpServerErrorException.ServiceUnavailable e) {
            return new RespostaProvedor.Indisponivel(retryAfterDe(e));
        } catch (HttpClientErrorException e) {
            return new RespostaProvedor.ErroCliente(e.getStatusText());
        } catch (HttpServerErrorException e) {
            return new RespostaProvedor.ErroServidor(e.getStatusText());
        } catch (ResourceAccessException e) {
            return new RespostaProvedor.Timeout();
        } catch (Exception e) {
            IntegracaoExternaException erro = new IntegracaoExternaException(
                    "Falha nao classificada ao chamar o provedor externo", e);
            log.error(erro.getMessage(), erro);
            return new RespostaProvedor.Indisponivel(RETRY_AFTER_PADRAO);
        }
    }

    @SuppressWarnings("unused")
    private RespostaProvedor circuitoAberto(Solicitacao solicitacao, Throwable causa) {
        log.warn("Circuito aberto para o provedor externo, solicitacao={}", solicitacao.getId());
        return new RespostaProvedor.Indisponivel(RETRY_AFTER_PADRAO);
    }

    private Duration retryAfterDe(HttpStatusCodeException e) {
        HttpHeaders headers = e.getResponseHeaders();
        String valor = headers == null ? null : headers.getFirst("Retry-After");
        if (valor == null) {
            return RETRY_AFTER_PADRAO;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(valor.trim()));
        } catch (NumberFormatException ex) {
            return RETRY_AFTER_PADRAO;
        }
    }
}
