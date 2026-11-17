import { useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/Auth'
import type { Produto } from '../shared/types'
import { Badge, Empty, ErrorNotice, Icon, Loading, Pagination } from '../shared/ui'
import { catalogo } from './api'
import { ProdutoModal } from './ProdutoModal'

export function Produtos() {
  const { can } = useAuth()
  const client = useQueryClient()
  const [buscaInput, setBuscaInput] = useState('')
  const [busca, setBusca] = useState('')
  const [categoriaId, setCategoria] = useState('')
  const [ativo, setAtivo] = useState('')
  const [pagina, setPagina] = useState(0)
  const [modal, setModal] = useState<number | 'novo' | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [notice, setNotice] = useState('')
  const [changing, setChanging] = useState<number | null>(null)
  const filtros = { pagina, tamanho: 20, busca, categoriaId, ativo }
  const produtos = useQuery({ queryKey: ['produtos', filtros], queryFn: ({ signal }) => catalogo.produtos(filtros, signal) })
  const categorias = useQuery({ queryKey: ['categorias', 'seletor'], queryFn: ({ signal }) => catalogo.todasCategorias(signal) })
  const categoryName = (id: number) => categorias.data?.find(c => c.id === id)?.nome ?? 'Categoria #' + id
  const search = (event: FormEvent) => { event.preventDefault(); setBusca(buscaInput.trim()); setPagina(0) }
  async function situacao(produto: Produto) {
    if (!window.confirm((produto.ativo ? 'Inativar' : 'Reativar') + ' o produto “' + produto.nome + '”?')) return
    setChanging(produto.id); setError(null); setNotice('')
    try {
      await catalogo.situacao(produto)
      setNotice(produto.ativo ? 'Produto inativado.' : 'Produto reativado.')
    } catch (err) { setError(err) } finally {
      void client.invalidateQueries({ queryKey: ['produtos'] }); void client.invalidateQueries({ queryKey: ['produto'] })
      setChanging(null)
    }
  }
  return <>
    <header className="page-heading"><div><span className="eyebrow">SEU CATÁLOGO</span><h1>Produtos</h1><p>Uma visão organizada de tudo que faz parte da sua rede.</p></div>{can('CADASTRAR') && <button className="button" onClick={() => { setNotice(''); setModal('novo') }}><Icon name="plus" size={18}/>Novo produto</button>}</header>
    <div className="catalog-banner"><span className="banner-icon"><Icon name="box" size={28}/></span><div><strong>Um cadastro, muitas possibilidades.</strong><p>Centralize as informações dos seus produtos e mantenha o catálogo sempre em dia.</p></div><span className="banner-label">CATÁLOGO CENTRAL</span></div>
    {notice && <div className="notice success" role="status">{notice}</div>}
    {error != null && <ErrorNotice error={error}/>}
    <section className="panel">
      <div className="panel-title"><h2>Todos os produtos</h2>{produtos.data && <span className="count">{produtos.data.total} registros</span>}</div>
      <form className="filters" onSubmit={search}>
        <label className="search-input"><span className="sr-only">Buscar produtos</span><Icon name="search" size={18}/><input value={buscaInput} maxLength={200} onChange={event => setBuscaInput(event.target.value)} placeholder="Busque por nome ou código de barras"/></label>
        <label><span className="sr-only">Filtrar categoria</span><select value={categoriaId} onChange={event => { setCategoria(event.target.value); setPagina(0) }}><option value="">Todas as categorias</option>{categorias.data?.map(c => <option key={c.id} value={c.id}>{c.nome}{c.ativa ? '' : ' (inativa)'}</option>)}</select></label>
        <label><span className="sr-only">Filtrar situação</span><select value={ativo} onChange={event => { setAtivo(event.target.value); setPagina(0) }}><option value="">Todas as situações</option><option value="true">Ativos</option><option value="false">Inativos</option></select></label>
        <button className="button secondary" type="submit">Buscar</button>
      </form>
      {(busca || categoriaId || ativo) && <button className="clear-filters" onClick={() => { setBusca(''); setBuscaInput(''); setCategoria(''); setAtivo(''); setPagina(0) }}>Limpar filtros</button>}
      {categorias.error && <ErrorNotice error={categorias.error}><button className="button small secondary" onClick={() => void categorias.refetch()}>Recarregar categorias</button></ErrorNotice>}
      {produtos.isPending ? <Loading label="Carregando produtos…"/> : produtos.error ? <ErrorNotice error={produtos.error}><button className="button small secondary" onClick={() => void produtos.refetch()}>Tentar novamente</button></ErrorNotice> : <>
        {!produtos.data.itens.length ? <Empty title={busca || categoriaId || ativo ? 'Nenhum produto encontrado' : 'Seu catálogo começa aqui'}><p>{busca || categoriaId || ativo ? 'Experimente ajustar a busca ou limpar os filtros.' : 'Cadastre o primeiro produto para começar a organizar sua rede.'}</p>{can('CADASTRAR') && !busca && !categoriaId && !ativo && <button className="button secondary" onClick={() => setModal('novo')}><Icon name="plus" size={16}/>Cadastrar primeiro produto</button>}</Empty> : <div className="table-scroll"><table><thead><tr><th>Produto</th><th>Código de barras</th><th>Categoria</th><th>Unidade</th><th>Situação</th><th className="align-right">Ações</th></tr></thead><tbody>{produtos.data.itens.map(produto => <tr key={produto.id}>
          <td><div className="product-cell">{produto.imagemUrl ? <img src={produto.imagemUrl + '?v=' + produto.versao} alt="" loading="lazy"/> : <span className="product-placeholder"><Icon name="box"/></span>}<div><button className="product-name" onClick={() => setModal(produto.id)}>{produto.nome}</button><span>{produto.marca || 'Sem marca informada'}</span></div></div></td>
          <td className="code">{produto.codigoBarras}</td><td>{categoryName(produto.categoriaId)}</td><td><span className="unit">{produto.unidadeMedida}</span></td><td><Badge active={produto.ativo}/></td>
          <td><div className="row-actions"><button className="icon-button" title={can('EDITAR') ? 'Editar produto' : 'Consultar produto'} aria-label={(can('EDITAR') ? 'Editar ' : 'Consultar ') + produto.nome} onClick={() => setModal(produto.id)}><Icon name="edit" size={17}/></button>{can('INATIVAR') && <button className="text-button" disabled={changing !== null} onClick={() => void situacao(produto)}>{changing === produto.id ? 'Aguarde…' : produto.ativo ? 'Inativar' : 'Reativar'}</button>}</div></td>
        </tr>)}</tbody></table></div>}
        <Pagination pagina={pagina} tamanho={20} total={produtos.data.total} onChange={setPagina}/>
      </>}
    </section>
    <p className="page-footnote">O catálogo é compartilhado somente com os usuários autorizados da sua organização.</p>
    {modal !== null && <ProdutoModal id={modal === 'novo' ? undefined : modal} onClose={() => setModal(null)} onSaved={setNotice}/>}
  </>
}
