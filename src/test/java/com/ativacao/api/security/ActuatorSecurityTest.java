package com.ativacao.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorSecurityTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void prometheus_semToken_naoExigeAutenticacao() {
        var resposta = restTemplate.getForEntity("/actuator/prometheus", String.class);
        assertThat(resposta.getStatusCode()).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void healthLiveness_semToken_retorna200() {
        var resposta = restTemplate.getForEntity("/actuator/health/liveness", String.class);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
