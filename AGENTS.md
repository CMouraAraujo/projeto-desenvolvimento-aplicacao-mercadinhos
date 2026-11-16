# Convenções do projeto

## Git

- Usar branches com prefixos feature/, bugfix/, hotfix/, chore/, refactor/ ou docs/, conforme a alteração.
- Não usar o prefixo codex/.
- Preservar alterações do usuário. Não fazer commit ou push sem solicitação.

## Bibliotecas e arquitetura

- Preferir bibliotecas maduras e mantidas quando reduzirem código repetitivo ou risco técnico. A escolha não se limita a Lombok.
- Antes de adicionar dependências, verificar compatibilidade, manutenção, licença e necessidade concreta. Preferir versões gerenciadas pelo Spring Boot quando disponíveis.
- Bases existentes: Spring Security para segurança; Jakarta Bean Validation para entradas; Spring Data JPA para persistência; Flyway para migrations; JUnit, AssertJ e Testcontainers para testes.
- Usar Lombok seletivamente: @Getter, @NoArgsConstructor e @RequiredArgsConstructor quando fizerem sentido. Setters somente para campos cuja alteração direta seja válida.
- Evitar @Data em entidades JPA e toString/equals/hashCode automáticos que incluam relacionamentos, dados sensíveis ou campos mutáveis sem análise.
- DTOs simples podem usar records. Avaliar MapStruct quando o mapeamento entre DTOs e domínio justificar o processador adicional; não criar infraestrutura sem uso.
- Não expor entidades JPA diretamente na API. Organizar código por módulo de negócio, com limites explícitos.

## Testes e segurança

- Implementar testes de regras de negócio e cenários de falha; usar integração com MySQL real para comportamento de banco e migrations.
- Testar autorização e isolamento entre organizações antes de expor funcionalidades de negócio. Cobrir tentativas de acessar IDs de outra organização e referências cruzadas.
- Testar duplicidade e concorrência quando afetarem integridade. Não criar testes que apenas confirmem getters/setters gerados.
- Manter execução de testes no CI. Não tratar somente a porcentagem de cobertura como prova de qualidade.
- Verificar permissões no backend; não confiar em organização, usuário ou papel enviados pelo cliente como prova de autorização.
- Negar acesso por padrão, validar entradas, usar transações e restrições no banco conforme as regras.
- Nunca versionar segredos nem registrar senhas/tokens em logs. Manter configuração local separada de produção.
- Ao implementar autenticação, cobrir armazenamento seguro de senhas, expiração/revogação, recuperação de acesso, proteção contra abuso e CSRF conforme a estratégia de sessão escolhida.
- Documentar limitações: configuração inicial e testes de perímetro não equivalem a autenticação ou isolamento multiempresa implementados.

## Verificação local

- Windows: .\scripts\backend.ps1 verify (requer Docker em execução).
- Outros ambientes: cd backend e sh mvnw -B -ntp verify.
- Atualizar README quando mudar comandos, configuração ou comportamento disponível.
