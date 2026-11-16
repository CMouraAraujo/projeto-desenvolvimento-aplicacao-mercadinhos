package br.com.mercadinhos.identidade.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
class AutenticacaoController {
    private final VinculoRepository vinculos;

    record TokenCsrf(String headerName, String token) { }
    record UsuarioAtual(Long usuarioId, String nome, String email, Long organizacaoId, String papel) { }
    record OrganizacaoAtual(Long id, String nome) { }

    @GetMapping("/api/auth/csrf")
    TokenCsrf csrf(CsrfToken token) { return new TokenCsrf(token.getHeaderName(), token.getToken()); }

    @GetMapping("/api/auth/me")
    UsuarioAtual me(@AuthenticationPrincipal SessaoUsuario sessao) {
        var vinculo = vinculoAtual(sessao);
        return new UsuarioAtual(vinculo.getUsuario().getId(), vinculo.getUsuario().getNome(),
                vinculo.getUsuario().getEmail(), vinculo.getOrganizacao().getId(), vinculo.getPapel().name());
    }

    @GetMapping("/api/organizacao")
    OrganizacaoAtual organizacao(@AuthenticationPrincipal SessaoUsuario sessao) {
        var organizacao = vinculoAtual(sessao).getOrganizacao();
        return new OrganizacaoAtual(organizacao.getId(), organizacao.getNome());
    }

    private VinculoOrganizacao vinculoAtual(SessaoUsuario sessao) {
        if (sessao == null) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        return vinculos.findByUsuarioId(sessao.getUsuarioId())
                .filter(v -> v.getOrganizacao().getId().equals(sessao.getOrganizacaoId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
