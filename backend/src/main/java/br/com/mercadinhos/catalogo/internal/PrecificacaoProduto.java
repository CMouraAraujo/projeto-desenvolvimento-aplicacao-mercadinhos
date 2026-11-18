package br.com.mercadinhos.catalogo.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class PrecificacaoProduto {
    private PrecificacaoProduto() { }

    static BigDecimal calcular(BigDecimal compra, BigDecimal percentual) {
        return compra.multiply(BigDecimal.ONE.add(percentual.movePointLeft(2))).setScale(2, RoundingMode.HALF_UP);
    }

    static void validar(DadosProduto dados) {
        if (dados.precoVendaPadrao() != null
                && dados.precoVendaPadrao().compareTo(calcular(dados.valorCompra(), dados.percentualMarkup())) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preço de venda padrão deve corresponder ao valor de compra com o acréscimo informado");
        }
    }
}
