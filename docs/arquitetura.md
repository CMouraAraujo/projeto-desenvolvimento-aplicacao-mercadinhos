# Arquitetura e segurança

## Direção

Uma aplicação backend organizada em módulos de negócio, com API para o painel web e, futuramente, para o autoatendimento. O modelo inicial é um monólito modular: implantação conjunta e limites explícitos entre os módulos.

Java com Spring Boot e MySQL são a base técnica acordada. A organização modular deve existir no código, independentemente do uso de uma biblioteca específica.

## Componentes propostos

| Componente | Proposta | Finalidade |
| --- | --- | --- |
| Backend | Java e Spring Boot | API e regras de negócio |
| Segurança | Spring Security | Autenticação e autorização |
| Persistência | Spring Data JPA e MySQL/InnoDB | Persistência e transações |
| Migrações | Flyway | Evolução versionada do schema |
| Frontend | React e TypeScript | Painel web e modal de cadastro |
| Imagens | Armazenamento de arquivos/objetos | Banco guarda a referência do arquivo |
| Limites de módulos | Avaliar Spring Modulith | Verificação das dependências entre módulos |

Versões compatíveis e mantidas serão fixadas no início da implementação. Não há decisão tomada sobre provedor de hospedagem ou serviço de armazenamento.

## Limites iniciais

- Identidade e acesso: organizações, usuários, vínculos, convites e permissões.
- Catálogo: produtos, categorias e referência às imagens dos produtos.
- Mercadinhos: unidades e disponibilidade dos produtos, em etapa posterior.
- Estoque, compras e autoatendimento: módulos futuros.

Um módulo deve acessar outro por uma interface explícita, evitando depender diretamente de seus repositórios e entidades internas. As pastas de código devem refletir os módulos de negócio.

## Isolamento entre organizações

Proposta: banco e schema compartilhados com organizacao_id nas entidades de cada cliente.

- Resolver a organização a partir de um vínculo autenticado e autorizado.
- Não confiar em organizacao_id enviado pelo navegador como prova de acesso.
- Filtrar consultas, alterações, exclusões e listagens pelo contexto da organização.
- Aplicar o mesmo isolamento a arquivos e a futuros processos assíncronos.
- Garantir que referências entre entidades não cruzem organizações, com validação no backend e restrições no banco onde aplicável.
- Usar restrição única de organização e código de barras para tratar concorrência.

## Administração e permissões

Administrador da plataforma e administrador de uma organização são responsabilidades distintas. Um administrador de cliente não pode criar organizações arbitrariamente ou administrar dados de outros clientes.

Proposta para entrada de clientes: operador autorizado da plataforma cria a organização e convida seu administrador inicial. O administrador da organização convida seus funcionários e libera módulos.

Acesso por módulo é requisito confirmado. Permissões adicionais por ação, como consultar, cadastrar, editar e inativar, são uma proposta a validar. Conceder uma permissão não pode permitir que um administrador ultrapasse seu próprio escopo.

## Autenticação

A estratégia de sessão ainda não foi escolhida; JWT não é um requisito. Antes da implementação, definir login, expiração, revogação, convites e recuperação de acesso.

Senhas devem ser armazenadas com hash adequado; credenciais e tokens não devem aparecer em logs nem no repositório. Produção deve utilizar HTTPS. Se forem usadas sessões com cookies, considerar os controles correspondentes de CSRF e atributos de segurança dos cookies.

## Imagens

Receber arquivos com validação de tipo real e tamanho, gerar nomes internos e controlar autorização. Guardar a referência no banco; definir tratamento de arquivos órfãos e substituições.

A primeira versão não fará download automático a partir de URLs arbitrárias. O usuário poderá obter a imagem externamente e enviá-la pelo cadastro.

## Evolução fiscal

Separar classificação do produto das regras tributárias dependentes da organização e da operação. A configuração inicial não constitui um motor fiscal. Não fixar o modelo de forma a impedir novas classificações e tributos.

## Referências

- [Spring Modulith](https://spring.io/projects/spring-modulith/)
- [MySQL: InnoDB](https://dev.mysql.com/doc/refman/8.4/en/innodb-introduction.html)
- [MySQL: restrições de chave primária e unicidade](https://dev.mysql.com/doc/refman/8.4/en/constraint-primary-key.html)
- [Leiaute NF-e/NFC-e](https://moc.sped.fazenda.pr.gov.br/NFe_NFCe/NFe_NFCe/Leiaute.html)

Referências orientam a implementação; versões e regras fiscais aplicáveis devem ser conferidas quando essas funcionalidades forem construídas.
