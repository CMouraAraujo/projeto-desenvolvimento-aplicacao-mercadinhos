-- Cadastros existentes permanecem sem custo/markup até serem preenchidos; não inventar valores históricos.
ALTER TABLE produtos
    ADD COLUMN valor_compra DECIMAL(11,2) NULL,
    ADD COLUMN percentual_markup DECIMAL(7,2) NULL,
    ADD COLUMN preco_venda_padrao DECIMAL(11,2) NULL,
    ADD CONSTRAINT ck_produtos_compra CHECK (valor_compra > 0),
    ADD CONSTRAINT ck_produtos_markup CHECK (percentual_markup BETWEEN 0 AND 10000),
    ADD CONSTRAINT ck_produtos_preco_padrao CHECK (preco_venda_padrao > 0),
    ADD CONSTRAINT ck_produtos_precificacao CHECK (
        (valor_compra IS NULL AND percentual_markup IS NULL AND preco_venda_padrao IS NULL)
        OR (valor_compra IS NOT NULL AND percentual_markup IS NOT NULL
            AND (preco_venda_padrao IS NULL OR preco_venda_padrao = ROUND(valor_compra * (1 + percentual_markup / 100), 2)))
    );
