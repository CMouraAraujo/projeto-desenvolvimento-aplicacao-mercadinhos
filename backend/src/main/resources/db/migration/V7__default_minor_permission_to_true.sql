-- Novos registros são livres por padrão. Classificações anteriores são preservadas.
ALTER TABLE categorias ALTER COLUMN permitido_para_menores SET DEFAULT TRUE;
ALTER TABLE produtos ALTER COLUMN permitido_para_menores SET DEFAULT TRUE;
