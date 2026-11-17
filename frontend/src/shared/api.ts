type TokenCsrf = { headerName: string; token: string }
type Problem = { detail?: string; campos?: string[] }
let csrf: TokenCsrf | null = null
let csrfPending: Promise<TokenCsrf> | null = null
let sessionGeneration = 0

export class ApiError extends Error {
  constructor(public status: number, message: string, public campos: string[] = [], public retryAfter = 0) {
    super(message)
  }
}

const messages: Record<number, string> = {
  400: 'Confira os campos informados.',
  401: 'Sua sessão expirou ou foi revogada. Entre novamente.',
  403: 'Acesso negado ou token de segurança inválido. Confira seu acesso antes de tentar novamente.',
  404: 'O registro não foi encontrado.',
  409: 'Há um conflito de dados ou uma versão mais recente. Recarregue e revise antes de salvar.',
  413: 'O arquivo excede o tamanho permitido.',
  429: 'Muitas tentativas de acesso. Aguarde antes de tentar novamente.',
  503: 'O serviço está temporariamente indisponível. Tente novamente em instantes.',
}

export function resetCsrf() {
  sessionGeneration++
  csrf = null
  csrfPending = null
}
export async function getCsrf(): Promise<TokenCsrf> {
  if (csrf) return csrf
  if (!csrfPending) {
    const generation = sessionGeneration
    const pending = request<TokenCsrf>('/auth/csrf').then(token => {
      if (generation === sessionGeneration) csrf = token
      return token
    }).finally(() => {
      if (generation === sessionGeneration) csrfPending = null
    })
    csrfPending = pending
  }
  return csrfPending
}

export async function request<T>(path: string, options: RequestInit & { anonymous?: boolean } = {}): Promise<T> {
  const { anonymous, ...init } = options
  const method = init.method ?? 'GET'
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await getCsrf()
    headers.set(token.headerName, token.token)
  }
  let response: Response
  try {
    response = await fetch('/api' + path, { ...init, method, headers, credentials: 'same-origin' })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.')
  }
  if (!response.ok) {
    let problem: Problem = {}
    try { problem = await response.json() as Problem } catch { /* Segurança pode retornar corpo vazio. */ }
    if (response.status === 401 && !anonymous) {
      resetCsrf()
      window.dispatchEvent(new Event('session-expired'))
    }
    if (response.status === 403) resetCsrf()
    const retry = response.headers.get('Retry-After')
    const seconds = retry ? (/^\d+$/.test(retry) ? Number(retry) : Math.max(0, Math.ceil((Date.parse(retry) - Date.now()) / 1000))) : 0
    throw new ApiError(response.status, problem.detail || messages[response.status] || 'Não foi possível concluir a operação.', problem.campos ?? [], Number.isFinite(seconds) ? seconds : 0)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
export function json(method: string, body: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
}
export function queryString(values: Record<string, string | number | undefined>) {
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return params.toString()
}
export function errorMessage(error: unknown) {
  return error instanceof Error ? error.message : 'Não foi possível concluir a operação.'
}
