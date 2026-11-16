package br.com.mercadinhos.identidade.internal;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
final class RevalidacaoSessaoFilter extends OncePerRequestFilter {
    private final AutenticacaoService autenticacao;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessaoUsuario sessao
                && !autenticacao.sessaoValida(sessao)) {
            SecurityContextHolder.clearContext();
            var httpSession = request.getSession(false);
            if (httpSession != null) { httpSession.invalidate(); }
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
