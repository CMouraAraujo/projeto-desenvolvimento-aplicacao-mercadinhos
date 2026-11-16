package br.com.mercadinhos.identidade.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "vinculos_organizacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class VinculoOrganizacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizacao_id", nullable = false)
    private Organizacao organizacao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Papel papel;
    @Column(nullable = false)
    private boolean ativo = true;

    VinculoOrganizacao(Organizacao organizacao, Usuario usuario, Papel papel) {
        this.organizacao = organizacao;
        this.usuario = usuario;
        this.papel = papel;
    }
}
