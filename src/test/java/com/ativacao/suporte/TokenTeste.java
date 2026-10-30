package com.ativacao.suporte;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

public final class TokenTeste {

    public static final String SECRET = "chave-de-teste-usada-somente-nos-testes-automatizados";

    private TokenTeste() {
    }

    public static final String SISTEMA_PADRAO = "sistema-cliente-teste";

    public static String comEscopos(String... escopos) {
        return comEscoposComoSistema(SISTEMA_PADRAO, escopos);
    }

    public static String comEscoposComoSistema(String sistema, String... escopos) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sistema)
                    .claim("scope", String.join(" ", escopos))
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
