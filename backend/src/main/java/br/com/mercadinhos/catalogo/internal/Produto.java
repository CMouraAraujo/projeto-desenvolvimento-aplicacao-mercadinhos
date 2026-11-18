package br.com.mercadinhos.catalogo.internal;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "produtos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Produto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "organizacao_id", nullable = false, updatable = false) private Long organizacaoId;
    @Column(name = "categoria_id", nullable = false) private Long categoriaId;
    @Column(nullable = false, length = 200) private String nome;
    @Column(name = "codigo_barras", nullable = false, length = 64) private String codigoBarras;
    @Column(length = 4000) private String descricao;
    @Column(length = 100) private String marca;
    @Enumerated(EnumType.STRING) @Column(name = "unidade_medida", nullable = false, length = 10) private UnidadeMedida unidadeMedida;
    @Column(name = "permite_venda_fracionada", nullable = false) private boolean permiteVendaFracionada;
    @Column(name = "controla_lote", nullable = false) private boolean controlaLote;
    @Column(name = "controla_validade", nullable = false) private boolean controlaValidade;
    @Column(name = "permitido_para_menores", nullable = false) private boolean permitidoParaMenores;
    @Column(nullable = false) private boolean ativo = true;
    @Version @Column(nullable = false) private long versao;
    @Column(name = "imagem_chave", length = 40) private String imagemChave;
    @Embedded private DadosFiscais dadosFiscais;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm;
    @Column(name = "criado_por", nullable = false, updatable = false) private Long criadoPor;
    @Column(name = "atualizado_por", nullable = false) private Long atualizadoPor;

    Produto(Long organizacaoId, DadosProduto dados, Long autor, boolean permitidoParaMenores) {
        this.organizacaoId = organizacaoId;
        criadoPor = autor;
        criadoEm = Instant.now();
        preencher(dados, autor, permitidoParaMenores);
    }

    void preencher(DadosProduto dados, Long autor, boolean permitidoParaMenores) {
        nome = NomesCategoria.normalizar(dados.nome());
        codigoBarras = dados.codigoBarras();
        categoriaId = dados.categoriaId();
        this.permitidoParaMenores = permitidoParaMenores;
        unidadeMedida = dados.unidadeMedida();
        descricao = limpar(dados.descricao());
        marca = limpar(dados.marca());
        permiteVendaFracionada = Boolean.TRUE.equals(dados.permiteVendaFracionada());
        controlaLote = Boolean.TRUE.equals(dados.controlaLote());
        controlaValidade = Boolean.TRUE.equals(dados.controlaValidade());
        dadosFiscais = DadosFiscais.de(dados.dadosFiscais());
        registrarAlteracao(autor);
    }

    void alterarSituacao(boolean ativo, Long autor) { this.ativo = ativo; registrarAlteracao(autor); }
    void alterarImagem(String chave, Long autor) { imagemChave = chave; registrarAlteracao(autor); }
    private void registrarAlteracao(Long autor) { atualizadoPor = autor; atualizadoEm = Instant.now(); }
    private static String limpar(String valor) { return valor == null || valor.isBlank() ? null : valor.strip(); }
}
