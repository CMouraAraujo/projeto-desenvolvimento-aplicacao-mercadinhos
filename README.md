# Sistema de gerenciamento de minimercados

Aplicação modular para redes de minimercados de autoatendimento. Cada empresa cliente terá dados e usuários próprios. O primeiro módulo de negócio será o catálogo de produtos.

[Documentação funcional e arquitetura na Wiki](https://github.com/CMouraAraujo/projeto-desenvolvimento-aplicacao-mercadinhos/wiki)

## Estado atual

Backend com Spring Boot, MySQL, Flyway e validação do schema. Já oferece provisionamento local de organização e administrador, login por sessão, consulta da conta e da organização atual e logout.

O contexto da organização vem da sessão autenticada e é revalidado no banco. Já há gestão de funcionários e permissões por módulo/ação, restritas à organização do administrador. Ainda não há recuperação de senha, API de produtos ou interface web. Esta é a primeira entrega de identidade; os limites de produção estão descritos abaixo.

## Tecnologias

- Java 21 como versão alvo do código; validado localmente também com JDK 22.
- Spring Boot 4.1.2, Spring Security e Spring Data JPA.
- MySQL 8.4 e Flyway.
- Maven Wrapper: não é necessário instalar Maven globalmente.
- Testcontainers com MySQL real nos testes de integração.

## Estrutura

```text
backend/                  API e testes
  src/main/java/br/com/mercadinhos/
    identidade/           Organizações e segurança
    catalogo/             Limite reservado ao futuro catálogo
  src/main/resources/db/migration/
frontend/                 Reservado; interface ainda não implementada
scripts/backend.ps1       Execução e verificação no Windows
compose.yaml              MySQL para desenvolvimento local
```

Os arquivos em docs/ são o rascunho inicial anterior à publicação na Wiki. A Wiki é a referência de documentação funcional; as instruções de execução são mantidas neste README.

## Requisitos locais

JDK 21 ou superior compatível com Spring Boot, Docker Desktop com engine Linux em execução e PowerShell 7 no Windows. Internet é necessária no primeiro build para baixar Maven, dependências e imagens Docker. A integração contínua usa JDK 21.

## Executar no Windows

Na raiz do repositório, prepare a configuração local apenas se ainda não existir:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Edite .env e substitua DB_PASSWORD e MYSQL_ROOT_PASSWORD por senhas locais diferentes. Prefira senhas alfanuméricas para evitar regras distintas de escape entre Compose e arquivos de propriedades. O arquivo .env não deve ser versionado. Na preparação inicial deste ambiente, ele já foi criado com senhas aleatórias.

Inicie o banco:

```powershell
docker compose up -d --wait mysql
```

Inicie o backend em primeiro plano:

```powershell
.\scripts\backend.ps1 run
```

O script localiza o JDK disponível se JAVA_HOME estiver ausente ou inválido e utiliza o Maven Wrapper. Não altera permanentemente as variáveis do Windows. Encerre a aplicação com Ctrl+C.

Confira a saúde:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Resultado esperado: status UP. Não existe interface de login nem usuário padrão. As rotas autenticadas exigem uma conta criada pelo provisionamento e as demais funcionalidades seguem bloqueadas.

O MySQL é publicado apenas em 127.0.0.1:3307. O banco não contém dados iniciais de clientes. As migrações são aplicadas automaticamente ao iniciar o backend; Hibernate valida o schema e não cria tabelas por conta própria.

## Testes e build

Com Docker Desktop em execução:

```powershell
.\scripts\backend.ps1 verify
```

Os testes criam um MySQL descartável em uma porta dinâmica e não usam o volume de desenvolvimento. Cobrem migrações, Unicode, hash de senha, login/logout, CSRF, sessão, flags do cookie de produção, contas inativas, contexto da organização, revogação de acesso, concorrência e limite de tentativas.

O comando verify também empacota a aplicação em backend/target/. A rotina do GitHub Actions executa a mesma verificação com Java 21.

Em outros sistemas, dentro de backend/ e com JAVA_HOME configurado:

```sh
sh mvnw -B -ntp verify
sh mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

## Configuração

| Variável | Padrão | Finalidade |
| --- | --- | --- |
| DB_HOST | localhost | Host do banco |
| DB_PORT | 3307 | Porta publicada pelo Compose |
| DB_NAME | mercadinhos | Nome do banco |
| DB_USERNAME | mercadinhos | Usuário da aplicação |
| DB_PASSWORD | Sem padrão | Senha obrigatória da aplicação |
| MYSQL_ROOT_PASSWORD | Sem padrão | Senha administrativa usada somente pelo Compose |
| SERVER_PORT | 8080 | Porta HTTP |

A leitura automática do .env ocorre apenas no perfil local. Fora dele, forneça as variáveis de ambiente explicitamente. Este Compose é para desenvolvimento; produção exige configuração própria, HTTPS e gerenciamento de segredos.

O volume MySQL persiste entre reinícios. Alterar senhas no .env depois de inicializar o volume não altera os usuários já existentes no banco.

Para parar o banco sem apagar os dados:

```powershell
docker compose stop mysql
```

## Próximas entregas

1. Recuperação segura de acesso e troca da senha inicial, antes do uso em produção.
2. Categorias isoladas por organização.
3. API e modal de produtos, com código de barras obrigatório e único por organização.
4. Vínculo com mercadinhos e demais módulos conforme a Wiki.

## Convenções de desenvolvimento

Branches seguem feature/, bugfix/, hotfix/, chore/, refactor/ ou docs/. A branch atual de trabalho trata autenticação e organizações.

Bibliotecas são escolhidas conforme o problema: Spring Security, Bean Validation, Spring Data JPA, Flyway, Lombok, Caffeine, JUnit, AssertJ e Testcontainers já compõem a base. Lombok usa processamento de anotações explícito no Maven, com getters e construtor protegido na entidade; setters não são liberados indiscriminadamente.

O CI executa os testes, e o Dependabot está configurado para propor atualizações Maven e GitHub Actions. Os testes verificam CSRF, cabeçalhos de segurança, isolamento de usuários por organização, autorização por ação, revogação de sessão e concorrência. As rotas de catálogo ainda não foram liberadas.

As convenções para futuras alterações estão em [AGENTS.md](AGENTS.md). Testes de isolamento entre organizações e de permissões serão obrigatórios nas entregas das APIs correspondentes; a autenticação de sessão já está implementada; as permissões dos módulos serão acrescentadas com suas respectivas APIs.

## Criar organização e administrador

Após gerar o JAR com verify e iniciar o MySQL, execute em um terminal interativo:

```powershell
.\scripts\backend.ps1 provision
```

O comando pergunta o nome da organização, o nome do administrador, e-mail e senha sem exibir a senha digitada. Cria os três registros em uma transação e encerra sem abrir um servidor HTTP. Repetir um e-mail falha sem criar organização órfã. Não envie senhas como argumentos nem as registre em arquivos.

A senha deve ter de 12 a 72 caracteres e no máximo 72 bytes em UTF-8 (limite do BCrypt); e-mails são ASCII e normalizados para minúsculas. Nesta entrega, cada e-mail pertence a uma organização. As senhas são armazenadas com BCrypt, custo 12 e prefixo de algoritmo para permitir evolução.

Em outro sistema, após o build, na raiz do projeto:

```sh
java -jar backend/target/mercadinhos-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=local,provisionamento
```

Sem console interativo o comando falha, em vez de aceitar senha exposta. Em produção, use apenas o perfil provisionamento com as variáveis de ambiente do banco, sem o perfil local. O operador precisa de acesso ao host e ao banco; não existe uma rota pública para provisionar clientes.

## API de autenticação

| Método | Rota | Comportamento |
| --- | --- | --- |
| GET | /api/auth/csrf | Público; cria token CSRF e sessão anônima |
| POST | /api/auth/login | Formulário email e senha; 204 no sucesso, 401 nas credenciais inválidas |
| GET | /api/auth/me | Dados públicos da conta autenticada e seu papel |
| GET | /api/organizacao | ID e nome da organização da sessão |
| POST | /api/auth/logout | Exige CSRF; encerra a sessão e remove o cookie |

Login recebe application/x-www-form-urlencoded, usando o filtro de autenticação do Spring Security. Não recebe JSON nesta versão. Guarde os cookies entre as chamadas e envie o token recebido no cabeçalho indicado pelo endpoint CSRF. Depois do login, obtenha um novo token: o token da sessão anônima é invalidado.

Exemplo no PowerShell 7 com o backend local em execução:

```powershell
$api = 'http://localhost:8080'
$csrf = Invoke-RestMethod "$api/api/auth/csrf" -SessionVariable navegador
$credencial = Get-Credential -Message 'Informe o e-mail e a senha do administrador'
try {
    Invoke-WebRequest "$api/api/auth/login" -Method Post -WebSession $navegador `
        -Headers @{ $csrf.headerName = $csrf.token } `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{ email = $credencial.UserName; senha = $credencial.GetNetworkCredential().Password }
} finally {
    Remove-Variable credencial
}
Invoke-RestMethod "$api/api/auth/me" -WebSession $navegador
Invoke-RestMethod "$api/api/organizacao" -WebSession $navegador
$csrf = Invoke-RestMethod "$api/api/auth/csrf" -WebSession $navegador
Invoke-WebRequest "$api/api/auth/logout" -Method Post -WebSession $navegador `
    -Headers @{ $csrf.headerName = $csrf.token }
```

## Segurança de sessão e limites desta entrega

- Cookie HttpOnly, SameSite=Lax e Secure por padrão. Somente o perfil local desativa Secure para localhost em HTTP. Produção requer HTTPS.
- Expiração por 30 minutos de inatividade, ID de sessão rotacionado no login e sessão invalidada no logout.
- O backend consulta a conta e o vínculo em cada requisição autenticada. Desativação de usuário, organização ou vínculo, mudança de papel ou versão da conta revogam a sessão na requisição seguinte. Alterações de senha futuras devem incrementar a versão da conta.
- Não há seleção de organização pelo navegador: parâmetro ou cabeçalho fornecido pelo cliente não muda o contexto autenticado.
- Caffeine limita tentativas de login a 30 por IP em uma janela de 15 minutos, com cache limitado a 10 mil entradas. Ao exceder, responde 429 com Retry-After. Contam tentativas válidas e inválidas que passaram pelo CSRF.
- Sessões e limitador são locais a uma instância e se perdem ao reiniciar. Implantação em múltiplas instâncias exige solução compartilhada e proteção na borda. O IP considerado é o da conexão direta; cabeçalhos de proxy não são confiados automaticamente.
- Ainda não há recuperação de senha, convites por e-mail, MFA, painel visual de administração ou trilha completa de auditoria de acesso. Essas funcionalidades e a configuração do ambiente precisam ser concluídas antes de oferecer o serviço a clientes reais.

## Swagger / OpenAPI no ambiente local

Inicie o backend com o perfil local e abra:

- Interface: http://localhost:8080/swagger-ui/index.html
- Especificação JSON: http://localhost:8080/v3/api-docs

No Windows, se a política de execução bloquear o script PowerShell, use o Maven Wrapper diretamente na raiz do projeto:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-22'
.\backend\mvnw.cmd -f .\backend\pom.xml spring-boot:run "-Dspring-boot.run.profiles=local"
```

Ajuste JAVA_HOME ao JDK instalado na sua máquina. Para executar os testes sem o script:

```powershell
.\backend\mvnw.cmd -f .\backend\pom.xml -B -ntp verify
```

Se já houver uma instância rodando na porta 8080, encerre-a com Ctrl+C no terminal correspondente e reinicie para carregar esta alteração. O Swagger é servido pelo próprio backend; não usa outra porta nem um contêiner separado.

### Testar pelo navegador

1. Crie seu administrador pelo provisionamento, caso ainda não exista.
2. No Swagger, abra GET /api/auth/csrf, clique em Try it out e Execute.
3. Copie somente o valor de token da resposta. Abra Authorize e cole em csrf.
4. Execute POST /api/auth/login com os campos email e senha. O retorno de sucesso é 204; o navegador recebe e guarda o cookie de sessão automaticamente.
5. Execute GET /api/auth/me e GET /api/organizacao para conferir o acesso.
6. Execute novamente GET /api/auth/csrf depois do login. Em Authorize, remova o valor anterior de csrf e informe o novo token.
7. Execute POST /api/auth/logout. A sessão termina com 204. Depois, GET /api/auth/me deve retornar 401.

O campo sessao em Authorize documenta o cookie JSESSIONID; não precisa ser preenchido e não substitui o login. Cookies HttpOnly são gerenciados pelo navegador. Login e logout continuam exigindo CSRF: a documentação não desativa a proteção.

Autorizações do Swagger não são persistidas entre recargas. O token deve ser atualizado após login e logout. Se a sessão expirar, obtenha novo token e entre novamente. Não use credenciais reais de produção nesta interface local.

### Restrição de acesso à documentação

Springdoc 3.2.0 fornece os modelos OpenAPI e o Swagger UI. A documentação está desativada por padrão e habilitada no application-local.properties. Além disso, Spring Security só libera os GETs das rotas da documentação quando o perfil local está ativo.

Fora do perfil local, as rotas continuam bloqueadas inclusive para administradores. Ativar propriedades do Springdoc isoladamente não libera o acesso pela segurança. Não ative o perfil local em produção.

Os testes verificam o contrato documentado, o formulário de login, os endpoints tratados por filtros, os recursos da interface e a restrição por ambiente. Login, logout e provisão não são duplicados em controladores fictícios apenas para aparecer no Swagger.

## Funcionários e permissões

Somente administradores gerenciam usuários. A organização é inferida da sessão; nunca é escolhida no JSON, em query strings ou cabeçalhos. O cadastro via API sempre cria FUNCIONARIO, sem promoção para ADMIN. Campos não suportados, como papel, id ou organizacaoId, são rejeitados com 400.

| Método | Rota | Acesso e finalidade |
| --- | --- | --- |
| GET | /api/usuarios?pagina=0&tamanho=20 | Admin: lista usuários da organização, inclusive administradores |
| GET | /api/usuarios/{id} | Admin: consulta usuário da própria organização |
| POST | /api/usuarios | Admin + CSRF: cria funcionário |
| PUT | /api/usuarios/{id}/permissoes | Admin + CSRF: substitui integralmente as permissões do funcionário |
| PATCH | /api/usuarios/{id}/situacao | Admin + CSRF: ativa ou desativa funcionário |
| GET | /api/modulos | Admin: lista módulos e ações disponíveis |
| GET | /api/auth/permissoes | Autenticado: consulta as próprias permissões efetivas |

Módulo inicial: PRODUTOS. Ações: CONSULTAR, CADASTRAR, EDITAR e INATIVAR. Qualquer ação além de CONSULTAR exige também CONSULTAR no mesmo módulo. Lista vazia significa nenhum acesso. Administradores têm acesso completo aos módulos conhecidos, sem precisar gravar permissões individuais. Isso não libera gestão de usuários aos funcionários.

### Exemplo no Swagger

Entre como administrador e atualize o token CSRF em Authorize, conforme o fluxo de autenticação acima. Em POST /api/usuarios, envie:

```json
{
  "nome": "Funcionário de cadastro",
  "email": "funcionario@example.com",
  "senha": "DefinaUmaSenhaInicialForte",
  "permissoes": [
    { "modulo": "PRODUTOS", "acao": "CONSULTAR" },
    { "modulo": "PRODUTOS", "acao": "CADASTRAR" }
  ]
}
```

O exemplo de senha é apenas ilustrativo. Defina uma senha exclusiva e entregue-a por canal seguro. Convites e troca obrigatória de senha inicial ainda serão implementados; não ofereça este fluxo a clientes reais antes dessas etapas.

O retorno 201 contém o ID, a versão, os dados públicos e as permissões; nunca contém senha ou hash. O e-mail é globalmente único nesta versão, pois uma conta pertence a uma organização. Conflitos retornam 409 sem expor detalhes de outra empresa.

Para substituir permissões, consulte primeiro GET /api/usuarios/{id} e use a versão retornada:

```json
{
  "versao": 0,
  "permissoes": [
    { "modulo": "PRODUTOS", "acao": "CONSULTAR" }
  ]
}
```

Para desativar, envie ao PATCH /api/usuarios/{id}/situacao:

```json
{ "versao": 0, "ativo": false }
```

Substitua 0 pela versão atual em cada operação. Uma alteração incrementa a versão do vínculo, revogando sessões anteriores na próxima requisição. O funcionário precisa entrar novamente para usar as novas permissões. Uma versão desatualizada retorna 409; releia o cadastro antes de tentar novamente. Operações simultâneas usam bloqueio de linha e comparação da versão para impedir sobrescritas silenciosas.

A paginação aceita pagina >= 0 e tamanho de 1 a 100. Usuários de outra organização retornam o mesmo 404 de um ID inexistente. Funcionários recebem 403 ao tentar gerenciar usuários, mesmo que tentem alterar a própria conta. A API não permite desativar ou editar permissões de administradores, protegendo o acesso administrativo da organização.

### Integração dos próximos módulos

O contrato público identidade.AcessoAtual permite exigir uma ação e obter a organização validada. Cada operação de negócio deverá chamar exigir(Modulo.PRODUTOS, Acao correspondente) e restringir suas consultas pelo organizacaoId() retornado. Os testes já exercitam esse contrato; a API de produtos e a interface visual ainda serão construídas.

A migração V3 cria as permissões vinculadas ao usuário da organização e adiciona o versionamento dos vínculos. Migrações anteriores foram preservadas. Reinicie o backend para aplicar a migração automaticamente; não é necessário executar SQL manualmente.
