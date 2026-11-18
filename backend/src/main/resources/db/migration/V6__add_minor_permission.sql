-- A classificação é apenas informativa nesta etapa; registros existentes começam como não permitidos.
ALTER TABLE categorias
    ADD COLUMN permitido_para_menores BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_categorias_permitido_menores CHECK (permitido_para_menores IN (0, 1));

ALTER TABLE produtos
    ADD COLUMN permitido_para_menores BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT ck_produtos_permitido_menores CHECK (permitido_para_menores IN (0, 1));
