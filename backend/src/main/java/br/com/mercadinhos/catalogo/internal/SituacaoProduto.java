package br.com.mercadinhos.catalogo.internal;

import jakarta.validation.constraints.*;

record SituacaoProduto(@NotNull @PositiveOrZero Long versao, @NotNull Boolean ativo) { }
