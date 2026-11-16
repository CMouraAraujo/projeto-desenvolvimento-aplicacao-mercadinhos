# Escopo do módulo de produtos

## Objetivo

Manter um catálogo de produtos por organização, preparado para uso futuro em compras, estoque, planograma e vendas por autoatendimento.

## Fluxo principal

1. Usuário autenticado e autorizado abre a página Produtos.
2. Consulta a listagem ou aciona Cadastrar produto.
3. O sistema abre um modal com os campos do cadastro.
4. O usuário preenche os dados e salva.
5. O backend valida os dados, a organização e a autorização, e persiste o cadastro.
6. O modal apresenta os erros ou fecha após sucesso, atualizando a listagem.

Em caso de erro, o formulário deve preservar os dados preenchidos. Fechar um formulário alterado deve solicitar confirmação de descarte.

## Organização da interface

Proposta: modal amplo com abas Geral, Operacional e Fiscal, ações Cancelar e Salvar e indicação de campos obrigatórios. A seleção de mercadinhos será adicionada posteriormente, na parte inferior do modal.

A listagem deverá permitir busca por nome ou código de barras e filtros por categoria e situação. Paginação deve ocorrer no backend.

## Campos

| Grupo | Campo | Regra ou situação |
| --- | --- | --- |
| Identificação | ID interno | Gerado pelo sistema; proposta de chave primária técnica |
| Identificação | Nome | Obrigatório; não precisa ser único |
| Identificação | Código de barras | Obrigatório; único dentro da organização; armazenado como texto |
| Identificação | Imagem | Uma imagem inicialmente; obrigatoriedade pendente |
| Identificação | Descrição | Opcional |
| Identificação | Marca | Campo adicional proposto, opcional |
| Organização | Categoria | Cadastrada pelo administrador; proposta de preenchimento obrigatório |
| Operacional | Unidade de medida | Proposta de preenchimento obrigatório |
| Operacional | Permite venda fracionada | Campo proposto; comportamento de venda será futuro |
| Operacional | Controla lote | Campo proposto; não cadastra lotes nesta etapa |
| Operacional | Controla validade | Campo proposto; não cadastra validades nesta etapa |
| Situação | Ativo/inativo | Proposta para preservar histórico em vez de excluir produtos |
| Fiscal | NCM | Solicitação inicial; validação e obrigatoriedade a definir |
| Fiscal | CEST | Solicitação inicial; aplicabilidade a definir |
| Fiscal | CFOP | Solicitação inicial; tratar como configuração por operação |
| Fiscal | CST de PIS | Solicitação inicial |
| Fiscal | CST de COFINS | Solicitação inicial |
| Fiscal | Situação tributária de ICMS | Considerar CST/CSOSN conforme regime |
| Fiscal | Base de cálculo de ICMS | Definir se a intenção é modalidade, redução ou outra regra; não salvar valor fixo da operação no produto |
| Fiscal | Origem da mercadoria | Campo adicional proposto |
| Auditoria | Criação e última alteração | Data e usuário responsável, preenchidos pelo sistema |

Os enquadramentos fiscais e as obrigatoriedades deverão ser validados com a contabilidade antes de qualquer emissão fiscal. Registrar dados fiscais não equivale a implementar cálculo tributário ou emissão de documentos.

## Regras de negócio

- Produtos e categorias pertencem à organização, e não ao usuário que os cadastrou.
- O código de barras do mesmo produto pode existir em organizações diferentes.
- Uma organização não pode ter dois produtos com o mesmo código de barras, mesmo que um esteja inativo; a interface deve orientar a edição ou reativação do registro existente.
- Nomes podem se repetir. Apresentações distintas, como lata de 350 ml e garrafa de 2 l, terão cadastros distintos.
- Uma categoria associada deve pertencer à mesma organização do produto.
- O vínculo futuro com um mercadinho permite trabalhar com o produto, mas não cria saldo de estoque.
- Preço por unidade, quantidade em estoque, lote recebido e data de validade de cada lote não pertencem ao cadastro central.
- O backend deve validar as regras independentemente das validações da interface.
- O escopo inicial considera um código de barras por produto. Embalagens com códigos adicionais exigirão evolução específica.

A aceitação de formatos de código, limites dos campos, tamanho e formato das imagens ainda deve ser especificada. Código de barras obrigatório não implica restringir todo cadastro a EAN-13.

## Categorias

Proposta inicial: cadastrar, listar, editar e inativar categorias, sem hierarquia de subcategorias. Impedir nomes duplicados na organização. Categoria inativa permanece nos produtos existentes e não fica disponível para novas associações.

## Critérios de aceite previstos

- Um usuário sem acesso ao módulo não pode consultar nem alterar produtos pela API.
- Um usuário de uma organização não consegue acessar produtos ou categorias de outra, mesmo conhecendo seus IDs.
- Nome ou código de barras ausente impede salvar.
- Tentativas simultâneas de cadastrar o mesmo código na mesma organização resultam em apenas um registro.
- Organizações diferentes podem cadastrar o mesmo código.
- A edição preserva o ID interno.
- Falha na gravação mantém os dados do formulário e apresenta mensagem compreensível.
- Produtos inativos ficam identificados e podem ser filtrados.
- As regras definitivas de campos obrigatórios e upload serão acrescentadas aos critérios após confirmação.

## Fora desta entrega

Estoque, compras, fornecedores, movimentações, inventário, preços por mercadinho, seleção de unidades, editor de planograma, emissão fiscal, pagamentos, integração com tablets, operação offline e busca automática de imagens ou códigos na internet.
