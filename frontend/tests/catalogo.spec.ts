import { test, expect, type Page } from '@playwright/test'

const categoria = { id: 1, nome: 'Mercearia', ativa: true, permitidoParaMenores: false, versao: 0, criadoEm: '2026-11-16T12:00:00Z', atualizadoEm: '2026-11-16T12:00:00Z' }
const fiscalObrigatorio = { ncm: '09012100', cfopPadrao: '5102', icmsCst: null, icmsCsosn: '102' }
const produto = { valorCompra: 10, percentualMarkup: 50, precoVendaPadrao: 15, id: 1, nome: 'Café torrado 500 g', codigoBarras: '00012345', categoriaId: 1, unidadeMedida: 'UN', descricao: null, marca: 'Café da Serra', permiteVendaFracionada: false, controlaLote: false, controlaValidade: true, dadosFiscais: fiscalObrigatorio, ativo: true, permitidoParaMenores: false, versao: 3, imagemUrl: null, criadoEm: '2026-11-16T12:00:00Z', atualizadoEm: '2026-11-16T12:00:00Z', criadoPor: 1, atualizadoPor: 1 }
type Options = { admin?: boolean; authenticated?: boolean; permissions?: string[] }
async function mockApi(page: Page, options: Options = {}) {
  let authenticated = options.authenticated !== false
  const calls: { path: string; method: string; body: unknown; csrf: string | undefined; contentType: string | undefined }[] = []
  let csrf = 'anonimo'
  await page.route('**/api/**', async route => {
    const req = route.request()
    const url = new URL(req.url())
    let body: unknown = undefined
    if (req.headers()['content-type']?.includes('application/json')) body = req.postDataJSON()
    else body = req.postData()
    calls.push({ path: url.pathname, method: req.method(), body, csrf: req.headers()['x-csrf-token'], contentType: req.headers()['content-type'] })
    const send = (data: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
    if (url.pathname === '/api/auth/csrf') return send({ headerName: 'X-CSRF-TOKEN', token: csrf })
    if (url.pathname === '/api/auth/login') { authenticated = true; csrf = 'autenticado'; return route.fulfill({ status: 204 }) }
    if (url.pathname === '/api/auth/logout') { authenticated = false; return route.fulfill({ status: 204 }) }
    if (!authenticated) return route.fulfill({ status: 401 })
    if (url.pathname === '/api/auth/me') return send({ usuarioId: 1, nome: 'Ana Souza', email: 'ana@example.com', organizacaoId: 1, papel: options.admin === false ? 'FUNCIONARIO' : 'ADMIN' })
    if (url.pathname === '/api/organizacao') return send({ id: 1, nome: 'Rede Primavera' })
    if (url.pathname === '/api/auth/permissoes') return send((options.permissions ?? ['CONSULTAR', 'EDITAR', 'CADASTRAR', 'INATIVAR']).map(acao => ({ modulo: 'PRODUTOS', acao })))
    if (url.pathname === '/api/categorias') return send({ itens: [categoria], total: 1, pagina: 0, tamanho: 20 })
    if (url.pathname === '/api/produtos/opcoes') return send({ unidadesMedida: ['UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT'], imagemMaxBytes: 5242880, formatosImagem: ['image/png', 'image/jpeg'] })
    if (url.pathname === '/api/produtos' && req.method() === 'POST') return send({ ...produto, ...(body as object) }, 201)
    if (url.pathname === '/api/produtos') return send({ itens: [produto], total: 1, pagina: 0, tamanho: 20 })
    if (url.pathname === '/api/produtos/1' && req.method() === 'PUT') return send({ ...produto, ...(body as { dados: object }).dados, versao: 4 })
    if (url.pathname === '/api/produtos/1') return send(produto)
    return route.fulfill({ status: 404 })
  })
  return calls
}
async function preencherPrecificacao(page: Page) {
  await page.getByRole('textbox', { name: 'Valor de compra (R$)', exact: true }).fill('10,00')
  await page.getByRole('textbox', { name: 'Acréscimo / markup (%)', exact: true }).fill('50')
}
async function preencherFiscalObrigatorio(page: Page) {
  await preencherPrecificacao(page)
  await page.getByRole('textbox', { name: 'NCM', exact: true }).fill('09012100')
  await page.getByRole('textbox', { name: 'CFOP padrão', exact: true }).fill('5102')
  await page.getByRole('combobox', { name: 'Situação tributária de ICMS', exact: true }).selectOption('102')
}
test('login usa formulário e renova CSRF para logout; limpa a tela privada', async ({ page }) => {
  const calls = await mockApi(page, { authenticated: false })
  await page.goto('/')
  await page.getByRole('heading', { name: 'Entre no seu espaço' }).waitFor()
  await page.screenshot({ path: test.info().outputPath('login-identidade-visual.png'), fullPage: true })
  await page.getByLabel('E-mail', { exact: true }).fill('ana@example.com')
  await page.getByLabel('Senha', { exact: true }).fill('SenhaSegura123')
  await page.getByRole('button', { name: 'Entrar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Produtos', exact: true })).toBeVisible()
  const login = calls.find(c => c.path === '/api/auth/login')!
  expect(login.csrf).toBe('anonimo')
  expect(login.contentType).toContain('application/x-www-form-urlencoded')
  expect(String(login.body)).toContain('email=ana%40example.com')
  await page.getByRole('button', { name: 'Sair da conta' }).click()
  await expect(page.getByRole('heading', { name: 'Entre no seu espaço' })).toBeVisible()
  expect(calls.find(c => c.path === '/api/auth/logout')?.csrf).toBe('autenticado')
  await expect(page.getByText(produto.nome, { exact: true })).toHaveCount(0)
})
test('funcionário com consulta não vê ações de escrita nem manutenção de categorias', async ({ page }) => {
  await mockApi(page, { admin: false, permissions: ['CONSULTAR'] })
  await page.goto('/')
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Novo produto' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Inativar', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: 'Categorias', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Categorias', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Nova categoria' })).toHaveCount(0)
})
test('sem CONSULTAR não consulta o catálogo', async ({ page }) => {
  const calls = await mockApi(page, { admin: false, permissions: [] })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Seu acesso está sendo preparado' })).toBeVisible()
  expect(calls.some(c => c.path.startsWith('/api/produtos'))).toBe(false)
})
test('conflito mantém formulário e não repete edição automaticamente', async ({ page }) => {
  await mockApi(page)
  let writes = 0
  await page.route('**/api/produtos/1', route => {
    if (route.request().method() !== 'PUT') return route.fallback()
    writes++
    expect(route.request().postDataJSON().versao).toBe(3)
    return route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Versão desatualizada.' }) })
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Novo café')
  await page.getByRole('button', { name: 'Salvar produto' }).click()
  await expect(page.getByRole('alert')).toContainText('Versão desatualizada.')
  await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('Novo café')
  expect(writes).toBe(1)
})
test('expiração de sessão limpa catálogo e retorna ao login', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  await page.route('**/api/produtos?**', route => route.fulfill({ status: 401 }))
  await page.getByRole('textbox', { name: 'Buscar produtos' }).fill('Outro café')
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Entre no seu espaço' })).toBeVisible()
  await expect(page.getByText(produto.nome, { exact: true })).toHaveCount(0)
})
test('upload com falha permite tentar novamente sem cadastrar outro produto', async ({ page }) => {
  const calls = await mockApi(page)
  let uploads = 0
  await page.route('**/api/produtos/1/imagem', route => {
    uploads++
    return uploads === 1
      ? route.fulfill({ status: 503, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Armazenamento indisponível.' }) })
      : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...produto, imagemUrl: '/api/produtos/1/imagem', versao: 4 }) })
  })
  await page.goto('/')
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('catalogo.png'), fullPage: true })
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Café novo')
  await page.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('000987')
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
  await preencherFiscalObrigatorio(page)
  const image = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 2; canvas.height = 2
    canvas.getContext('2d')!.fillRect(0, 0, 2, 2)
    return canvas.toDataURL('image/png').split(',')[1]
  })
  await page.getByLabel('Selecionar imagem', { exact: true }).setInputFiles({
    name: 'foto.png', mimeType: 'image/png', buffer: Buffer.from(image, 'base64'),
  })
  await expect(page.getByText('foto.png', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Salvar produto' }).click()
  await expect(page.getByRole('alert')).toContainText('Armazenamento indisponível.')
  await page.getByRole('button', { name: 'Tentar enviar imagem' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.filter(c => c.path === '/api/produtos' && c.method === 'POST')).toHaveLength(1)
  expect(uploads).toBe(2)
})
test('modal confirma descarte e mantém foco dentro do diálogo', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  await page.screenshot({ path: test.info().outputPath('catalogo.png'), fullPage: true })
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto pendente')
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.keyboard.press('Tab')
  expect(await page.evaluate(() => !!document.activeElement?.closest('dialog'))).toBe(true)
})
test('layout móvel não transborda a página e permite preencher modal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('catalogo.png'), fullPage: true })
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Unidade de medida', exact: true })).toBeVisible()
})

