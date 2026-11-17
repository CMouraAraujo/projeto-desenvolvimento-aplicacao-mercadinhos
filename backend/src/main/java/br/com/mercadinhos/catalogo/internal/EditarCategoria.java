package br.com.mercadinhos.catalogo.internal;

import jakarta.validation.constraints.*;

record EditarCategoria(@NotNull @PositiveOrZero Long versao, @NotBlank @Size(max = 100) String nome) { }
