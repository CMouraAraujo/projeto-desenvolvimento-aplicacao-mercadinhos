package br.com.mercadinhos.catalogo.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

record DadosProduto(
        @NotBlank @Size(max = 200) String nome,
        @NotBlank @Pattern(regexp = "[0-9]{1,64}")
        @Schema(description = "Código numérico como texto; zeros à esquerda são preservados. Não valida dígito GTIN.") String codigoBarras,
        @NotNull @Positive Long categoriaId,
        @NotNull UnidadeMedida unidadeMedida,
        @Size(max = 4000) String descricao,
        @Size(max = 100) String marca,
        @NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
        BigDecimal valorCompra,
        @NotNull @DecimalMin("0") @DecimalMax("10000") @Digits(integer = 5, fraction = 2)
        @Schema(description = "Acréscimo sobre o custo em percentual: 50 representa custo multiplicado por 1,50.") BigDecimal percentualMarkup,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2)
        @Schema(description = "Opcional. Quando informado, deve corresponder ao custo × (1 + markup / 100), arredondado a centavos HALF_UP.") BigDecimal precoVendaPadrao,
        Boolean permiteVendaFracionada,
        Boolean controlaLote,
        Boolean controlaValidade,
        @Valid DadosFiscaisDTO dadosFiscais) { }
