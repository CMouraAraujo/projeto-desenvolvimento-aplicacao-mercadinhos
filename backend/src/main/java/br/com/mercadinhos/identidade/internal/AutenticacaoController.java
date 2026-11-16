package br.com.mercadinhos.identidade.internal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "Autenticação")
@RestController
@RequiredArgsConstructor
class AutenticacaoController {
    private final VinculoRepository vinculos;

    record TokenCsrf(String headerName, String token) { }
    record UsuarioAtual(Long usuarioId, String nome, String email, Long organizacaoId, String papel) { }
    record OrganizacaoAtual(Long id, String nome) { }

    @Operation(summary = "Obter token CSRF", description = "Cria ou reutiliza a sessão e retorna um token para os próximos POSTs.")
    @ApiResponse(responseCode = "200", description = "Token e nome do cabeçalho CSRF")
    @GetMapping("/api/auth/csrf")
    TokenCsrf csrf(@Parameter(hidden = true) CsrfToken token) { return new TokenCsrf(token.getHeaderName(), token.getToken()); }

    @Operation(summary = "Consultar a conta autenticada")
    @SecurityRequirement(name = "sessao")
    @ApiResponse(responseCode = "200", description = "Conta e organização da sessão")
    @ApiResponse(responseCode = "401", description = "Sessão ausente, expirada ou revogada")
    @GetMapping("/api/auth/me")
    UsuarioAtual me(@Parameter(hidden = true) @AuthenticationPrincipal SessaoUsuario sessao) {
        var vinculo = vinculoAtual(sessao);
        return new UsuarioAtual(vinculo.getUsuario().getId(), vinculo.getUsuario().getNome(),
                vinculo.getUsuario().getEmail(), vinculo.getOrganizacao().getId(), vinculo.getPapel().name());
    }

    @Operation(summary = "Consultar a organização atual", tags = {"Organização"},
            description = "A organização vem da sessão; parâmetros ou cabeçalhos do cliente não alteram esse contexto.")
    @SecurityRequirement(name = "sessao")
    @ApiResponse(responseCode = "200", description = "Organização da sessão")
    @ApiResponse(responseCode = "401", description = "Sessão ausente, expirada ou revogada")
    @GetMapping("/api/organizacao")
    OrganizacaoAtual organizacao(@Parameter(hidden = true) @AuthenticationPrincipal SessaoUsuario sessao) {
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
