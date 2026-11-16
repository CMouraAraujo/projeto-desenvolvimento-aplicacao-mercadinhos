package br.com.mercadinhos.identidade.internal;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Autenticação")
class PermissoesController {
    private final ContextoAcesso contexto;

    @GetMapping("/api/auth/permissoes")
    @Operation(summary = "Consultar as próprias permissões efetivas")
    @SecurityRequirement(name = "sessao")
    @Transactional(readOnly = true)
    List<PermissaoDTO> minhasPermissoes() {
        return UsuariosService.permissoesEfetivas(contexto.vinculoAtual());
    }
}
