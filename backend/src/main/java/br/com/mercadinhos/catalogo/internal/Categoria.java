package br.com.mercadinhos.catalogo.internal;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categorias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Categoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(nullable = false, length = 100)
    private String nome;
    @Column(nullable = false)
    private boolean ativa = true;
    @Version
    @Column(nullable = false)
    private long versao;
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    Categoria(Long organizacaoId, String nome) {
        this.organizacaoId = organizacaoId;
        this.nome = nome;
        criadoEm = Instant.now();
        atualizadoEm = criadoEm;
    }

    void renomear(String nome) {
        this.nome = nome;
        atualizadoEm = Instant.now();
    }

    void alterarSituacao(boolean ativa) {
        this.ativa = ativa;
        atualizadoEm = Instant.now();
    }
}
