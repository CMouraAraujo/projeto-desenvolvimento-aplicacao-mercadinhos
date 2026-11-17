import { useState } from 'react'
import { useAuth } from './auth/Auth'
import { Categorias } from './catalogo/Categorias'
import { Produtos } from './catalogo/Produtos'
import { Empty, ErrorNotice, Icon } from './shared/ui'

export function App() {
  const { usuario, organizacao, can, logout } = useAuth()
  const [page, setPage] = useState<'produtos' | 'categorias'>('produtos')
  const [error, setError] = useState<unknown>(null)
  const [leaving, setLeaving] = useState(false)
  const consultar = can('CONSULTAR')
  return <div className="app-shell"><a href="#conteudo" className="skip-link">Ir para o conteúdo</a>
    <aside className="sidebar"><a className="brand" href="#produtos" onClick={() => setPage('produtos')} aria-label="Mercadinhos, início"><span className="brand-mark"><Icon name="leaf" size={23}/></span>mercadinhos<span className="brand-dot">.</span></a>
      <div className="workspace"><span className="workspace-avatar">{organizacao.nome.slice(0, 1).toUpperCase()}</span><div><small>SEU ESPAÇO</small><strong>{organizacao.nome}</strong></div></div>
      <nav aria-label="Navegação principal"><p className="nav-label">GESTÃO</p>{consultar && <><button aria-current={page === 'produtos' ? 'page' : undefined} className={page === 'produtos' ? 'nav-item selected' : 'nav-item'} onClick={() => setPage('produtos')}><Icon name="box"/>Produtos{page === 'produtos' && <span className="nav-dot"/>}</button><button aria-current={page === 'categorias' ? 'page' : undefined} className={page === 'categorias' ? 'nav-item selected' : 'nav-item'} onClick={() => setPage('categorias')}><Icon name="grid"/>Categorias{page === 'categorias' && <span className="nav-dot"/>}</button></>}</nav>
      <div className="sidebar-note"><Icon name="leaf" size={24}/><strong>Gestão que cresce com você.</strong><p>Uma base bem cuidada para os próximos passos da sua rede.</p></div>
      <div className="sidebar-footer"><span className="connection-dot"/>Ambiente da organização</div>
    </aside>
    <div className="main-area"><header className="topbar"><div className="breadcrumb">Gestão<span>/</span><strong>{consultar ? page === 'produtos' ? 'Produtos' : 'Categorias' : 'Início'}</strong></div><div className="account"><span className="avatar">{usuario.nome.slice(0, 1).toUpperCase()}</span><div><strong>{usuario.nome}</strong><small>{usuario.papel === 'ADMIN' ? 'Administrador' : 'Funcionário'}</small></div><button className="icon-button" aria-label="Sair da conta" title="Sair da conta" disabled={leaving} onClick={async () => {
      setLeaving(true); setError(null)
      try { await logout() } catch (err) { setError(err) } finally { setLeaving(false) }
    }}><Icon name="logout" size={18}/></button></div></header>
      <main id="conteudo" className="main-content">{error != null && <ErrorNotice error={error}/>}
        {!consultar ? <Empty title="Seu acesso está sendo preparado"><p>Você entrou na organização {organizacao.nome}. Peça ao administrador a liberação do módulo Produtos.</p></Empty> : page === 'produtos' ? <Produtos/> : <Categorias/>}
      </main>
    </div>
  </div>
}
