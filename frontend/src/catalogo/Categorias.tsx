import { useEffect, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/Auth'
import type { Categoria } from '../shared/types'
import { Badge, Empty, ErrorNotice, Icon, Loading, Pagination } from '../shared/ui'
import { catalogo } from './api'
import { CategoriaModal } from './CategoriaModal'

export function Categorias() {
  const { usuario } = useAuth()
  const admin = usuario.papel === 'ADMIN'
  const client = useQueryClient()
  const [buscaInput, setBuscaInput] = useState('')
  const [busca, setBusca] = useState('')
  const [ativa, setAtiva] = useState('')
  const [pagina, setPagina] = useState(0)
  const [tamanho, setTamanho] = useState(10)
  const [modal, setModal] = useState<Categoria | 'nova' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState('')
  const [busy, setBusy] = useState(false)
  const filtros = { pagina, tamanho, busca, ativa }
  const query = useQuery({ queryKey: ['categorias', 'lista', filtros], queryFn: ({ signal }) => catalogo.categorias(filtros, signal) })
  useEffect(() => {
    if (query.data) {
      const ultimaPagina = Math.max(0, Math.ceil(query.data.total / tamanho) - 1)
      if (pagina > ultimaPagina) setPagina(ultimaPagina)
    }
  }, [query.data, pagina, tamanho])
  const invalidate = () => { void client.invalidateQueries({ queryKey: ['categorias'] }) }
  return <>
    <header className="page-heading"><div><span className="eyebrow">SEU CATÁLOGO</span><h1>Categorias</h1><p>A organização do seu catálogo começa por aqui.</p></div>{admin && <button className="button" onClick={() => setModal('nova')}><Icon name="plus" size={18}/>Nova categoria</button>}</header>
    {notice && <div className="notice success" role="status">{notice}</div>}
    {error != null && <ErrorNotice error={error}/>}
    <section className="panel"><div className="panel-title"><h2>Todas as categorias</h2>{query.data && <span className="count">{query.data.total} registros</span>}</div>
      <form className="filters" onSubmit={event => { event.preventDefault(); setBusca(buscaInput.trim()); setPagina(0) }}><label className="search-input"><span className="sr-only">Buscar categorias</span><Icon name="search" size={18}/><input placeholder="Busque pelo nome da categoria" maxLength={100} value={buscaInput} onChange={event => setBuscaInput(event.target.value)}/></label><label><span className="sr-only">Filtrar situação</span><select value={ativa} onChange={event => { setAtiva(event.target.value); setPagina(0) }}><option value="">Todas as situações</option><option value="true">Ativas</option><option value="false">Inativas</option></select></label><button className="button secondary">Buscar</button></form>
      {query.isPending ? <Loading/> : query.error ? <ErrorNotice error={query.error}><button className="button small secondary" onClick={() => void query.refetch()}>Tentar novamente</button></ErrorNotice> : <>
        {!query.data.itens.length ? <Empty title="Nenhuma categoria encontrada"><p>{admin ? 'Cadastre uma categoria para organizar seus produtos.' : 'Consulte o administrador da sua organização.'}</p></Empty> : <div className="table-scroll"><table><thead><tr><th>Nome</th><th>Situação</th><th>Última atualização</th>{admin && <th className="align-right">Ações</th>}</tr></thead><tbody>{query.data.itens.map(c => <tr key={c.id}><td><strong>{c.nome}</strong></td><td><Badge active={c.ativa}/></td><td>{new Date(c.atualizadoEm).toLocaleDateString('pt-BR')}</td>{admin && <td><div className="row-actions"><button className="icon-button" aria-label={'Editar ' + c.nome} onClick={() => setModal(c)}><Icon name="edit" size={17}/></button><button className="text-button" disabled={busy} onClick={async () => {
          if (!window.confirm((c.ativa ? 'Inativar' : 'Reativar') + ' a categoria “' + c.nome + '”? Produtos existentes serão preservados.')) return
          setBusy(true); setError(null)
          try { await catalogo.situacaoCategoria(c); setNotice('Situação da categoria atualizada.') } catch (err) { setError(err) } finally { setBusy(false); invalidate() }
        }}>{c.ativa ? 'Inativar' : 'Reativar'}</button></div></td>}</tr>)}</tbody></table></div>}
      </>}
      <Pagination pagina={pagina} tamanho={tamanho} total={query.data?.total ?? 0} onChange={setPagina} loading={query.isPending} onSizeChange={size => { setTamanho(size); setPagina(0) }}/>
    </section><p className="page-footnote">Categorias inativas permanecem nos produtos existentes e não podem ser escolhidas em novas associações.</p>
    {modal !== null && <CategoriaModal atual={modal === 'nova' ? undefined : modal} onClose={() => setModal(null)} onSaved={() => { invalidate(); setNotice('Categoria salva com sucesso.'); setModal(null) }}/>}
  </>
}
