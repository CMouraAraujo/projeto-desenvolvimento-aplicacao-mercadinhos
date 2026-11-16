package br.com.mercadinhos.identidade.internal;

import br.com.mercadinhos.identidade.Acao;
import br.com.mercadinhos.identidade.Modulo;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PermissaoModulo {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Modulo modulo;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Acao acao;
}
