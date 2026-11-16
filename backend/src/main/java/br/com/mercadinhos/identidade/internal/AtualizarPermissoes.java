package br.com.mercadinhos.identidade.internal;

import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

record AtualizarPermissoes(
        @NotNull @PositiveOrZero Long versao,
        @NotNull @Size(max = 100) Set<@NotNull @Valid PermissaoDTO> permissoes) { }
