CREATE TABLE usuarios (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    nome VARCHAR(160) NOT NULL,
    senha_hash VARCHAR(255) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vinculos_organizacao (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organizacao_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    papel VARCHAR(30) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    CONSTRAINT uk_vinculos_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_vinculos_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    CONSTRAINT fk_vinculos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT ck_vinculos_papel CHECK (papel IN ('ADMIN', 'FUNCIONARIO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
