import { test, expect, type Page } from '@playwright/test'

const categoria = { id: 1, nome: 'Mercearia', ativa: true, versao: 0, criadoEm: '2026-11-16T12:00:00Z', atualizadoEm: '2026-11-16T12:00:00Z' }
const produto = { id: 1, nome: 'Café torrado 500 g', codigoBarras: '00012345', categoriaId: 1, unidadeMedida: 'UN', descricao: null, marca: 'Café da Serra', permiteVendaFracionada: false, controlaLote: false, controlaValidade: true, dadosFiscais: null, ativo: true, versao: 3, imagemUrl: null, criadoEm: '2026-11-16T12:00:00Z', atualizadoEm: '2026-11-16T12:00:00Z', criadoPor: 1, atualizadoPor: 1 }
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
test('login usa formulário e renova CSRF para logout; limpa a tela privada', async ({ page }) => {
  const calls = await mockApi(page, { authenticated: false })
  await page.goto('/')
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
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Novo café')
  await page.getByRole('button', { name: 'Salvar produto' }).click()
  await expect(page.getByRole('alert')).toContainText('Versão desatualizada.')
  await expect(page.getByLabel('Nome do produto *', { exact: true })).toHaveValue('Novo café')
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
  await page.screenshot({ path: test.info().outputPath('catalogo.png'), fullPage: true })
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Café novo')
  await page.getByLabel('Código de barras *', { exact: true }).fill('000987')
  await page.getByLabel('Categoria *', { exact: true }).selectOption('1')
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
  await page.screenshot({ path: test.info().outputPath('catalogo.png'), fullPage: true })
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Produto pendente')
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
  await expect(page.getByLabel('Nome do produto *', { exact: true })).toBeVisible()
  await expect(page.getByLabel('Unidade de medida *', { exact: true })).toBeVisible()
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
  await productDialog.getByLabel('Nome do produto *', { exact: true }).fill('Suco de uva')
  await productDialog.getByLabel('Código de barras *', { exact: true }).fill('000456')
  await productDialog.getByLabel('Marca', { exact: true }).fill('Marca teste')
  await productDialog.getByLabel('Controla lote').check()
  await productDialog.getByRole('button', { name: 'Nova categoria', exact: true }).click()
  const categoryDialog = page.getByRole('dialog', { name: 'Nova categoria', exact: true })
  await expect(categoryDialog).toBeVisible()
  await categoryDialog.getByLabel('Nome da categoria *', { exact: true }).fill('Bebidas')
  expect(calls.filter(c => c.path === '/api/produtos' && c.method === 'POST')).toHaveLength(0)
  await categoryDialog.getByRole('button', { name: 'Salvar categoria', exact: true }).click()
  await expect(categoryDialog).toHaveCount(0)
  await expect(productDialog.getByLabel('Categoria *', { exact: true })).toHaveValue('2')
  await expect(productDialog.getByLabel('Nome do produto *', { exact: true })).toHaveValue('Suco de uva')
  await expect(productDialog.getByLabel('Código de barras *', { exact: true })).toHaveValue('000456')
  await expect(productDialog.getByLabel('Marca', { exact: true })).toHaveValue('Marca teste')
  await expect(productDialog.getByRole('button', { name: 'Nova categoria', exact: true })).toBeFocused()
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
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Produto em andamento')
  await page.getByRole('button', { name: 'Nova categoria', exact: true }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog', { name: 'Nova categoria', exact: true })).toHaveCount(0)
  await expect(page.getByRole('dialog', { name: 'Novo produto', exact: true })).toBeVisible()
  await expect(page.getByLabel('Nome do produto *', { exact: true })).toHaveValue('Produto em andamento')
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
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Produto pendente')
  await page.getByLabel('Categoria *', { exact: true }).selectOption('1')
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
  await expect(page.getByLabel('Categoria *', { exact: true })).toHaveValue('1')
  await expect(page.getByLabel('Nome do produto *', { exact: true })).toHaveValue('Produto pendente')
})
test('funcionário com cadastro de produtos não pode cadastrar categorias no modal', async ({ page }) => {
  await mockApi(page, { admin: false, permissions: ['CONSULTAR', 'CADASTRAR', 'EDITAR'] })
  await page.goto('/')
  await page.getByRole('button', { name: 'Novo produto', exact: true }).click()
  await expect(page.getByLabel('Categoria *', { exact: true })).toBeVisible()
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
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Café com dados fiscais')
  await page.getByLabel('Código de barras *', { exact: true }).fill('000987')
  await page.getByLabel('Categoria *', { exact: true }).selectOption('1')
  const ncm = dialog.getByLabel('NCM', { exact: true })
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
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Café fiscal')
  await page.getByLabel('Código de barras *', { exact: true }).fill('00000999')
  await page.getByLabel('Categoria *', { exact: true }).selectOption('1')
  const ncm = page.getByLabel('NCM', { exact: true })
  const cest = page.getByLabel('CEST', { exact: true })
  await ncm.pressSequentially('09012100')
  await expect(ncm).toHaveValue('0901.21.00')
  await cest.pressSequentially('0100100')
  await expect(cest).toHaveValue('01.001.00')
  // Apagar ao lado de um separador não pode prender o cursor.
  await ncm.evaluate((input: HTMLInputElement) => { input.focus(); input.setSelectionRange(5, 5) })
  await ncm.press('Backspace')
  await expect(ncm).toHaveValue('0902.10.0')
  await ncm.fill('0901.21.00')
  await expect(ncm).toHaveValue('0901.21.00')
  await page.getByRole('button', { name: 'Salvar produto', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find(c => c.path === '/api/produtos' && c.method === 'POST')?.body).toMatchObject({
    codigoBarras: '00000999', dadosFiscais: { ncm: '09012100', cest: '0100100' },
  })
})
test('edição carrega máscaras fiscais e permite limpar dados opcionais', async ({ page }) => {
  const calls = await mockApi(page)
  await page.route('**/api/produtos/1', route => route.request().method() === 'GET'
    ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...produto, dadosFiscais: { ncm: '09012100', cest: '0100100' } }) })
    : route.fallback())
  await page.goto('/')
  await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
  await expect(page.getByLabel('NCM', { exact: true })).toHaveValue('0901.21.00')
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
  await page.getByLabel('Nome do produto *', { exact: true }).fill('Produto ICMS')
  await page.getByLabel('Código de barras *', { exact: true }).fill('0000101')
  await page.getByLabel('Categoria *', { exact: true }).selectOption('1')
  const select = page.getByLabel('Situação tributária de ICMS', { exact: true })
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
for (const [inicial, selecionado] of [['102', '60'], ['60', '500'], ['60', '']] as const) {
  test('edição de ICMS troca ' + inicial + ' por ' + (selecionado || 'não informado'), async ({ page }) => {
    const calls = await mockApi(page)
    await page.route('**/api/produtos/1', route => route.request().method() === 'GET'
      ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
          ...produto, dadosFiscais: { ncm: '09012100', icmsCst: inicial.length === 2 ? inicial : null, icmsCsosn: inicial.length === 3 ? inicial : null },
        }) })
      : route.fallback())
    await page.goto('/')
    await page.getByRole('button', { name: 'Editar ' + produto.nome, exact: true }).click()
    const select = page.getByLabel('Situação tributária de ICMS', { exact: true })
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
    ctx.fillStyle = '#216c51'; ctx.fillRect(0, 0, 20, 20)
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
  await expect(page.getByLabel('Nome do produto *', { exact: true })).toHaveValue('')
})