test('cria categoria dentro do produto, seleciona e preserva os dados preenchidos', async ({ page }) => {
  const calls = await mockApi(page)
  const novaCategoria = { ...categoria, id: 2, nome: 'Bebidas' }
  await page.route('**/api/categorias', route => route.request().method() === 'POST'
    ? route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(novaCategoria) })
    : route.fallback())
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  const productDialog = page.getByRole('dialog', { name: 'Novo produto', exact: true })
  await productDialog.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Suco de uva')
  await productDialog.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('000456')
  await productDialog.getByLabel('Marca', { exact: true }).fill('Marca teste')
  await productDialog.getByLabel('Controla lote').check()
  await productDialog.getByRole('button', { name: 'Nova categoria', exact: true }).click()
  const categoryDialog = page.getByRole('dialog', { name: 'Nova categoria', exact: true })
  await expect(categoryDialog).toBeVisible()
  await categoryDialog.getByLabel('Nome da categoria *', { exact: true }).fill('Bebidas')
  expect(calls.filter(c => c.path === '/api/produtos' && c.method === 'POST')).toHaveLength(0)
  await categoryDialog.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
  await expect(categoryDialog).toHaveCount(0)
  await expect(productDialog.getByRole('combobox', { name: 'Categoria', exact: true })).toHaveValue('2')
  await expect(productDialog.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('Suco de uva')
  await expect(productDialog.getByRole('textbox', { name: 'Código de barras', exact: true })).toHaveValue('000456')
  await expect(productDialog.getByLabel('Marca', { exact: true })).toHaveValue('Marca teste')
  await expect(productDialog.getByRole('button', { name: 'Nova categoria', exact: true })).toBeFocused()
  await preencherFiscalObrigatorio(page)
  await productDialog.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(productDialog).toHaveCount(0)
  expect(calls.find(c => c.path === '/api/produtos' && c.method === 'POST')?.body).toMatchObject({
    nome: 'Suco de uva', codigoBarras: '000456', categoriaId: 2, marca: 'Marca teste', controlaLote: true,
  })
})
test('Escape fecha somente o cadastro de categoria e mantém o produto aberto', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto em andamento')
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: 'Nova categoria', exact: true })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: 'Novo produto', exact: true })).toBeVisible()
  await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('Produto em andamento')
})
test('categoria duplicada mantém preenchimento e permite cancelar sem alterar seleção do produto', async ({ page }) => {
  await mockApi(page)
  let posts = 0
  await page.route('**/api/categorias', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    posts++
    return route.fulfill({ status: 409, contentType: 'application/problem+json', body: JSON.stringify({ detail: 'Nome já utilizado na organização.' }) })
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto pendente')
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Nova categoria', exact: true })
  await dialog.getByLabel('Nome da categoria *', { exact: true }).fill('Mercearia')
  await dialog.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('Nome já utilizado')
  await expect(dialog.getByLabel('Nome da categoria *', { exact: true })).toHaveValue('Mercearia')
  expect(posts).toBe(1)
  page.once('dialog', confirm => confirm.accept())
  await dialog.getByRole('button', { name: 'Cancelar', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('combobox', { name: 'Categoria', exact: true })).toHaveValue('1')
  await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('Produto pendente')
})
test('funcionário com cadastro de produtos não pode cadastrar categorias no modal', async ({ page }) => {
  await mockApi(page, { admin: false, permissions: ['CONSULTAR', 'CADASTRAR', 'EDITAR'] })
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await expect(page.getByRole('combobox', { name: 'Categoria', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Nova categoria', exact: true })).toHaveCount(0)
})
test('cadastro de categoria no produto funciona em tela móvel', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Nova categoria', exact: true })
  await expect(dialog.getByLabel('Nome da categoria *', { exact: true })).toBeFocused()
  await page.screenshot({ path: test.info().outputPath('nova-categoria-mobile.png') })
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
})

test('produto tem seções contínuas com scroll e leva o foco ao erro fiscal', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Novo produto', exact: true })
  await expect(dialog.getByRole('tab')).toHaveCount(0)
  for (const name of ['Geral', 'Operacional', 'Fiscal']) {
    await expect(dialog.getByRole('heading', { name, exact: true })).toBeVisible()
  }
  expect(await dialog.locator('.modal-body').evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true)
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Café com dados fiscais')
  await page.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('000987')
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
  await preencherFiscalObrigatorio(page)
  const ncm = dialog.getByRole('textbox', { name: 'NCM', exact: true })
  await ncm.fill('0901210')
  await dialog.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(ncm).toBeFocused()
  await expect(dialog.getByRole('alert')).toContainText('NCM: informe 8 dígitos.')
  await ncm.scrollIntoViewIfNeeded()
  expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth)).toBe(true)
  await page.screenshot({ path: test.info().outputPath('produto-secao-fiscal-mobile.png') })
})
test('NCM e CEST são formatados ao digitar e enviados somente com dígitos', async ({ page }) => {
  const calls = await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Café fiscal')
  await page.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('00000999')
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
  const ncm = page.getByRole('textbox', { name: 'NCM', exact: true })
  const cest = page.getByLabel('CEST', { exact: true })
  await ncm.pressSequentially('09012100')
  await expect(ncm).toHaveValue('0901.21.00')
  await cest.pressSequentially('0100100')
  await expect(cest).toHaveValue('01.001.00')
  await page.getByRole('textbox', { name: 'CFOP padrão', exact: true }).fill('5102')
  await page.getByRole('combobox', { name: 'Situação tributária de ICMS', exact: true }).selectOption('102')
  await preencherPrecificacao(page)
  // Apagar ao lado de um separador não pode prender o cursor.
  await ncm.evaluate((input: HTMLInputElement) => { input.focus(); input.setSelectionRange(5, 5) })
  await ncm.press('Backspace')
  await expect(ncm).toHaveValue('0902.10.0')
  await ncm.fill('0901.21.00')
  await expect(ncm).toHaveValue('0901.21.00')
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.path === '/api/produtos' && c.method === 'POST')?.body).toMatchObject({
    codigoBarras: '00000999', dadosFiscais: { ...fiscalObrigatorio, cest: '0100100' },
  })
})
test('edição carrega máscaras fiscais e permite limpar dados opcionais', async ({ page }) => {
  const calls = await mockApi(page)
  await page.route('**/api/produtos/1', route => route.request().method() === 'GET'
    ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...produto, dadosFiscais: { ...fiscalObrigatorio, cest: '0100100' } }) })
    : route.fallback())
  await page.goto('/')
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'NCM', exact: true })).toHaveValue('0901.21.00')
  await expect(page.getByLabel('CEST', { exact: true })).toHaveValue('01.001.00')
  await page.getByLabel('CEST', { exact: true }).fill('')
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.path === '/api/produtos/1' && c.method === 'PUT')?.body).toMatchObject({
    versao: 3, dados: { dadosFiscais: { ncm: '09012100', cest: null } },
  })
})

