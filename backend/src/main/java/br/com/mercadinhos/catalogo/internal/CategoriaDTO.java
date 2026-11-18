package br.com.mercadinhos.catalogo.internal;

import java.time.Instant;

record CategoriaDTO(Long id, String nome, boolean ativa, boolean permitidoParaMenores, long versao, Instant criadoEm, Instant atualizadoEm) {
    static CategoriaDTO de(Categoria categoria) {
        return new CategoriaDTO(categoria.getId(), categoria.getNome(), categoria.isAtiva(), categoria.isPermitidoParaMenores(), categoria.getVersao(),
                categoria.getCriadoEm(), categoria.getAtualizadoEm());
    }
}
