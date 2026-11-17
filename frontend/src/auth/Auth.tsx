import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError, getCsrf, request, resetCsrf } from '../shared/api'
import type { Acao, Organizacao, Permissao, Usuario } from '../shared/types'
import { pode } from '../shared/types'
import { ErrorNotice, Loading } from '../shared/ui'
import { Login } from './Login'

type Session = { usuario: Usuario; organizacao: Organizacao; permissoes: Permissao[] }
const AuthContext = createContext<(Session & { can: (acao: Acao) => boolean; logout: () => Promise<void> }) | null>(null)
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('Sessão necessária')
  return context
}
export function AuthProvider({ children }: { children: ReactNode }) {
  const client = useQueryClient()
  const [session, setSession] = useState<Session | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [expired, setExpired] = useState(false)
  const clear = useCallback(() => {
    void client.cancelQueries()
    client.clear()
    resetCsrf()
    setSession(null)
  }, [client])
  const loadSession = useCallback(async () => {
    const usuario = await request<Usuario>('/auth/me', { anonymous: true })
    const [organizacao, permissoes] = await Promise.all([
      request<Organizacao>('/organizacao'), request<Permissao[]>('/auth/permissoes'),
    ])
    setSession({ usuario, organizacao, permissoes })
  }, [])
  const initialize = useCallback(async () => {
    setLoading(true); setError(null)
    try { await loadSession() } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) setError(err)
    } finally { setLoading(false) }
  }, [loadSession])
  useEffect(() => {
    void initialize()
    const expire = () => { clear(); setExpired(true) }
    window.addEventListener('session-expired', expire)
    return () => window.removeEventListener('session-expired', expire)
  }, [initialize, clear])
  if (loading) return <main className="boot"><Loading label="Preparando seu espaço de trabalho…"/></main>
  if (error) return <main className="boot"><ErrorNotice error={error}/><button className="button" onClick={() => void initialize()}>Tentar novamente</button></main>
  if (!session) return <Login expired={expired} onLogin={async (email, senha) => {
    clear()
    await getCsrf()
    await request<void>('/auth/login', {
      anonymous: true, method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ email, senha }),
    })
    resetCsrf()
    await getCsrf()
    await loadSession()
    setExpired(false)
  }}/>
  return <AuthContext.Provider value={{ ...session, can: acao => pode(session.usuario, session.permissoes, acao), logout: async () => {
    await request<void>('/auth/logout', { method: 'POST' })
    clear(); setExpired(false)
  } }}>{children}</AuthContext.Provider>
}
