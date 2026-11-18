# Contrato de integração do frontend

Este documento orienta o chat responsável pelo frontend. A aplicação web ainda não está implementada. O contrato executável está em GET /v3/api-docs, com o backend no perfil local; consulte-o antes de criar tipos ou formulários.

## Separação do trabalho

Manter o backend neste chat e abrir outro para o frontend depois de validar e integrar a API de produtos. Usar branches/checkouts separados se os dois chats trabalharem simultaneamente; não alternar a branch de um mesmo diretório enquanto o outro chat edita arquivos.

Sugestão de frontend: React e TypeScript, Vite, TanStack Query para cache de dados da API e React Hook Form com Zod para formulários. Essas bibliotecas ainda não foram adicionadas; o chat do frontend deve conferir versões compatíveis antes de instalar. O backend continua sendo a autoridade de validação e autorização.

## Execução e autenticação

- Backend local: http://localhost:8080. Swagger: /swagger-ui/index.html.
- No desenvolvimento do frontend, usar proxy do Vite para /api apontando ao backend e trabalhar com URLs relativas. Isso evita configurar CORS aberto.
- Autenticação usa cookie de sessão HttpOnly, não JWT. Não guardar senha nem token de sessão em localStorage.
- GET /api/auth/csrf retorna { headerName, token } e cria/reutiliza a sessão anônima.
- POST /api/auth/login recebe formulário application/x-www-form-urlencoded com email e senha; enviar o token no cabeçalho indicado e preservar cookies. Sucesso: 204.
- Obter novo token CSRF depois do login, pois o anterior é invalidado. Mantê-lo apenas em memória. Enviar nos POST/PUT/PATCH/DELETE seguintes.
- GET /api/auth/me fornece usuário, papel e organização. GET /api/auth/permissoes fornece pares modulo/acao efetivos.
- POST /api/auth/logout exige CSRF atual e retorna 204. Limpar cache de dados privados do frontend após logout ou 401.
- Cookies de produção usam Secure e requerem HTTPS; o perfil local permite HTTP.

## Permissões de interface

Módulo PRODUTOS; ações CONSULTAR, CADASTRAR, EDITAR e INATIVAR. Cada endpoint de produtos verifica a ação no backend. Funcionário pode receber ações individuais; as ações de escrita exigem também CONSULTAR no cadastro de permissões.

Admin possui todas as ações. A manutenção de categorias e a gestão de funcionários continuam exclusivas de ADMIN, mesmo quando um funcionário tem CADASTRAR ou EDITAR em Produtos. Esconder ou desabilitar botões conforme os acessos, sem usar isso como substituto da segurança do servidor.

## Telas iniciais

1. Login e tratamento de sessão expirada.
2. Navegação modular, com menu Produtos conforme CONSULTAR.
3. Categorias: listagem para autorizados; manutenção somente para ADMIN.
4. Produtos: listagem paginada com busca, filtro de categoria e situação, e botões conforme ação.
5. Modal amplo de cadastro/edição com abas Geral, Operacional e Fiscal; seleção de mercadinhos será futura.

## Endpoints de produtos

| Método | URL | Ação |
| --- | --- | --- |
| GET | /api/produtos/opcoes | CONSULTAR; unidades e limites de imagem |
| GET | /api/produtos?pagina=0&tamanho=20&busca=&categoriaId=&ativo=true | CONSULTAR; omitir filtros vazios |
| GET | /api/produtos/{id} | CONSULTAR |
| GET | /api/produtos/por-codigo/{codigo} | CONSULTAR; consulta exata |
| POST | /api/produtos | CADASTRAR |
| PUT | /api/produtos/{id} | EDITAR |
| PATCH | /api/produtos/{id}/situacao | INATIVAR |
| GET | /api/produtos/{id}/imagem | CONSULTAR |
| PUT | /api/produtos/{id}/imagem | EDITAR; multipart |
| DELETE | /api/produtos/{id}/imagem?versao={versao} | EDITAR |

Listagem retorna { itens, pagina, tamanho, total }. Paginação começa em zero e tamanho deve ser de 1 a 100. Busca literal de até 200 caracteres por trecho de nome ou código; não interpreta % ou _ como curingas. Sem ativo, inclui ativos e inativos. Nunca enviar organizacaoId: o contexto vem da sessão.

## Dados do formulário

POST recebe o objeto abaixo. Exemplo ilustrativo; usar uma categoria existente e ativa da organização:

```json
{
  "nome": "Café torrado 500 g",
  "codigoBarras": "00012345",
  "categoriaId": 1,
  "unidadeMedida": "UN",
  "descricao": "Descrição opcional",
  "marca": "Marca opcional",
  "permiteVendaFracionada": false,
  "controlaLote": false,
  "controlaValidade": true,
  "dadosFiscais": {
    "ncm": "09012100",
    "pisCst": "01",
    "cofinsCst": "01"
  }
}
```

Exemplos fiscais apenas demonstram formato; não determinam o enquadramento tributário do produto.

| Campo | Regra |
| --- | --- |
| nome | Obrigatório, até 200 caracteres; espaços normalizados |
| codigoBarras | Obrigatório, string de 1 a 64 dígitos; preservar zeros; não usar parseInt |
| categoriaId | Obrigatório; categoria da organização; precisa estar ativa para criar ou trocar vínculo |
| unidadeMedida | Obrigatória: UN, KG, G, L, ML, CX ou PCT; preferir opções retornadas pela API |
| descricao | Opcional, até 4000 caracteres |
| marca | Opcional, até 100 caracteres |
| permiteVendaFracionada, controlaLote, controlaValidade | Booleanos opcionais, false se omitidos ou nulos |
| dadosFiscais | Objeto opcional; não representa configuração fiscal homologada |

