package br.com.mercadinhos.identidade.internal;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
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

    @Version
    @Column(nullable = false)
    private long versao;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm = Instant.now();
    @ElementCollection
    @CollectionTable(name = "permissoes_modulo", joinColumns = @JoinColumn(name = "vinculo_id"))
    @BatchSize(size = 100)
    private Set<PermissaoModulo> permissoes = new HashSet<>();

    void substituirPermissoes(Set<PermissaoModulo> novas) {
        permissoes.clear();
        permissoes.addAll(novas);
        atualizadoEm = Instant.now();
    }

    void alterarSituacao(boolean ativo) {
        usuario.alterarSituacao(ativo);
        this.ativo = ativo;
        atualizadoEm = Instant.now();
    }

    VinculoOrganizacao(Organizacao organizacao, Usuario usuario, Papel papel) {
        this.organizacao = organizacao;
        this.usuario = usuario;
        this.papel = papel;
    }
}
