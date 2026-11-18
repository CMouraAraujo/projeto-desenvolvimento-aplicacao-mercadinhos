package br.com.mercadinhos.catalogo.internal;

import java.time.Instant;
import java.math.BigDecimal;

record ProdutoDTO(Long id, String nome, String codigoBarras, Long categoriaId, UnidadeMedida unidadeMedida,
                  String descricao, String marca, BigDecimal valorCompra, BigDecimal percentualMarkup, BigDecimal precoVendaPadrao, boolean permiteVendaFracionada, boolean controlaLote,
                  boolean controlaValidade, boolean permitidoParaMenores, boolean ativo, long versao, DadosFiscaisDTO dadosFiscais,
                  String imagemUrl, Instant criadoEm, Instant atualizadoEm, Long criadoPor, Long atualizadoPor) { }
