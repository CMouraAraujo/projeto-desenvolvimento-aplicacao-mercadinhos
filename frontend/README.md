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
- Produtos: paginação no servidor, busca por nome/código, filtros por categoria/situação/presença de imagem, consulta, cadastro, edição e ativação/inativação.
- Painéis de Produtos e Categorias com listagem compacta e seleção de 10, 25 ou 50 itens por página (padrão 10). Trocar a quantidade retorna à primeira página e mantém os filtros; Anterior/Próxima consultam a API. Se o total diminuir, a página é ajustada para continuar no intervalo disponível.
- No formulário de produto, nome, categoria, código de barras, unidade de medida, NCM, CFOP e situação tributária de ICMS são obrigatórios, indicados por asterisco vermelho e atributos de acessibilidade. A edição de produtos antigos sem esses dados exige completá-los antes de salvar.
- Modal de produtos em página única com seções Geral, Operacional e Fiscal, rolagem, espaçamento compacto, validação e confirmação de descarte.
- Prévia ampliada da imagem imediatamente após a seleção, antes de salvar; cancelar a seleção restaura a imagem anterior, quando houver. Upload/substituição/remoção da imagem com controle de versão. O upload exige EDITAR, inclusive após um cadastro feito com CADASTRAR.
- Falha no upload mantém o produto salvo e permite tentar apenas o envio novamente.
- Cadastro de categoria pelo botão Nova categoria no modal de produto (ADMIN), com seleção automática e preservação do preenchimento do produto.
- Categorias: consulta para PRODUTOS/CONSULTAR; cadastro, renomeação e situação exclusivos de ADMIN.
- Layout responsivo, diálogo modal nativo com contenção de foco, rótulos e erros associados aos campos.

Código de barras e códigos fiscais são strings. No formulário, NCM é exibido como XXXX.XX.XX e CEST como XX.XXX.XX; a API recebe somente os dígitos, preservando zeros à esquerda. A situação tributária de ICMS usa seleção por código com descrição curta: 101, 102, 103, 201, 202, 203, 300, 400, 500 e 60. O código 60 preenche icmsCst e os de três dígitos preenchem icmsCsosn, limpando o outro campo. Uma situação anterior fora da lista é preservada na edição até a escolha de outra opção. PUT de produto envia todos os campos editáveis; campos opcionais vazios viram null. Conflitos não são repetidos nem sobrescritos automaticamente: o preenchimento é mantido, e o usuário pode recarregar os dados atuais para revisar.

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

A gestão de funcionários e permissões tem API, mas ainda não tem tela. Recuperação de senha, convites e troca obrigatória de senha inicial dependem de evolução do backend. Há custo, markup e sugestão de preço no catálogo; estoque, mercadinhos, preços por unidade, emissão fiscal e autoatendimento continuam futuros. O preenchimento fiscal não constitui homologação tributária. A obrigatoriedade de NCM, CFOP e situação de ICMS está aplicada no frontend; o contrato atual do backend ainda aceita dados fiscais opcionais. Torná-los obrigatórios também na API exige coordenação com o backend.

A validação autenticada contra o backend real deve ser feita com conta provisionada e categorias da organização. Os testes do frontend verificam comportamento da interface; autorização e isolamento entre organizações continuam sendo responsabilidade e cobertura do backend.

## Classificação indicativa para menores de 18 anos

O modal de categoria, inclusive quando aberto dentro do produto, contém a checkbox "Proibida para menores de 18 anos". Ela começa desmarcada: categoria livre (permitidoParaMenores=true). Marcada: categoria restrita (permitidoParaMenores=false). A gestão permanece exclusiva de administradores.

A API persiste permitidoParaMenores na categoria e copia o valor para o produto ao cadastrá-lo ou trocar de categoria. Alterar a classificação da categoria ou editar um produto mantendo a categoria não reclassifica produtos existentes. Essa cópia permite evolução futura; ainda não existe verificação de idade ou bloqueio de venda.

A migration V6 do backend cria permitido_para_menores BOOLEAN NOT NULL DEFAULT FALSE nas tabelas categorias e produtos. A V7 muda o padrão de novos registros para TRUE, sem inverter classificações existentes. Reinicie o backend atualizado para aplicar as migrations antes de usar a checkbox. Registros anteriores conservam os valores já salvos.

## Identidade visual

A paleta institucional é definida por variáveis CSS em src/styles.css: laranja #FF6400, grafite #232323 e branco #FFFFFF. Os tons de apoio usam cinzas neutros e variações de laranja para bordas, fundos, hover e foco. Navegação e painel de apresentação do login usam grafite; o conteúdo usa superfícies claras.

Botões primários combinam fundo laranja com texto grafite para legibilidade. Descrições e textos pequenos usam cinzas de maior contraste. Erros e asteriscos obrigatórios mantêm vermelho semântico. O layout compacto dos formulários, a rolagem interna e a navegação por teclado permanecem disponíveis.

O filtro de imagem oferece "Imagem: todas", "Com imagem" e "Sem imagem". Ele envia comImagem=true/false à API (omitir inclui ambos), combina com busca/categoria/situação e retorna à primeira página ao mudar. O total e a paginação são calculados no servidor para o conjunto filtrado.

A listagem exibe a miniatura cadastrada usando imagemUrl como retornada pela API, inclusive seu parâmetro de versão, sem concatenar outra interrogação. Se não houver imagem ou o carregamento falhar, aparece um ícone com identificação de ausência/indisponibilidade. A prévia de imagem já cadastrada no modal segue a mesma URL protegida. O filtro considera a referência salva no banco; ele não audita a disponibilidade física dos arquivos.

Atualize e reinicie o backend antes de usar o filtro comImagem na interface.

## Precificação no cadastro

“Valor de compra (R$)” e “Acréscimo / markup (%)” são obrigatórios, com asterisco vermelho. Aceitam vírgula ou ponto e até duas casas decimais; custo positivo até 999999999,99 e percentual de 0 a 10000. O preço de venda padrão é calculado em centavos com arredondamento HALF_UP (10 + 50% = 15), sem exigir preenchimento manual. Desmarcar “Definir preço de venda padrão” envia precoVendaPadrao=null. Recalcular os valores durante edição mantém a proteção por versão.

Reinicie a API atualizada para aplicar V8. Produtos antigos abrem com compra/percentual vazios e exigem preenchimento ao salvar; os valores não são presumidos como zero. O preço padrão fica preparado para uso futuro nos mercadinhos; a configuração por unidade ainda não está implementada.
