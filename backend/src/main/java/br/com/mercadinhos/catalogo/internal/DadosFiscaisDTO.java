package br.com.mercadinhos.catalogo.internal;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;

record DadosFiscaisDTO(
        @Pattern(regexp = "[0-9]{8}") String ncm,
        @Pattern(regexp = "[0-9]{7}") String cest,
        @Pattern(regexp = "[0-9]{4}") String cfopPadrao,
        @Pattern(regexp = "[0-9]{2}") String pisCst,
        @Pattern(regexp = "[0-9]{2}") String cofinsCst,
        @Pattern(regexp = "[0-8]") String origem,
        @Pattern(regexp = "[0-9]{2}") String icmsCst,
        @Pattern(regexp = "[0-9]{3}") String icmsCsosn,
        @Pattern(regexp = "[0-9]{1,2}") String icmsModalidadeBaseCalculo,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal icmsAliquota,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal icmsReducaoBase) {
    @AssertTrue(message = "Informe CST ou CSOSN de ICMS, não ambos")
    @JsonIgnore
    public boolean isSituacaoIcmsCompativel() { return icmsCst == null || icmsCsosn == null; }
}
