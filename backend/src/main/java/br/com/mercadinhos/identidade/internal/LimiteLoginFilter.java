package br.com.mercadinhos.identidade.internal;

import java.io.IOException;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

final class LimiteLoginFilter extends OncePerRequestFilter {
    private final Cache<String, AtomicInteger> tentativas = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(15)).build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod())
                && (request.getContextPath() + "/api/auth/login").equals(request.getRequestURI())) {
            if (tentativas.get(request.getRemoteAddr(), key -> new AtomicInteger()).incrementAndGet() > 30) {
                response.setStatus(429);
                response.setHeader("Retry-After", "900");
                return;
            }
            String email = request.getParameter("email");
            String senha = request.getParameter("senha");
            if (email == null || email.isBlank() || email.length() > 254 || senha == null
                    || senha.isEmpty() || senha.getBytes(StandardCharsets.UTF_8).length > 72) {
                response.setStatus(401);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
