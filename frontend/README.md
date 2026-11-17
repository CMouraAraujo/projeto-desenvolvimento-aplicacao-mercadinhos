# Frontend · Mercadinhos

Painel web em React e TypeScript, com Vite, TanStack Query, React Hook Form e Zod.

## Executar

Requer Node.js 22.12 ou superior na linha 22, ou Node.js 24+, e o backend local em http://localhost:8080. As versões estão fixadas no package.json e no package-lock.json; use npm ci para reproduzir a instalação.

Na pasta frontend:

```powershell
npm ci
npm run dev
```

Abra http://127.0.0.1:5173. O Vite encaminha /api para o backend. Use uma conta já provisionada; não há credenciais padrão nem cadastro público. O proxy existe apenas no servidor de desenvolvimento: em produção, sirva o build e /api pela mesma origem, com HTTPS e proxy reverso.

## Disponível

- Login por formulário, sessão HttpOnly, renovação de CSRF após login e logout.
- Tratamento de 401 com remoção do cache privado e retorno ao login.
- Navegação e ações conforme as permissões efetivas.
- Produtos: paginação no servidor, busca por nome/código, filtros por categoria/situação, consulta, cadastro, edição e ativação/inativação.
- Modal de produtos com abas Geral, Operacional e Fiscal, validação e confirmação de descarte.
- Upload/substituição/remoção da imagem com controle de versão. O upload exige EDITAR, inclusive após um cadastro feito com CADASTRAR.
- Falha no upload mantém o produto salvo e permite tentar apenas o envio novamente.
- Categorias: consulta para PRODUTOS/CONSULTAR; cadastro, renomeação e situação exclusivos de ADMIN.
- Layout responsivo, diálogo modal nativo com contenção de foco, rótulos e erros associados aos campos.

Código de barras e códigos fiscais são strings. PUT de produto envia todos os campos editáveis; campos opcionais vazios viram null. Conflitos não são repetidos nem sobrescritos automaticamente: o preenchimento é mantido, e o usuário pode recarregar os dados atuais para revisar.

## Verificar

```powershell
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Com o navegador instalado, npm run verify executa todos os passos de validação. Em Linux/CI, use npx playwright install --with-deps chromium.

Os testes unitários cobrem serialização, validações, permissões visuais e cliente HTTP/CSRF. Os testes Playwright usam respostas controladas da API e cobrem login/logout, sessão expirada, permissões, conflito, upload parcial, descarte e tela móvel. Não precisam de MySQL, não criam usuários e não alteram dados reais. Não substituem os testes de autorização e isolamento do backend ou a validação final com conta provisionada.

npm run preview serve o build local, mas não tem proxy /api: use npm run dev para integrar ao backend local.

## Estrutura

- src/auth/: sessão e login.
- src/catalogo/: integração, listagens e formulários de produtos/categorias.
- src/shared/: cliente HTTP, tipos e componentes compartilhados.
- tests/: fluxos de navegador.
- vite.config.ts: desenvolvimento, proxy e testes unitários.
- playwright.config.ts: testes no Chromium.

Dependências diretas de aplicação sob MIT; TypeScript e Playwright sob Apache-2.0. A escolha e os peer dependencies foram conferidos no registro npm antes da instalação. Não há fontes remotas, CDN ou armazenamento de credenciais no navegador.

## Limites

A gestão de funcionários e permissões tem API, mas ainda não tem tela. Recuperação de senha, convites e troca obrigatória de senha inicial dependem de evolução do backend. Não há preços, estoque, mercadinhos, emissão fiscal ou autoatendimento nesta entrega. Dados fiscais opcionais não constituem homologação tributária.

A validação autenticada contra o backend real deve ser feita com conta provisionada e categorias da organização. Os testes do frontend verificam comportamento da interface; autorização e isolamento entre organizações continuam sendo responsabilidade e cobertura do backend.
