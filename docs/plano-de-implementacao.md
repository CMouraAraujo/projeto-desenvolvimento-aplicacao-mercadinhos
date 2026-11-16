# Plano de implementação

## Etapa 0: fechar o contrato inicial

Confirmar as decisões pendentes abaixo e atualizar esta documentação antes de implementar os comportamentos afetados. Inspecionar o ambiente local e fixar as versões da stack e os requisitos de execução.

## Etapa 1: fundação

- Criar backend e frontend com organização por módulos.
- Configurar MySQL e migrações.
- Implementar organização, autenticação e autorização básicas.
- Definir provisionamento do primeiro administrador sem credenciais padrão em produção.
- Preparar configuração por ambiente e tratamento consistente de erros.
- Testar isolamento entre organizações e bloqueio de ações não autorizadas.

Resultado: usuário autenticado opera somente no contexto permitido.

## Etapa 2: categorias

- Listar, cadastrar, editar e inativar categorias.
- Aplicar autorização de administrador e isolamento por organização.
- Definir unicidade de nome e tratamento de categorias em uso.

Resultado: categorias disponíveis para o cadastro de produtos.

## Etapa 3: produtos

- Implementar persistência, validações e API.
- Criar listagem paginada com busca e filtros.
- Criar modal de cadastro e edição.
- Implementar imagem conforme os limites escolhidos.
- Implementar inativação e auditoria.
- Documentar a API e o fluxo de execução local.

Resultado: fluxo completo de cadastro e consulta, com código de barras obrigatório.

## Etapa 4: validação da primeira entrega

- Exercitar cadastro, edição e inativação pela interface.
- Testar duplicidade de código, inclusive sob concorrência.
- Testar isolamento entre organizações, referências de categoria e acesso a imagens.
- Testar permissões pela API, sem depender de botões ocultos.
- Verificar comportamento do modal em falhas e telas menores.
- Revisar migrações e instruções de execução em ambiente limpo.

Resultado: primeira versão verificável e documentada.

## Evolução posterior

Mercadinhos e vínculo de produtos; compras e recebimento; movimentações e inventário; planograma; integração com tablets. A ordem detalhada será revista conforme a operação e as dependências, especialmente emissão fiscal, pagamento e operação offline.

## Decisões pendentes

| Tema | Proposta ou pergunta |
| --- | --- |
| Frontend e bibliotecas | Confirmar React/TypeScript, JPA, Spring Security e Flyway |
| Versões | Fixar versões compatíveis e mantidas na preparação do projeto |
| Identificador do produto | Confirmar ID técnico e unicidade do código por organização |
| Provisionamento | Confirmar criação de organização e convite do administrador pelo operador da plataforma |
| Permissões | Apenas acesso ao módulo ou também ações de consultar, cadastrar, editar e inativar? |
| Identidade | Uma conta poderá participar de mais de uma organização? |
| Campos obrigatórios | Nome e código são obrigatórios; confirmar categoria, unidade de medida, imagem e dados fiscais |
| Códigos | Definir formatos aceitos, normalização, limites e validação de dígito quando aplicável |
| Imagens | Definir formatos, tamanho máximo e armazenamento local de desenvolvimento e produção |
| Fiscal | Definir significado de base de cálculo, perfis tributários e validação com a contabilidade |
| Autenticação | Escolher sessões e fluxo de convite/recuperação |
| Ambiente | Confirmar ferramentas locais, hospedagem, backups e integração contínua |

Essas decisões não impedem a documentação inicial. Cada uma deve ser resolvida antes da implementação que depende dela.

## Primeiro commit

Mensagem sugerida: docs: documenta escopo e arquitetura inicial do sistema

O primeiro commit deve conter apenas os documentos iniciais. Nenhum código de aplicação, schema executável ou funcionalidade implementada é pressuposto.
