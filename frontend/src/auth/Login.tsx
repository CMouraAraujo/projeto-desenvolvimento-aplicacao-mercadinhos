import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { ApiError } from '../shared/api'
import { ErrorNotice, Field, Icon } from '../shared/ui'

const schema = z.object({ email: z.email('Informe um e-mail válido.').max(254), senha: z.string().min(1, 'Informe sua senha.') })
type LoginData = z.infer<typeof schema>
export function Login({ expired, onLogin }: { expired: boolean; onLogin: (email: string, senha: string) => Promise<void> }) {
  const { register, handleSubmit, resetField, formState: { errors, isSubmitting } } = useForm<LoginData>({ resolver: zodResolver(schema) })
  const [error, setError] = useState<unknown>(null)
  const [waitUntil, setWaitUntil] = useState(0)
  const [remaining, setRemaining] = useState(0)
  useEffect(() => {
    const update = () => setRemaining(Math.max(0, Math.ceil((waitUntil - Date.now()) / 1000)))
    update()
    const timer = window.setInterval(update, 1000)
    return () => window.clearInterval(timer)
  }, [waitUntil])
  return <main className="login-page">
    <section className="login-story"><div className="brand"><span className="brand-mark"><Icon name="leaf" size={26}/></span>mercadinhos<span className="brand-dot">.</span></div><div><span className="eyebrow">MENOS ROTINA. MAIS GESTÃO.</span><h1>Seu catálogo.<br/>Tudo no lugar.</h1><p>Uma base organizada para o dia a dia da sua rede de minimercados.</p><div className="story-card"><Icon name="box" size={32}/><div><strong>Organização que faz diferença</strong><span>Produtos, categorias e informações em um só lugar.</span></div></div></div><small>Painel de gestão · Acesso da sua organização</small></section>
    <section className="login-panel"><div className="login-form"><span className="eyebrow">BEM-VINDO DE VOLTA</span><h2>Entre no seu espaço</h2><p className="muted">Use o e-mail e a senha fornecidos pelo seu administrador.</p>
      {expired && <div className="notice" role="status">Sua sessão terminou. Entre novamente para continuar.</div>}
      {error != null && <ErrorNotice error={error}/>}
      <form onSubmit={handleSubmit(async data => {
        setError(null)
        try { await onLogin(data.email, data.senha) } catch (err) {
          resetField('senha')
          setError(err instanceof ApiError && err.status === 401 ? new Error('E-mail ou senha inválidos, ou conta indisponível.') : err)
          if (err instanceof ApiError && err.status === 429) setWaitUntil(Date.now() + Math.max(err.retryAfter, 1) * 1000)
        }
      })} noValidate>
        <Field label="E-mail" error={errors.email?.message}><input type="email" autoComplete="username" placeholder="voce@empresa.com.br" {...register('email')} aria-invalid={!!errors.email}/></Field>
        <Field label="Senha" error={errors.senha?.message}><input type="password" autoComplete="current-password" placeholder="Digite sua senha" {...register('senha')} aria-invalid={!!errors.senha}/></Field>
        <button className="button full" disabled={isSubmitting || remaining > 0}>{isSubmitting ? 'Entrando…' : remaining ? 'Aguarde ' + remaining + 's' : 'Entrar'}<Icon name="arrow" size={18}/></button>
      </form><p className="login-help">Precisa de acesso? Fale com o administrador da sua organização.</p>
    </div></section>
  </main>
}
