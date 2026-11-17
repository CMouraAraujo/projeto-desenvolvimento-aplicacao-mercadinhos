package br.com.mercadinhos.catalogo.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record SituacaoCategoria(@NotNull @PositiveOrZero Long versao, @NotNull Boolean ativa) { }
