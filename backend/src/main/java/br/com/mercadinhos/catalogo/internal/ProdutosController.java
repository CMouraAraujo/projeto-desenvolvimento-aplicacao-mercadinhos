package br.com.mercadinhos.catalogo.internal;

import java.net.URI;
import java.util.List;
import br.com.mercadinhos.identidade.*;
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
@RequestMapping("/api/produtos")
@RequiredArgsConstructor
@Tag(name = "Produtos")
@SecurityRequirement(name = "sessao")
@ApiResponse(responseCode = "400", description = "Dados inválidos")
@ApiResponse(responseCode = "401", description = "Sessão ausente ou revogada")
@ApiResponse(responseCode = "403", description = "Sem permissão para a ação ou CSRF inválido")
class ProdutosController {
    private final ProdutosService produtos;
    private final AcessoAtual acesso;

    record Opcoes(List<UnidadeMedida> unidadesMedida, int imagemMaxBytes, List<String> formatosImagem) { }

    @GetMapping("/opcoes")
    @Operation(summary = "Consultar opções para o formulário de produtos")
    Opcoes opcoes() {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        return new Opcoes(List.of(UnidadeMedida.values()), ImagemProdutoService.MAX_BYTES, List.of("image/png", "image/jpeg"));
    }

    @GetMapping
    @Operation(summary = "Listar produtos", description = "Busca literal por nome ou código, filtro de categoria e situação. Inclui inativos se ativo não for informado.")
    ProdutosService.Pagina listar(@RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @RequestParam(required = false) @Size(max = 200) String busca,
            @RequestParam(required = false) @Positive Long categoriaId,
            @RequestParam(required = false) Boolean ativo) {
        return produtos.listar(pagina, tamanho, busca, categoriaId, ativo);
    }

    @GetMapping("/por-codigo/{codigo}")
    @Operation(summary = "Consultar produto pelo código de barras exato")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado na organização")
    ProdutoDTO porCodigo(@PathVariable @Pattern(regexp = "[0-9]{1,64}") String codigo) { return produtos.porCodigo(codigo); }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar produto da organização")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado na organização")
    ProdutoDTO obter(@PathVariable @Positive Long id) { return produtos.obter(id); }

    @PostMapping
    @Operation(summary = "Cadastrar produto", description = "Exige CADASTRAR. Categoria ativa da mesma organização. Código único, inclusive entre inativos. Dados fiscais opcionais, sem homologação tributária.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "201", description = "Produto cadastrado")
    @ApiResponse(responseCode = "409", description = "Código duplicado ou categoria inativa")
    ResponseEntity<ProdutoDTO> cadastrar(@RequestBody @Valid DadosProduto dados) {
        var produto = produtos.cadastrar(dados);
        return ResponseEntity.created(URI.create("/api/produtos/" + produto.id())).body(produto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Substituir os dados do produto", description = "Exige EDITAR e versão atual. Campos opcionais omitidos são limpos; imagem e situação são preservadas. Categoria inativa pode ser mantida, mas não escolhida como novo vínculo.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "409", description = "Versão desatualizada, código duplicado ou categoria inativa")
    ProdutoDTO editar(@PathVariable @Positive Long id, @RequestBody @Valid AtualizarProduto dados) { return produtos.editar(id, dados); }

    @PatchMapping("/{id}/situacao")
    @Operation(summary = "Inativar ou reativar produto", description = "Exige INATIVAR e versão atual. Preserva histórico e reserva o código de barras.")
    @SecurityRequirement(name = "csrf")
    @ApiResponse(responseCode = "409", description = "Versão desatualizada")
    ProdutoDTO situacao(@PathVariable @Positive Long id, @RequestBody @Valid SituacaoProduto dados) { return produtos.situacao(id, dados); }
}