test('situação de ICMS lista os códigos solicitados e salva CSOSN', async ({ page }) => {
  const calls = await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto ICMS')
  await page.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('0000101')
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
  await preencherFiscalObrigatorio(page)
  const select = page.getByRole('combobox', { name: 'Situação tributária de ICMS', exact: true })
  expect(await select.locator('option').evaluateAll(options => options.map(option => (option as HTMLOptionElement).value))).toEqual([
    '', '101', '102', '103', '201', '202', '203', '300', '400', '500', '60',
  ])
  await select.selectOption('101')
  await expect(select.locator('option:checked')).toHaveText('101 – Simples Nacional com crédito')
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.path === '/api/produtos' && c.method === 'POST')?.body).toMatchObject({
    dadosFiscais: { icmsCst: null, icmsCsosn: '101' },
  })
})
for (const [inicial, selecionado] of [['102', '60'], ['60', '500']] as const) {
  test('edição de ICMS troca ' + inicial + ' por ' + (selecionado || 'não informado'), async ({ page }) => {
    const calls = await mockApi(page)
    await page.route('**/api/produtos/1', route => route.request().method() === 'GET'
      ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
          ...produto, dadosFiscais: { ...fiscalObrigatorio, icmsCst: inicial.length === 2 ? inicial : null, icmsCsosn: inicial.length === 3 ? inicial : null },
        }) })
      : route.fallback())
    await page.goto('/')
    await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
    const select = page.getByRole('combobox', { name: 'Situação tributária de ICMS', exact: true })
    await expect(select).toHaveValue(inicial)
    await select.selectOption(selecionado)
    await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(calls.find(c => c.path === '/api/produtos/1' && c.method === 'PUT')?.body).toMatchObject({
      dados: { dadosFiscais: {
        ncm: '09012100', icmsCst: selecionado === '60' ? '60' : null,
        icmsCsosn: selecionado.length === 3 ? selecionado : null,
      } },
    })
  })
}
test('prévia maior aparece ao selecionar imagem e sai ao cancelar, sem salvar produto', async ({ page }) => {
  const calls = await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  const imagem = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 20; canvas.height = 20
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#ff6400'; ctx.fillRect(0, 0, 20, 20)
    return canvas.toDataURL('image/png').split(',')[1]
  })
  await page.getByLabel('Selecionar imagem', { exact: true }).setInputFiles({
    name: 'produto.png', mimeType: 'image/png', buffer: Buffer.from(imagem, 'base64'),
  })
  const preview = page.getByRole('img', { name: 'Prévia da imagem selecionada', exact: true })
  await expect(preview).toBeVisible()
  await expect(preview).toHaveJSProperty('naturalWidth', 20)
  expect(await preview.evaluate(img => img.getBoundingClientRect().width)).toBeGreaterThanOrEqual(140)
  expect(await preview.getAttribute('src')).toMatch(/^blob:/)
  expect(calls.filter(c => c.method !== 'GET')).toHaveLength(0)
  await preview.scrollIntoViewIfNeeded()
  await page.screenshot({ path: test.info().outputPath('produto-preview-compacto.png') })
  await page.getByRole('button', { name: 'Cancelar seleção', exact: true }).click()
  await expect(preview).toHaveCount(0)
  await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('')
})

