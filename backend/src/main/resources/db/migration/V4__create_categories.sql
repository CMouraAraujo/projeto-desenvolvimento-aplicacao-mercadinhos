CREATE TABLE categorias (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organizacao_id BIGINT NOT NULL,
    nome VARCHAR(100) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    versao BIGINT NOT NULL DEFAULT 0,
    criado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    atualizado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_categorias_organizacao_nome UNIQUE (organizacao_id, nome),
    CONSTRAINT fk_categorias_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacoes (id),
    INDEX ix_categorias_organizacao_ativa_nome (organizacao_id, ativa, nome)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
