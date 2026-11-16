# Sistema de gerenciamento de minimercados

Aplicação modular para redes de minimercados de autoatendimento. Cada empresa cliente terá dados e usuários próprios. O primeiro módulo de negócio será o catálogo de produtos.

[Documentação funcional e arquitetura na Wiki](https://github.com/CMouraAraujo/projeto-desenvolvimento-aplicacao-mercadinhos/wiki)

## Estado atual

Backend com Spring Boot, MySQL, Flyway e validação do schema. Já oferece provisionamento local de organização e administrador, login por sessão, consulta da conta e da organização atual e logout.

O contexto da organização vem da sessão autenticada e é revalidado no banco. Ainda não há gestão de funcionários, permissões por módulo, recuperação de senha, API de produtos ou interface web. Esta é a primeira entrega de identidade; os limites de produção estão descritos abaixo.

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

1. Gestão de funcionários, permissões por módulo e recuperação segura de acesso.
2. Categorias isoladas por organização.
3. API e modal de produtos, com código de barras obrigatório e único por organização.
4. Vínculo com mercadinhos e demais módulos conforme a Wiki.

## Convenções de desenvolvimento

Branches seguem feature/, bugfix/, hotfix/, chore/, refactor/ ou docs/. A branch atual de trabalho trata autenticação e organizações.

Bibliotecas são escolhidas conforme o problema: Spring Security, Bean Validation, Spring Data JPA, Flyway, Lombok, Caffeine, JUnit, AssertJ e Testcontainers já compõem a base. Lombok usa processamento de anotações explícito no Maven, com getters e construtor protegido na entidade; setters não são liberados indiscriminadamente.

O CI executa os testes, e o Dependabot está configurado para propor atualizações Maven e GitHub Actions. Os testes atuais também verificam CSRF, cabeçalhos de segurança e bloqueio de usuários autenticados enquanto as funcionalidades de negócio ainda não foram liberadas.

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
- Ainda não há recuperação de senha, convites por e-mail, MFA, painel de administração, permissões por módulo ou trilha completa de auditoria de acesso. Essas funcionalidades e a configuração do ambiente precisam ser concluídas antes de oferecer o serviço a clientes reais.