for (const [label, role] of [
  ['Nome do produto', 'textbox'], ['Categoria', 'combobox'], ['CFOP padrão', 'textbox'],
  ['NCM', 'textbox'], ['Situação tributária de ICMS', 'combobox'],
  ['Valor de compra (R$)', 'textbox'], ['Acréscimo / markup (%)', 'textbox'],
] as const) {
  test('não salva produto com obrigatório vazio: ' + label, async ({ page }) => {
    const calls = await mockApi(page)
    await page.goto('/')
    await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
    await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto completo')
    await page.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('000123')
    await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
    await preencherFiscalObrigatorio(page)
    const campo = page.getByRole(role, { name: label, exact: true })
    await expect(campo).toHaveAttribute('aria-required', 'true')
    expect(await campo.evaluate(input => {
      const label = (input as HTMLInputElement).labels?.[0]
      const marker = label?.querySelector('.required-indicator')
      return marker ? getComputedStyle(marker).color : null
    })).toBe('rgb(180, 35, 24)')
    if (role === 'combobox') await campo.selectOption('')
    else await campo.fill('')
    await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
    await expect(campo).toBeFocused()
    await expect(campo).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByRole('alert')).toHaveCount(1)
    expect(calls.filter(c => c.method === 'POST' && c.path === '/api/produtos')).toHaveLength(0)
    if (label !== 'Nome do produto') await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('Produto completo')
    if (label === 'Situação tributária de ICMS') {
      await page.screenshot({ path: test.info().outputPath('produto-obrigatorios.png') })
    }
  })
}
test('edição de produto antigo exige completar os dados fiscais antes de salvar', async ({ page }) => {
  const calls = await mockApi(page)
  await page.route('**/api/produtos/1', route => route.request().method() === 'GET'
    ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...produto, dadosFiscais: null }) })
    : route.fallback())
  await page.goto('/')
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(3)
  expect(calls.filter(c => c.method === 'PUT')).toHaveLength(0)
  await preencherFiscalObrigatorio(page)
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.filter(c => c.method === 'PUT')).toHaveLength(1)
})

