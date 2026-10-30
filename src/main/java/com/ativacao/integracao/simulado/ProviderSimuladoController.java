package com.ativacao.integracao.simulado;

import com.ativacao.integracao.AtivarRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/provider/v1")
public class ProviderSimuladoController {

    private final Map<String, Boolean> processadosPorIdempotencyKey = new ConcurrentHashMap<>();

    @PostMapping("/activate")
    public ResponseEntity<Void> ativar(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @RequestBody AtivarRequestDto request) throws InterruptedException {
        if (processadosPorIdempotencyKey.containsKey(idempotencyKey)) {
            return ResponseEntity.ok().build();
        }

        String codigo = request.productCode();
        if (codigo.endsWith("-ERRO400")) {
            return ResponseEntity.badRequest().build();
        }
        if (codigo.endsWith("-ERRO500")) {
            return ResponseEntity.internalServerError().build();
        }
        if (codigo.endsWith("-RATELIMIT")) {
            return ResponseEntity.status(429).header("Retry-After", "2").build();
        }
        if (codigo.endsWith("-INDISPONIVEL")) {
            return ResponseEntity.status(503).header("Retry-After", "5").build();
        }
        if (codigo.endsWith("-TIMEOUT")) {
            Thread.sleep(Duration.ofSeconds(10));
        }

        processadosPorIdempotencyKey.put(idempotencyKey, true);
        return ResponseEntity.ok().build();
    }
}
