package br.com.mercadinhos.identidade.internal;

import java.net.URI;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Usuários", description = "Gestão exclusiva de administradores, limitada à própria organização")
@SecurityRequirement(name = "sessao")
@ApiResponse(responseCode = "401", description = "Sessão ausente ou revogada")
@ApiResponse(responseCode = "403", description = "Administrador obrigatório ou CSRF inválido")
class UsuariosController {
    private final UsuariosService usuarios;

    @GetMapping("/api/usuarios")
    @Operation(summary = "Listar usuários da organização", description = "Inclui administradores e funcionários; paginação limitada a 100 itens.")
    UsuariosService.Pagina listar(@RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho) {
        return usuarios.listar(pagina, tamanho);
    }

    @GetMapping("/api/usuarios/{id}")
    @Operation(summary = "Consultar usuário da própria organização")
    @ApiResponse(responseCode = "404", description = "Usuário inexistente ou de outra organização")
    UsuarioDTO obter(@PathVariable @Positive Long id) { return usuarios.obter(id); }

    @PostMapping("/api/usuarios")
    @Operation(summary = "Cadastrar funcionário", description = "Não permite escolher organização ou papel. A senha inicial deve ser entregue por canal seguro.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "201", description = "Funcionário criado")
    @ApiResponse(responseCode = "400", description = "Dados ou permissões inválidos")
    @ApiResponse(responseCode = "409", description = "Conflito de cadastro")
    ResponseEntity<UsuarioDTO> cadastrar(@RequestBody @Valid NovoFuncionario dados) {
        var usuario = usuarios.cadastrar(dados);
        return ResponseEntity.created(URI.create("/api/usuarios/" + usuario.id())).body(usuario);
    }

    @PutMapping("/api/usuarios/{id}/permissoes")
    @Operation(summary = "Substituir permissões do funcionário", description = "Lista vazia revoga tudo. Exige a versão atual; sessões anteriores serão revogadas.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "409", description = "Versão desatualizada")
    @ApiResponse(responseCode = "404", description = "Usuário inexistente ou de outra organização")
    UsuarioDTO permissoes(@PathVariable @Positive Long id, @RequestBody @Valid AtualizarPermissoes dados) {
        return usuarios.atualizarPermissoes(id, dados);
    }

    @PatchMapping("/api/usuarios/{id}/situacao")
    @Operation(summary = "Ativar ou desativar funcionário", description = "Exige versão atual. Não permite desativar administradores.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "409", description = "Versão desatualizada")
    @ApiResponse(responseCode = "404", description = "Usuário inexistente ou de outra organização")
    UsuarioDTO situacao(@PathVariable @Positive Long id, @RequestBody @Valid AtualizarSituacao dados) {
        return usuarios.atualizarSituacao(id, dados);
    }

    @GetMapping("/api/modulos")
    @Operation(summary = "Listar módulos e ações disponíveis", description = "PRODUTOS está preparado para o próximo módulo; sua API ainda não foi implementada.")
    List<UsuariosService.ModuloDTO> modulos() { return usuarios.modulos(); }
}