for (const permitidoParaMenores of [false, true]) {
  test('categoria no produto persiste a opção permitidoParaMenores=' + permitidoParaMenores, async ({ page }) => {
    const calls = await mockApi(page)
    let enviado: Record<string, unknown> | undefined
    await page.route('**/api/categorias', route => {
      if (route.request().method() !== 'POST') return route.fallback()
      enviado = route.request().postDataJSON()
      return route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify({
        ...categoria, ...enviado, id: 2,
      }) })
    })
    await page.goto('/')
    await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
    await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto em andamento')
    await page.getByRole('button', { name: 'Nova categoria', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: 'Nova categoria', exact: true })
    const checkbox = dialog.getByRole('checkbox', { name: /Proibida para menores de 18 anos/ })
    await expect(checkbox).not.toBeChecked()
    await checkbox.setChecked(!permitidoParaMenores)
    await dialog.getByLabel('Nome da categoria *', { exact: true }).fill(permitidoParaMenores ? 'Alimentos' : 'Bebidas alcoólicas')
    await dialog.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
    await expect(dialog).toHaveCount(0)
    expect(enviado?.permitidoParaMenores).toBe(permitidoParaMenores)
    await expect(page.getByRole('combobox', { name: 'Categoria', exact: true })).toHaveValue('2')
    await expect(page.getByRole('textbox', { name: 'Nome do produto', exact: true })).toHaveValue('Produto em andamento')
    expect(calls.filter(c => c.path === '/api/produtos' && c.method === 'POST')).toHaveLength(0)
  })
}
test('edição de categoria livre carrega desmarcada e permite restringir', async ({ page }) => {
  await mockApi(page)
  let enviado: Record<string, unknown> | undefined
  await page.route('**/api/categorias**', route => {
    const request = route.request()
    if (request.method() === 'PUT') {
      enviado = request.postDataJSON()
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        ...categoria, ...enviado, versao: 1,
      }) })
    }
    if (request.method() === 'GET') return route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ itens: [{ ...categoria, permitidoParaMenores: true }], total: 1, pagina: 0, tamanho: 20 }),
    })
    return route.fallback()
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'Categorias', exact: true }).click()
  await page.getByRole('button', { name: 'Editar Mercearia', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Editar categoria', exact: true })
  const checkbox = dialog.getByRole('checkbox', { name: /Proibida para menores de 18 anos/ })
  await expect(checkbox).not.toBeChecked()
  await checkbox.check()
  await dialog.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(enviado).toMatchObject({ nome: 'Mercearia', permitidoParaMenores: false, versao: 0 })
})

