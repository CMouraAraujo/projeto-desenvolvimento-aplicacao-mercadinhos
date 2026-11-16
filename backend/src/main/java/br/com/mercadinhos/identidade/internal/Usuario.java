package br.com.mercadinhos.identidade.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(nullable = false, length = 160)
    private String nome;
    @Column(name = "senha_hash", nullable = false, length = 255)
    private String senhaHash;
    @Column(nullable = false)
    private boolean ativo = true;
    @Version
    private long versao;

    void alterarSituacao(boolean ativo) { this.ativo = ativo; }

    Usuario(String email, String nome, String senhaHash) {
        this.email = email;
        this.nome = nome;
        this.senhaHash = senhaHash;
    }
}
