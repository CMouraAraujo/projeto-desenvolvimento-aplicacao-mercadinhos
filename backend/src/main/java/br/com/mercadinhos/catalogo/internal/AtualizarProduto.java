package br.com.mercadinhos.catalogo.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

record AtualizarProduto(@NotNull @PositiveOrZero Long versao, @NotNull @Valid DadosProduto dados) { }
