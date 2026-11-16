package br.com.mercadinhos.identidade.internal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

record AtualizarSituacao(@NotNull @PositiveOrZero Long versao, @NotNull Boolean ativo) { }
