package br.com.mercadinhos.catalogo.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

record DadosProduto(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Pattern(regexp = "[0-9]{1,64}")
        @Schema(description = "Código numérico como texto; zeros à esquerda são preservados. Não valida dígito GTIN.") String codigoBarras,
        @NotNull @Positive Long categoriaId,
        @NotNull UnidadeMedida unidadeMedida,
        @Size(max = 4000) String descricao,
        @Size(max = 100) String marca,
        Boolean permiteVendaFracionada,
        Boolean controlaLote,
        Boolean controlaValidade,
        @Valid DadosFiscaisDTO dadosFiscais) { }
