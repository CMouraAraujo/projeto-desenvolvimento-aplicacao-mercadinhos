import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getCsrf, json, queryString, request, resetCsrf } from './api'

const fetchMock = vi.fn<typeof fetch>()
beforeEach(() => { resetCsrf(); vi.stubGlobal('fetch', fetchMock); vi.stubGlobal('window', new EventTarget()) })
afterEach(() => { vi.unstubAllGlobals(); vi.resetAllMocks() })
const token = (value = 'anonimo') => Response.json({ headerName: 'X-CSRF-TOKEN', token: value })
describe('cliente autenticado', () => {
  it('envia cookies, formulário de login e CSRF; renova token após login', async () => {
    fetchMock.mockResolvedValueOnce(token()).mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(token('autenticado')).mockResolvedValueOnce(new Response(null, { status: 204 }))
    await request('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ email: 'teste@example.com', senha: 'segredo' }), anonymous: true })
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('X-CSRF-TOKEN')).toBe('anonimo')
    expect(fetchMock.mock.calls[1][1]?.credentials).toBe('same-origin')
    resetCsrf()
    await getCsrf()
    await request('/auth/logout', { method: 'POST' })
    expect(new Headers(fetchMock.mock.calls[3][1]?.headers).get('X-CSRF-TOKEN')).toBe('autenticado')
  })
  it('não repete mutações em conflito e conserva ProblemDetail.campos', async () => {
    fetchMock.mockResolvedValueOnce(token()).mockResolvedValueOnce(Response.json({ detail: 'Código duplicado.', campos: ['codigoBarras'] }, { status: 409 }))
    await expect(request('/produtos', json('POST', {}))).rejects.toMatchObject({ status: 409, campos: ['codigoBarras'] })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
  it('notifica expiração de sessão para limpar dados privados', async () => {
    const expired = vi.fn()
    window.addEventListener('session-expired', expired)
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(request('/produtos')).rejects.toBeInstanceOf(ApiError)
    expect(expired).toHaveBeenCalledOnce()
  })
  it('não expira sessão por credencial inválida no login', async () => {
    const expired = vi.fn()
    window.addEventListener('session-expired', expired)
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    await expect(request('/auth/me', { anonymous: true })).rejects.toMatchObject({ status: 401 })
    expect(expired).not.toHaveBeenCalled()
  })
  it('preserva o boundary automático do multipart', async () => {
    fetchMock.mockResolvedValueOnce(token()).mockResolvedValueOnce(Response.json({ id: 1 }))
    const body = new FormData()
    body.set('versao', '2'); body.set('arquivo', new Blob(['imagem'], { type: 'image/png' }), 'imagem.png')
    await request('/produtos/1/imagem', { method: 'PUT', body })
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).has('Content-Type')).toBe(false)
    expect(fetchMock.mock.calls[1][1]?.body).toBe(body)
  })
  it('respeita Retry-After numérico', async () => {
    fetchMock.mockResolvedValueOnce(token()).mockResolvedValueOnce(new Response(null, { status: 429, headers: { 'Retry-After': '90' } }))
    await expect(request('/auth/login', { method: 'POST', anonymous: true })).rejects.toMatchObject({ retryAfter: 90 })
  })
  it('compartilha uma única consulta CSRF entre solicitações simultâneas', async () => {
    fetchMock.mockResolvedValueOnce(token())
    await Promise.all([getCsrf(), getCsrf(), getCsrf()])
    expect(fetchMock).toHaveBeenCalledOnce()
  })
  it('omite filtros vazios e preserva paginação zero', () => {
    expect(queryString({ pagina: 0, busca: '', ativo: undefined, codigo: '0001' })).toBe('pagina=0&codigo=0001')
  })
})
