package br.com.mercadinhos.catalogo.internal;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class DadosFiscais {
    @Column(name = "fiscal_ncm", length = 8) private String ncm;
    @Column(name = "fiscal_cest", length = 7) private String cest;
    @Column(name = "fiscal_cfop_padrao", length = 4) private String cfopPadrao;
    @Column(name = "fiscal_pis_cst", length = 2) private String pisCst;
    @Column(name = "fiscal_cofins_cst", length = 2) private String cofinsCst;
    @Column(name = "fiscal_origem", length = 1) private String origem;
    @Column(name = "fiscal_icms_cst", length = 2) private String icmsCst;
    @Column(name = "fiscal_icms_csosn", length = 3) private String icmsCsosn;
    @Column(name = "fiscal_icms_modalidade_base", length = 2) private String icmsModalidadeBaseCalculo;
    @Column(name = "fiscal_icms_aliquota", precision = 7, scale = 4) private BigDecimal icmsAliquota;
    @Column(name = "fiscal_icms_reducao_base", precision = 7, scale = 4) private BigDecimal icmsReducaoBase;

    static DadosFiscais de(DadosFiscaisDTO dto) {
        if (dto == null) { return null; }
        return new DadosFiscais(dto.ncm(), dto.cest(), dto.cfopPadrao(), dto.pisCst(), dto.cofinsCst(), dto.origem(),
                dto.icmsCst(), dto.icmsCsosn(), dto.icmsModalidadeBaseCalculo(), dto.icmsAliquota(), dto.icmsReducaoBase());
    }
}
