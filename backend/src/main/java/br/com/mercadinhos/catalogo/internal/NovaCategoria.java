package br.com.mercadinhos.catalogo.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record NovaCategoria(@NotBlank @Size(max = 100) String nome, Boolean permitidoParaMenores) { }
