package br.com.mercadinhos.catalogo.internal;

import java.util.List;
import br.com.mercadinhos.identidade.Acao;
import br.com.mercadinhos.identidade.AcessoAtual;
import br.com.mercadinhos.identidade.Modulo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CategoriasService {
    private final CategoriaRepository categorias;
    private final AcessoAtual acesso;

    record Pagina(List<CategoriaDTO> itens, int pagina, int tamanho, long total) { }

    public Pagina listar(@Min(0) int pagina, @Min(1) @Max(100) int tamanho, @Size(max = 100) String busca, Boolean ativa) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        var resultado = categorias.listar(acesso.organizacaoId(), ativa, NomesCategoria.filtroLiteral(busca),
                PageRequest.of(pagina, tamanho, Sort.by("nome").ascending().and(Sort.by("id"))));
        return new Pagina(resultado.stream().map(CategoriaDTO::de).toList(), pagina, tamanho, resultado.getTotalElements());
    }

    public CategoriaDTO obter(@Positive Long id) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR);
        return CategoriaDTO.de(categorias.findByIdAndOrganizacaoId(id, acesso.organizacaoId())
                .orElseThrow(CategoriasService::naoEncontrada));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaDTO cadastrar(@Valid NovaCategoria dados) {
        acesso.exigir(Modulo.PRODUTOS, Acao.CADASTRAR);
        return CategoriaDTO.de(categorias.saveAndFlush(new Categoria(acesso.organizacaoId(), nomeValido(dados.nome()))));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaDTO editar(@Positive Long id, @Valid EditarCategoria dados) {
        acesso.exigir(Modulo.PRODUTOS, Acao.EDITAR);
        var categoria = paraAlterar(id, dados.versao());
        categoria.renomear(nomeValido(dados.nome()));
        categorias.flush();
        return CategoriaDTO.de(categoria);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaDTO situacao(@Positive Long id, @Valid SituacaoCategoria dados) {
        acesso.exigir(Modulo.PRODUTOS, Acao.INATIVAR);
        var categoria = paraAlterar(id, dados.versao());
        categoria.alterarSituacao(dados.ativa());
        categorias.flush();
        return CategoriaDTO.de(categoria);
    }

    private Categoria paraAlterar(Long id, Long versao) {
        var categoria = categorias.buscarParaAlterar(id, acesso.organizacaoId())
                .orElseThrow(CategoriasService::naoEncontrada);
        if (categoria.getVersao() != versao) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Categoria alterada; consulte a versão atual");
        }
        return categoria;
    }

    private static String nomeValido(String nome) {
        String normalizado = NomesCategoria.normalizar(nome);
        if (normalizado.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o nome da categoria");
        }
        return normalizado;
    }

    private static ResponseStatusException naoEncontrada() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoria não encontrada");
    }
}
