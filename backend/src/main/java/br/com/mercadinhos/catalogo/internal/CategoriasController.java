package br.com.mercadinhos.catalogo.internal;

import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Categorias por organização; manutenção exclusiva do administrador")
@SecurityRequirement(name = "sessao")
@ApiResponse(responseCode = "401", description = "Sessão ausente ou revogada")
@ApiResponse(responseCode = "403", description = "Sem permissão ou token CSRF inválido")
@ApiResponse(responseCode = "400", description = "Dados inválidos")
class CategoriasController {
    private final CategoriasService categorias;

    @GetMapping
    @Operation(summary = "Listar categorias", description = "Exige PRODUTOS/CONSULTAR. Sem filtro ativa, inclui ativas e inativas; busca literal por nome.")
    CategoriasService.Pagina listar(@RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @RequestParam(required = false) @Size(max = 100) String busca,
            @RequestParam(required = false) Boolean ativa) {
        return categorias.listar(pagina, tamanho, busca, ativa);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar categoria da organização", description = "Exige PRODUTOS/CONSULTAR.")
    @ApiResponse(responseCode = "404", description = "Categoria inexistente ou de outra organização")
    CategoriaDTO obter(@PathVariable @Positive Long id) { return categorias.obter(id); }

    @PostMapping
    @Operation(summary = "Cadastrar categoria", description = "Somente administrador. Nome de até 100 caracteres, único por organização inclusive entre inativas; ignora caixa e acentos.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "201", description = "Categoria criada")
    @ApiResponse(responseCode = "409", description = "Nome já utilizado na organização")
    ResponseEntity<CategoriaDTO> cadastrar(@RequestBody @Valid NovaCategoria dados) {
        var categoria = categorias.cadastrar(dados);
        return ResponseEntity.created(URI.create("/api/categorias/" + categoria.id())).body(categoria);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Renomear categoria", description = "Somente administrador; exige versão atual.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "404", description = "Categoria inexistente ou de outra organização")
    @ApiResponse(responseCode = "409", description = "Nome duplicado ou versão desatualizada")
    CategoriaDTO editar(@PathVariable @Positive Long id, @RequestBody @Valid EditarCategoria dados) {
        return categorias.editar(id, dados);
    }

    @PatchMapping("/{id}/situacao")
    @Operation(summary = "Inativar ou reativar categoria", description = "Somente administrador; preserva o registro e exige versão atual.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "404", description = "Categoria inexistente ou de outra organização")
    @ApiResponse(responseCode = "409", description = "Versão desatualizada")
    CategoriaDTO situacao(@PathVariable @Positive Long id, @RequestBody @Valid SituacaoCategoria dados) {
        return categorias.situacao(id, dados);
    }
}
