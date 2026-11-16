# Modelo conceitual de dados

Proposta de modelagem, ainda sem migrations ou schema executável. Tipos, tamanhos, índices completos e estratégia de IDs serão definidos na implementação.

## Identidade e acesso

- Organizacao: representa a empresa cliente, com ID, nome, situação e datas de auditoria.
- Usuario: identidade de autenticação; não é o proprietário direto de produtos ou mercadinhos.
- VinculoOrganizacao: relaciona usuário à organização e registra seu papel e situação.
- PermissaoModulo: acesso do vínculo aos módulos. Granularidade por ação a confirmar.
- Convite: proposta para ativar o primeiro administrador e adicionar funcionários, com expiração e uso único.

O vínculo separado evita que os dados de negócio dependam da permanência de um usuário. Permitir que uma identidade participe de várias organizações é uma decisão pendente, não uma funcionalidade prometida para o MVP.

## Catálogo

### Categoria

Campos previstos: id, organizacao_id, nome, ativo, criado_em, atualizado_em e responsáveis pela criação e alteração.

Proposta: nome único por organização, com política explícita de comparação e normalização. Não excluir categorias já referenciadas.

### Produto

Campos previstos: id, organizacao_id, categoria_id, nome, codigo_barras, descricao, marca, imagem_referencia, unidade_medida, permite_venda_fracionada, controla_lote, controla_validade, ativo e auditoria.

Regras estruturais:

- Chave primária: id interno, estável e gerado pelo sistema.
- Restrição única: organizacao_id e codigo_barras.
- codigo_barras é texto, preservando zeros à esquerda.
- Nome não é chave e não exige unicidade.
- Categoria e produto devem pertencer à mesma organização.
- Prever mecanismo de concorrência para evitar perda silenciosa de alterações simultâneas.

A chave técnica é uma recomendação de modelagem em substituição à proposta inicial de usar nome e código de barras como chaves primárias.

### Classificação e configuração fiscal

Prever classificação do produto, incluindo NCM, CEST quando aplicável e origem, e configuração tributária própria da organização/operação.

CFOP, CST/CSOSN, PIS, COFINS e regras da base de ICMS precisam de especificação fiscal antes da modelagem definitiva. Uma configuração pode oferecer padrões para o produto, mas a tributação efetiva será determinada no contexto da operação futura.

Códigos fiscais devem ser tratados como códigos, preservando sua representação e zeros à esquerda. Nenhuma regra fiscal específica está homologada por este documento.

## Relacionamentos futuros

- Organizacao possui vários Mercadinhos.
- ProdutoMercadinho relaciona produto e unidade da mesma organização, com disponibilidade e futura configuração comercial.
- Disponibilidade não representa quantidade física.
- MovimentacaoEstoque registra alterações de quantidade por produto e local.
- Lote recebido guarda os dados de lote e validade quando exigidos.

Esses relacionamentos não exigem criar agora tabelas vazias para módulos ainda fora do escopo.

## Exemplo de isolamento

A organização A cadastra um produto com código 00012345. A organização B também pode cadastrar esse código. Um segundo cadastro de 00012345 na organização A deve ser rejeitado, independentemente do nome ou da situação do registro existente.