test('edição de categoria restrita carrega marcada e permite liberar para menores', async ({ page }) => {
  await mockApi(page)
  let enviado: Record<string, unknown> | undefined
  await page.route('**/api/categorias**', route => {
    if (route.request().method() === 'PUT') {
      enviado = route.request().postDataJSON()
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
        ...categoria, ...enviado, versao: 1,
      }) })
    }
    if (route.request().method() === 'GET') return route.fulfill({
      status: 200, contentType: 'application/json',
      body: JSON.stringify({ itens: [{ ...categoria, permitidoParaMenores: false }], total: 1, pagina: 0, tamanho: 20 }),
    })
    return route.fallback()
  })
  await page.goto('/')
  await page.getByRole('button', { name: 'Categorias', exact: true }).click()
  await page.getByRole('button', { name: 'Editar Mercearia', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: 'Editar categoria', exact: true })
  const checkbox = dialog.getByRole('checkbox', { name: /Proibida para menores de 18 anos/ })
  await expect(checkbox).toBeChecked()
  await checkbox.uncheck()
  await dialog.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(enviado).toMatchObject({ nome: 'Mercearia', permitidoParaMenores: true, versao: 0 })
})

for (const recurso of ['produtos', 'categorias'] as const) {
  test('paginação de ' + recurso + ' usa 10, 25 e 50 itens no servidor e preserva filtros', async ({ page }) => {
    await mockApi(page)
    const pedidos: { pagina: number; tamanho: number; busca: string | null; situacao: string | null }[] = []
    await page.route('**/api/' + recurso + '?**', route => {
      const params = new URL(route.request().url()).searchParams
      const pagina = Number(params.get('pagina') ?? 0)
      const tamanho = Number(params.get('tamanho') ?? 10)
      pedidos.push({ pagina, tamanho, busca: params.get('busca'), situacao: params.get(recurso === 'produtos' ? 'ativo' : 'ativa') })
      const itens = Array.from({ length: 61 }, (_, index) => ({
        ...(recurso === 'produtos' ? produto : categoria),
        id: index + 1, nome: 'Item ' + String(index + 1).padStart(3, '0'),
      })).slice(pagina * tamanho, (pagina + 1) * tamanho)
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ itens, total: 61, pagina, tamanho }) })
    })
    await page.goto('/')
    if (recurso === 'categorias') await page.getByRole('button', { name: 'Categorias', exact: true }).click()
    const painel = page.locator('section.panel')
    const tamanho = painel.getByRole('combobox', { name: 'Itens por página', exact: true })
    await expect(tamanho).toHaveValue('10')
    expect(await tamanho.locator('option').allTextContents()).toEqual(['10', '25', '50'])
    await expect(painel.locator('tbody tr')).toHaveCount(10)
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–10 de 61 registros')
    await expect(painel.getByRole('button', { name: 'Anterior', exact: true })).toBeDisabled()
    await page.getByRole('textbox', { name: recurso === 'produtos' ? 'Buscar produtos' : 'Buscar categorias', exact: true }).fill('Item')
    await page.getByRole('button', { name: 'Buscar', exact: true }).click()
    await page.getByRole('combobox', { name: 'Filtrar situação', exact: true }).selectOption('true')
    await expect(painel.locator('tbody tr')).toHaveCount(10)
    await painel.getByRole('button', { name: 'Próxima', exact: true }).click()
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('11–20 de 61 registros')
    await tamanho.selectOption('25')
    await expect(painel.locator('tbody tr')).toHaveCount(25)
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–25 de 61 registros')
    expect(pedidos.at(-1)).toMatchObject({ pagina: 0, tamanho: 25, busca: 'Item', situacao: 'true' })
    await painel.getByRole('button', { name: 'Próxima', exact: true }).click()
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('26–50 de 61 registros')
    await tamanho.selectOption('50')
    await expect(painel.locator('tbody tr')).toHaveCount(50)
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–50 de 61 registros')
    expect(pedidos.at(-1)).toMatchObject({ pagina: 0, tamanho: 50, busca: 'Item', situacao: 'true' })
    await painel.getByRole('button', { name: 'Próxima', exact: true }).click()
    await expect(painel.locator('tbody tr')).toHaveCount(11)
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('51–61 de 61 registros')
    await expect(painel.getByRole('button', { name: 'Próxima', exact: true })).toBeDisabled()
    await painel.getByRole('button', { name: 'Anterior', exact: true }).click()
    await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–50 de 61 registros')
    await tamanho.selectOption('10')
    await expect(painel.locator('tbody tr')).toHaveCount(10)
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: test.info().outputPath('painel-' + recurso + '-compacto.png'), fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await tamanho.scrollIntoViewIfNeeded()
    await expect(tamanho).toBeVisible()
    await expect(painel.getByRole('button', { name: 'Próxima', exact: true })).toBeVisible()
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: test.info().outputPath('paginacao-' + recurso + '-mobile.png'), fullPage: true })
  })
}
test('seletor de tamanho mantém foco e fica acessível durante o carregamento', async ({ page }) => {
  await mockApi(page)
  let liberar = () => {}
  const continuar = new Promise<void>(resolve => { liberar = resolve })
  await page.route('**/api/produtos?**', async route => {
    const params = new URL(route.request().url()).searchParams
    if (params.get('tamanho') !== '25') return route.fallback()
    await continuar
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ itens: [produto], total: 1, pagina: 0, tamanho: 25 }) })
  })
  await page.goto('/')
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  const tamanho = page.getByRole('combobox', { name: 'Itens por página', exact: true })
  await tamanho.focus()
  try {
    await tamanho.selectOption('25')
    await expect(page.locator('.pagination').getByRole('status')).toHaveText('Carregando…')
    await expect(tamanho).toBeFocused()
    await expect(tamanho).toHaveValue('25')
    await expect(page.getByRole('button', { name: 'Próxima', exact: true })).toBeDisabled()
  } finally { liberar() }
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  await expect(page.locator('.pagination').getByRole('status')).toHaveText('1–1 de 1 registros')
})

