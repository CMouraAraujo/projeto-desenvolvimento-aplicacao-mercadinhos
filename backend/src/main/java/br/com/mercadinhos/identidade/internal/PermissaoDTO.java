package br.com.mercadinhos.identidade.internal;

import br.com.mercadinhos.identidade.Acao;
import br.com.mercadinhos.identidade.Modulo;
import jakarta.validation.constraints.NotNull;

record PermissaoDTO(@NotNull Modulo modulo, @NotNull Acao acao) { }
