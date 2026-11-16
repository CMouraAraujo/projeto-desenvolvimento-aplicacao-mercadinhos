ALTER TABLE vinculos_organizacao
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN atualizado_em TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE TABLE permissoes_modulo (
    vinculo_id BIGINT NOT NULL,
    modulo VARCHAR(30) NOT NULL,
    acao VARCHAR(30) NOT NULL,
    PRIMARY KEY (vinculo_id, modulo, acao),
    CONSTRAINT fk_permissoes_vinculo FOREIGN KEY (vinculo_id) REFERENCES vinculos_organizacao (id) ON DELETE CASCADE,
    CONSTRAINT ck_permissoes_modulo CHECK (modulo IN ('PRODUTOS')),
    CONSTRAINT ck_permissoes_acao CHECK (acao IN ('CONSULTAR', 'CADASTRAR', 'EDITAR', 'INATIVAR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
