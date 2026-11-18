package br.com.mercadinhos.catalogo.internal;

import java.util.List;
import br.com.mercadinhos.identidade.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ProdutosService {
    private final ProdutoRepository produtos;
    private final CategoriaRepository categorias;
    private final ProdutoMapper mapper;
    private final AcessoAtual acesso;

    record Pagina(List<ProdutoDTO> itens, int pagina, int tamanho, long total) { }

    public Pagina listar(@Min(0) int pagina, @Min(1) @Max(100) int tamanho,
            @Size(max = 200) String busca, @Positive Long categoriaId, Boolean ativo) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        var resultado = produtos.listar(acesso.organizacaoId(), ativo, categoriaId, NomesCategoria.filtroLiteral(busca),
                PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id"))));
        return new Pagina(resultado.stream().map(mapper::resposta).toList(), pagina, tamanho, resultado.getTotalElements());
    }

    public ProdutoDTO obter(@Positive Long id) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        return mapper.resposta(produtos.findByIdAndOrganizacaoId(id, acesso.organizacaoId()).orElseThrow(ProdutosService::naoEncontrado));
    }

    public ProdutoDTO porCodigo(@NotBlank @Pattern(regexp = "[0-9]{1,64}") String codigo) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        return mapper.resposta(produtos.findByCodigoBarrasAndOrganizacaoId(codigo, acesso.organizacaoId()).orElseThrow(ProdutosService::naoEncontrado));
    }

    @Transactional
    public ProdutoDTO cadastrar(@Valid DadosProduto dados) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CADASTRAR);
        Long org = acesso.organizacaoId();
        validarNome(dados.nome());
        var categoria = exigirCategoriaAtiva(dados.categoriaId(), org);
        return mapper.resposta(produtos.saveAndFlush(new Produto(org, dados, acesso.usuarioId(), categoria.isPermitidoParaMenores())));
    }

    @Transactional
    public ProdutoDTO editar(@Positive Long id, @Valid AtualizarProduto alteracao) {
        acesso.exigir(Modulo.PRODUTOS, Acao.EDITAR);
        var produto = paraAlterar(id, alteracao.versao());
        var dados = alteracao.dados();
        validarNome(dados.nome());
        boolean permitidoParaMenores = produto.isPermitidoParaMenores();
        if (!produto.getCategoriaId().equals(dados.categoriaId())) {
            permitidoParaMenores = exigirCategoriaAtiva(dados.categoriaId(), produto.getOrganizacaoId()).isPermitidoParaMenores();
        }
        produto.preencher(dados, acesso.usuarioId(), permitidoParaMenores);
        produtos.flush();
        return mapper.resposta(produto);
    }

    @Transactional
    public ProdutoDTO situacao(@Positive Long id, @Valid SituacaoProduto dados) {
        acesso.exigir(Modulo.PRODUTOS, Acao.INATIVAR);
        var produto = paraAlterar(id, dados.versao());
        produto.alterarSituacao(dados.ativo(), acesso.usuarioId());
        produtos.flush();
        return mapper.resposta(produto);
    }

    Produto paraAlterar(Long id, long versao) {
        var produto = produtos.buscarParaAlterar(id, acesso.organizacaoId()).orElseThrow(ProdutosService::naoEncontrado);
        if (produto.getVersao() != versao) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Produto alterado; consulte a versão atual");
        }
        return produto;
    }

    private Categoria exigirCategoriaAtiva(Long categoriaId, Long org) {
        var categoria = categorias.buscarParaAlterar(categoriaId, org)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada"));
        if (!categoria.isAtiva()) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Selecione uma categoria ativa"); }
        return categoria;
    }

    private static void validarNome(String nome) {
        String normalizado = NomesCategoria.normalizar(nome);
        if (normalizado.isEmpty() || normalizado.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome deve ter de 1 a 200 caracteres");
        }
    }

    static ResponseStatusException naoEncontrado() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado"); }
}