Código de barras é único por organização, inclusive entre produtos inativos. Nomes podem repetir. Não há validação de dígito verificador GTIN nesta entrega; o sistema permite códigos internos numéricos. Nenhuma quantidade de estoque, preço ou seleção de unidade faz parte deste objeto.

Campos fiscais opcionais: ncm (8 dígitos), cest (7), cfopPadrao (4), pisCst e cofinsCst (2), origem (0 a 8), icmsCst (2) ou icmsCsosn (3), icmsModalidadeBaseCalculo (1 ou 2 dígitos), icmsAliquota e icmsReducaoBase (percentuais de 0 a 100, até 4 casas decimais). Não enviar CST e CSOSN simultaneamente. Converter campos fiscais vazios para null ou omiti-los. Não existe valor fixo de base de ICMS no cadastro. A API valida formatos, não existência dos códigos em tabelas oficiais, aplicabilidade tributária ou cálculo de tributos.

Resposta inclui id, todos os dados públicos acima, ativo, versao, imagemUrl, criadoEm, atualizadoEm, criadoPor e atualizadoPor. Datas são instantes; apresentar no fuso do usuário. As referências de usuário são IDs, não objetos completos. Não enviar esses campos de controle no cadastro.

## Edição e inativação

PUT substitui todos os dados editáveis e recebe:

```json
{
  "versao": 0,
  "dados": {
    "nome": "Café torrado 500 g",
    "codigoBarras": "00012345",
    "categoriaId": 1,
    "unidadeMedida": "UN"
  }
}
```

Carregar os dados atuais no modal antes de editar. Omitir campos opcionais no PUT os limpa; ele não é atualização parcial. A imagem e a situação são preservadas. Uma categoria que ficou inativa pode continuar no produto existente se o ID não mudar; não oferecê-la para novas associações.

PATCH de situação recebe { "versao": 0, "ativo": false } ou true. Usar sempre a versão atual retornada pela API, inclusive depois de alterar imagem. Não há exclusão física de produtos.

## Imagens

Uma imagem opcional por produto. Fluxo: salvar JSON do produto, depois enviar a imagem usando ID e versão retornados. Se o upload falhar, o produto já existe: mostrar a pendência e permitir tentar o upload novamente, sem cadastrar outro produto.

PUT multipart/form-data contém arquivo e versao. Ao usar FormData no navegador, não definir manualmente Content-Type, para preservar o boundary. Enviar CSRF no cabeçalho e cookies da sessão.

Aceita conteúdo PNG/JPEG, até 5 MiB; máximo de 4 milhões de pixels e 4096 por lado. O nome enviado pelo cliente não é usado no armazenamento. O servidor regrava em PNG sem metadados e limita também o resultado processado a 5 MiB. SVG e outros formatos não são aceitos.

A imagemUrl é relativa e protegida. Usar com o mesmo proxy/origem e os cookies de sessão. Não copiar essa URL como imagem pública nem tentar preencher URL externa no JSON. A resposta de imagem usa no-store. Para remover, enviar DELETE com a versão atual; o produto permanece.

## Erros e conflitos

- 400: dados inválidos; ProblemDetail pode incluir campos, uma lista de nomes de campos. Preservar o formulário e destacar os erros; não exibir mensagens internas inventadas.
- 401: sessão ausente, expirada ou revogada; limpar cache privado e voltar ao login.
- 403: sem ação permitida ou CSRF inválido. Conferir permissões e o token; não repetir alterações automaticamente.
- 404: recurso inexistente ou de outra organização; não distinguir esses casos.
- 409: código duplicado, categoria inativa ou versão desatualizada. Recarregar estado e pedir revisão, sem sobrescrever automaticamente.
- 413: imagem ou requisição excedeu o limite.
- 429: tentativas de login excedidas; respeitar Retry-After.
- 503: armazenamento temporariamente indisponível; o usuário pode tentar novamente.

## Limites e próximos trabalhos

Não há estoque, compras, preços por mercadinho, planograma, pagamentos, emissão fiscal ou integração do tablet. A imagem usa armazenamento local protegido por API; produção precisa de volume persistente, backups e estratégia de múltiplas instâncias. Recuperação de senha, convites e troca obrigatória da senha inicial continuam pendentes antes de uso com clientes reais.

No novo chat, pedir para ler AGENTS.md, README.md, este guia e o OpenAPI local, implementar somente frontend/ e não alterar o contrato do backend sem coordenação. Começar por login, navegação, listagem e modal de produtos.

## Classificação indicativa para menores

Categorias aceitam permitidoParaMenores em POST/PUT (booleano). O padrão no cadastro é true; omitir ou enviar null no PUT preserva o valor atual. A checkbox representa o inverso: "Proibida para menores de 18 anos". Desmarcada envia permitidoParaMenores=true, marcada envia false. A edição recupera a classificação com a mesma inversão.

As respostas de categorias e produtos incluem permitidoParaMenores. Produtos recebem uma cópia da classificação da categoria ao cadastrar ou trocar categoria. O campo não é aceito como entrada no JSON de produtos. Mudanças posteriores da categoria não atualizam cópias existentes, inclusive ao salvar o produto sem trocar categoria.

A V6 adiciona as colunas permitido_para_menores em categorias e produtos com padrão false e restrições de não nulo e 0/1. A V7 altera apenas os defaults para true, sem modificar classificações já salvas. Reiniciar o backend atualizado aplica as migrations. O cadastro e a edição de categorias continuam exclusivos de ADMIN; apenas a classificação fica registrada, sem verificação etária ou bloqueio de venda nesta etapa.