async function pngDeTeste(page: Page) {
  const imagem = await page.evaluate(() => {
    const canvas = document.createElement('canvas')
    canvas.width = 20; canvas.height = 20
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#ff6400'; ctx.fillRect(0, 0, 20, 20)
    return canvas.toDataURL('image/png').split(',')[1]
  })
  return Buffer.from(imagem, 'base64')
}
test('filtra produtos com e sem imagem no servidor, mantém filtros e reseta paginação', async ({ page }) => {
  await mockApi(page)
  const png = await pngDeTeste(page)
  const pedidos: URLSearchParams[] = []
  const registros = Array.from({ length: 24 }, (_, index) => ({
    ...produto, id: index + 1, nome: 'Produto ' + String(index + 1).padStart(2, '0'),
    imagemUrl: index % 2 === 0 ? '/api/produtos/' + (index + 1) + '/imagem?versao=3' : null,
  }))
  await page.route('**/api/produtos/*/imagem?**', route => route.fulfill({ status: 200, contentType: 'image/png', body: png }))
  await page.route('**/api/produtos?**', route => {
    const params = new URL(route.request().url()).searchParams
    pedidos.push(params)
    const pagina = Number(params.get('pagina') ?? 0)
    const tamanho = Number(params.get('tamanho') ?? 10)
    const filtrados = registros.filter(p => !params.has('comImagem') || (!!p.imagemUrl === (params.get('comImagem') === 'true')))
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      itens: filtrados.slice(pagina * tamanho, (pagina + 1) * tamanho), total: filtrados.length, pagina, tamanho,
    }) })
  })
  await page.goto('/')
  await page.getByRole('textbox', { name: 'Buscar produtos', exact: true }).fill('Produto')
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
  await page.getByRole('combobox', { name: 'Filtrar categoria', exact: true }).selectOption('1')
  await page.getByRole('combobox', { name: 'Filtrar situação', exact: true }).selectOption('true')
  const painel = page.locator('section.panel')
  await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–10 de 24 registros')
  await painel.getByRole('button', { name: 'Próxima', exact: true }).click()
  await expect(painel.locator('.pagination').getByRole('status')).toHaveText('11–20 de 24 registros')
  const filtro = page.getByRole('combobox', { name: 'Filtrar imagem', exact: true })
  await filtro.selectOption('true')
  await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–10 de 12 registros')
  expect(Object.fromEntries(pedidos.at(-1)!)).toMatchObject({ comImagem: 'true', pagina: '0', busca: 'Produto', categoriaId: '1', ativo: 'true' })
  await painel.getByRole('combobox', { name: 'Itens por página', exact: true }).selectOption('25')
  await expect(painel.locator('tbody tr')).toHaveCount(12)
  await expect(painel.locator('tbody img')).toHaveCount(12)
  await filtro.selectOption('false')
  await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–12 de 12 registros')
  await expect(painel.locator('tbody img')).toHaveCount(0)
  expect(Object.fromEntries(pedidos.at(-1)!)).toMatchObject({ comImagem: 'false', tamanho: '25', busca: 'Produto', categoriaId: '1', ativo: 'true' })
  await page.getByRole('button', { name: 'Limpar filtros', exact: true }).click()
  await expect(painel.locator('.pagination').getByRole('status')).toHaveText('1–24 de 24 registros')
  await expect(filtro).toHaveValue('')
  expect(pedidos.at(-1)!.has('comImagem')).toBe(false)
  expect(pedidos.at(-1)!.has('busca')).toBe(false)
})
test('miniatura e modal exibem a imagem cadastrada sem duplicar parâmetros de versão', async ({ page }) => {
  await mockApi(page)
  const png = await pngDeTeste(page)
  const comImagem = { ...produto, imagemUrl: '/api/produtos/1/imagem?versao=3' }
  await page.route('**/api/produtos?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({ itens: [comImagem], total: 1, pagina: 0, tamanho: 10 }),
  }))
  await page.route('**/api/produtos/1', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(comImagem) }))
  await page.route('**/api/produtos/1/imagem?**', route => route.fulfill({ status: 200, contentType: 'image/png', body: png }))
  await page.goto('/')
  const miniatura = page.getByRole('img', { name: 'Imagem de ' + produto.nome, exact: true })
  await expect(miniatura).toHaveJSProperty('naturalWidth', 20)
  await expect(miniatura).toHaveAttribute('src', comImagem.imagemUrl)
  await page.screenshot({ path: test.info().outputPath('catalogo-miniatura.png'), fullPage: true })
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  const imagem = page.getByRole('dialog').getByRole('img', { name: produto.nome, exact: true })
  await expect(imagem).toHaveJSProperty('naturalWidth', 20)
  await expect(imagem).toHaveAttribute('src', comImagem.imagemUrl)
})
test('falha na miniatura mantém o produto visível e uma nova versão permite recarregar a imagem', async ({ page }) => {
  await mockApi(page)
  const png = await pngDeTeste(page)
  let versao = 3
  await page.route('**/api/produtos?**', route => route.fulfill({
    status: 200, contentType: 'application/json', body: JSON.stringify({
      itens: [{ ...produto, versao, imagemUrl: '/api/produtos/1/imagem?versao=' + versao }], total: 1, pagina: 0, tamanho: 10,
    }),
  }))
  await page.route('**/api/produtos/1/imagem?**', route => new URL(route.request().url()).searchParams.get('versao') === '3'
    ? route.fulfill({ status: 404 })
    : route.fulfill({ status: 200, contentType: 'image/png', body: png }))
  await page.goto('/')
  await expect(page.getByRole('img', { name: 'Imagem indisponível', exact: true })).toBeVisible()
  await expect(page.getByText(produto.nome, { exact: true })).toBeVisible()
  versao = 4
  await page.getByRole('textbox', { name: 'Buscar produtos', exact: true }).fill('Café')
  await page.getByRole('button', { name: 'Buscar', exact: true }).click()
  const miniatura = page.getByRole('img', { name: 'Imagem de ' + produto.nome, exact: true })
  await expect(miniatura).toHaveJSProperty('naturalWidth', 20)
  await expect(miniatura).toHaveAttribute('src', '/api/produtos/1/imagem?versao=4')
})


