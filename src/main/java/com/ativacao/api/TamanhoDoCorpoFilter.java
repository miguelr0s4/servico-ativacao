package com.ativacao.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TamanhoDoCorpoFilter extends OncePerRequestFilter {

    private final long limiteBytes;

    public TamanhoDoCorpoFilter(@Value("${app.max-request-body-bytes:10240}") long limiteBytes) {
        this.limiteBytes = limiteBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > limiteBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"title\":\"Corpo da requisicao muito grande\",\"status\":413,\"limiteBytes\":%d}"
                            .formatted(limiteBytes));
            return;
        }
        chain.doFilter(request, response);
    }
}
