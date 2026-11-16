# Sistema de gerenciamento de minimercados

Aplicação modular para redes de minimercados de autoatendimento. Cada empresa cliente terá dados e usuários próprios. O primeiro módulo de negócio será o catálogo de produtos.

[Documentação funcional e arquitetura na Wiki](https://github.com/CMouraAraujo/projeto-desenvolvimento-aplicacao-mercadinhos/wiki)

## Estado atual

Fundação do backend implementada: Spring Boot, MySQL, migrações com Flyway, validação do schema pelo Hibernate, modelo inicial de organização e política de segurança que bloqueia todas as rotas exceto a consulta de saúde.

Ainda não há login, provisionamento de clientes, permissões por módulo, API de produtos ou interface web. A estrutura prepara essas próximas entregas. Isolamento entre organizações será implementado e testado antes de expor dados de negócio.

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

Resultado esperado: status UP. Não existe página inicial nesta etapa; outras rotas GET retornam 401. Não há usuário nem senha padrão para entrar no sistema.

O MySQL é publicado apenas em 127.0.0.1:3307. O banco não contém dados iniciais de clientes. As migrações são aplicadas automaticamente ao iniciar o backend; Hibernate valida o schema e não cria tabelas por conta própria.

## Testes e build

Com Docker Desktop em execução:

```powershell
.\scripts\backend.ps1 verify
```

Os testes criam um MySQL descartável em uma porta dinâmica e não usam o volume de desenvolvimento. Verificam migração, armazenamento de caracteres Unicode, defaults da organização, saúde pública sem detalhes internos e bloqueio das demais rotas.

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

1. Provisionamento de organização e administrador, autenticação e autorização.
2. Categorias isoladas por organização.
3. API e modal de produtos, com código de barras obrigatório e único por organização.
4. Vínculo com mercadinhos e demais módulos conforme a Wiki.