test('precificação atualiza sugestão, aceita vírgula e permite salvar sem preço padrão', async ({ page }) => {
  const calls = await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByRole('textbox', { name: 'Nome do produto', exact: true }).fill('Produto com custo')
  await page.getByRole('textbox', { name: 'Código de barras', exact: true }).fill('000789')
  await page.getByRole('combobox', { name: 'Categoria', exact: true }).selectOption('1')
  await preencherFiscalObrigatorio(page)
  const preco = page.getByRole('textbox', { name: 'Preço de venda padrão (R$)', exact: true })
  await expect(preco).toHaveValue('15,00')
  await expect(preco).toHaveAttribute('readonly', '')
  await page.getByRole('textbox', { name: 'Acréscimo / markup (%)', exact: true }).fill('150')
  await expect(preco).toHaveValue('25,00')
  await page.getByRole('textbox', { name: 'Valor de compra (R$)', exact: true }).fill('0,10')
  await page.getByRole('textbox', { name: 'Acréscimo / markup (%)', exact: true }).fill('5')
  await expect(preco).toHaveValue('0,11')
  await preco.scrollIntoViewIfNeeded()
  await page.screenshot({ path: test.info().outputPath('produto-precificacao.png') })
  await page.getByRole('checkbox', { name: 'Definir preço de venda padrão' }).uncheck()
  await expect(preco).toHaveValue('')
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.method === 'POST' && c.path === '/api/produtos')?.body).toMatchObject({ valorCompra: 0.1, percentualMarkup: 5, precoVendaPadrao: null })
})

test('edição recalcula e envia sugestão com a versão do produto', async ({ page }) => {
  const calls = await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  await expect(page.getByRole('textbox', { name: 'Valor de compra (R$)', exact: true })).toHaveValue('10,00')
  await page.getByRole('textbox', { name: 'Acréscimo / markup (%)', exact: true }).fill('33,33')
  await expect(page.getByRole('textbox', { name: 'Preço de venda padrão (R$)', exact: true })).toHaveValue('13,33')
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.method === 'PUT')?.body).toMatchObject({ versao: 3, dados: { valorCompra: 10, percentualMarkup: 33.33, precoVendaPadrao: 13.33 } })
})

test('produto antigo exige preencher compra e markup sem inventar preços', async ({ page }) => {
  const calls = await mockApi(page)
  await page.route('**/api/produtos/1', route => route.request().method() === 'GET'
    ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...produto, valorCompra: null, percentualMarkup: null, precoVendaPadrao: null }) })
    : route.fallback())
  await page.goto('/')
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(2)
  expect(calls.some(c => c.method === 'PUT')).toBe(false)
  await preencherPrecificacao(page)
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.method === 'PUT')?.body).toMatchObject({ dados: { valorCompra: 10, percentualMarkup: 50, precoVendaPadrao: null } })
})
