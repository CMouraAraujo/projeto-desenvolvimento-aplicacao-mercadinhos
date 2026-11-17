import { useState } from 'react'
import { useForm, type FieldPath } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/Auth'
import { ApiError } from '../shared/api'
import type { Produto } from '../shared/types'
import { ErrorNotice, Field, Icon, Loading, Modal } from '../shared/ui'
import { catalogo } from './api'
import { dadosProduto, fiscalFields, produtoSchema, validarImagem, valoresProduto, type ProdutoForm } from './produtoSchema'

type Props = { id?: number; onClose: () => void; onSaved: (message: string) => void }
export function ProdutoModal(props: Props) {
  const detail = useQuery({ queryKey: ['produto', props.id], queryFn: ({ signal }) => catalogo.produto(props.id!, signal), enabled: props.id !== undefined, staleTime: 0 })
  const categorias = useQuery({ queryKey: ['categorias', 'seletor'], queryFn: ({ signal }) => catalogo.todasCategorias(signal) })
  const opcoes = useQuery({ queryKey: ['opcoes'], queryFn: ({ signal }) => catalogo.opcoes(signal) })
  if ((props.id && detail.isPending) || categorias.isPending || opcoes.isPending) return <Modal title="Produto" onClose={props.onClose}><Loading/></Modal>
  const error = detail.error || categorias.error || opcoes.error
  if (error) return <Modal title="Produto" onClose={props.onClose}><div className="modal-body"><ErrorNotice error={error}/><button className="button secondary" onClick={() => { void detail.refetch(); void categorias.refetch(); void opcoes.refetch() }}>Tentar novamente</button></div></Modal>
  return <ProdutoEditor {...props} initial={detail.data} categorias={categorias.data!} opcoes={opcoes.data!}/>
}
function ProdutoEditor({ initial, categorias, opcoes, onClose, onSaved }: Props & {
  initial?: Produto; categorias: Awaited<ReturnType<typeof catalogo.todasCategorias>>; opcoes: Awaited<ReturnType<typeof catalogo.opcoes>>
}) {
  const { can } = useAuth()
  const client = useQueryClient()
  const [current, setCurrent] = useState(initial)
  const readOnly = initial ? !can('EDITAR') : !can('CADASTRAR')
  const [tab, setTab] = useState<'geral' | 'operacional' | 'fiscal'>('geral')
  const [error, setError] = useState<unknown>(null)
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [imagePending, setImagePending] = useState(false)
  const [busyImage, setBusyImage] = useState(false)
  const { register, handleSubmit, formState: { errors, isDirty, isSubmitting }, reset, setError: fieldError } = useForm<ProdutoForm>({
    resolver: zodResolver(produtoSchema), defaultValues: valoresProduto(initial), shouldFocusError: false,
  })
  const busy = isSubmitting || busyImage
  const invalidate = () => {
    void client.invalidateQueries({ queryKey: ['produtos'] })
    void client.invalidateQueries({ queryKey: ['produto'] })
  }
  const close = () => {
    if (busy) return
    if (!readOnly && (isDirty || arquivo) && !window.confirm(imagePending ? 'O produto foi salvo, mas a imagem está pendente. Sair sem enviar a imagem?' : 'Descartar as alterações não salvas?')) return
    onClose()
  }
  async function save(form: ProdutoForm) {
    setError(null)
    try {
      let saved = imagePending && current ? current : await catalogo.salvar(dadosProduto(form), current)
      setCurrent(saved)
      reset(valoresProduto(saved))
      invalidate()
      if (arquivo) {
        setImagePending(true)
        try { saved = await catalogo.imagem(saved, arquivo) } catch (err) {
          setError(err)
          return
        }
        setCurrent(saved); setArquivo(null); setImagePending(false); invalidate()
      }
      onSaved('Produto salvo com sucesso.')
      onClose()
    } catch (err) {
      setError(err)
      if (err instanceof ApiError) for (const campo of err.campos) {
        const path = campo.replace(/^dados\./, '')
        if (path in valoresProduto() || path.startsWith('dadosFiscais.')) {
          fieldError(path as FieldPath<ProdutoForm>, { message: 'Confira este campo.' })
          setTab(path.startsWith('dadosFiscais') ? 'fiscal' : 'geral')
        }
      }
    }
  }
  return <Modal title={readOnly ? 'Detalhes do produto' : current ? 'Editar produto' : 'Novo produto'} subtitle="Informações do catálogo da sua organização." onClose={close} busy={busy}>
    <form onSubmit={handleSubmit(save, invalid => {
      setTab(invalid.nome || invalid.codigoBarras || invalid.categoriaId || invalid.marca || invalid.descricao ? 'geral' : invalid.dadosFiscais ? 'fiscal' : 'operacional')
    })} noValidate>
      <div className="tabs" role="tablist" aria-label="Dados do produto">{(['geral', 'operacional', 'fiscal'] as const).map((value, index) => <button type="button" role="tab" id={'tab-' + value} aria-controls={'panel-' + value} aria-selected={tab === value} tabIndex={tab === value ? 0 : -1} key={value} className={tab === value ? 'selected' : ''} onClick={() => setTab(value)} onKeyDown={event => {
        if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
          event.preventDefault()
          const next = (index + (event.key === 'ArrowRight' ? 1 : 2)) % 3
          const value = (['geral', 'operacional', 'fiscal'] as const)[next]
          setTab(value); document.getElementById('tab-' + value)?.focus()
        }
      }}><span>0{index + 1}</span>{value === 'geral' ? 'Geral' : value === 'operacional' ? 'Operacional' : 'Fiscal'}</button>)}</div>
      <div className="modal-body">
        {imagePending && <div className="notice" role="status">Produto salvo. A imagem está pendente: tente o envio novamente ou feche para continuar sem ela.</div>}
        {error != null && <ErrorNotice error={error}>{error instanceof ApiError && error.status === 409 && current && <button type="button" className="button small secondary" disabled={busy} onClick={async () => {
          if (!window.confirm('Recarregar os dados atuais e descartar o preenchimento deste formulário?')) return
          setBusyImage(true)
          try {
            const latest = await catalogo.produto(current.id)
            setCurrent(latest); reset(valoresProduto(latest)); setImagePending(false); setError(null); invalidate()
          } catch (err) { setError(err) } finally { setBusyImage(false) }
        }}>Recarregar dados atuais</button>}</ErrorNotice>}
        <fieldset disabled={readOnly || busy || imagePending} className="form-fields">
          <section role="tabpanel" id="panel-geral" aria-labelledby="tab-geral" hidden={tab !== 'geral'}>
            <div className="section-heading"><h3>Identificação</h3><p>O essencial para encontrar e organizar seu produto.</p></div>
            <div className="form-grid">
              <Field label="Nome do produto *" error={errors.nome?.message}><input maxLength={200} placeholder="Ex.: Café torrado 500 g" {...register('nome')} aria-invalid={!!errors.nome}/></Field>
              <Field label="Código de barras *" error={errors.codigoBarras?.message} hint="Somente números. Os zeros à esquerda serão mantidos."><input inputMode="numeric" maxLength={64} {...register('codigoBarras')} aria-invalid={!!errors.codigoBarras}/></Field>
              <Field label="Categoria *" error={errors.categoriaId?.message}><select {...register('categoriaId')} aria-invalid={!!errors.categoriaId}><option value="">Selecione uma categoria</option>{categorias.filter(c => c.ativa || c.id === current?.categoriaId).map(c => <option key={c.id} value={c.id}>{c.nome}{c.ativa ? '' : ' (inativa)'}</option>)}</select></Field>
              <Field label="Marca" error={errors.marca?.message}><input maxLength={100} placeholder="Opcional" {...register('marca')}/></Field>
              <div className="span-2"><Field label="Descrição" error={errors.descricao?.message}><textarea rows={3} maxLength={4000} placeholder="Informações adicionais sobre o produto" {...register('descricao')}/></Field></div>
            </div>
            {!categorias.some(c => c.ativa) && !current && <div className="notice">Cadastre uma categoria ativa antes de criar um produto. A manutenção de categorias é exclusiva do administrador.</div>}
          </section>
          <section role="tabpanel" id="panel-operacional" aria-labelledby="tab-operacional" hidden={tab !== 'operacional'}>
            <div className="section-heading"><h3>Como o produto é tratado</h3><p>Estas opções preparam o cadastro para os próximos módulos.</p></div>
            <Field label="Unidade de medida *" error={errors.unidadeMedida?.message}><select {...register('unidadeMedida')}>{opcoes.unidadesMedida.map(u => <option key={u} value={u}>{u}</option>)}</select></Field>
            <div className="check-list">
              <label><input type="checkbox" {...register('permiteVendaFracionada')}/><span><strong>Permite venda fracionada</strong><small>Indica se o produto pode ser vendido em frações.</small></span></label>
              <label><input type="checkbox" {...register('controlaLote')}/><span><strong>Controla lote</strong><small>O registro dos lotes será feito em uma etapa futura.</small></span></label>
              <label><input type="checkbox" {...register('controlaValidade')}/><span><strong>Controla validade</strong><small>As datas de validade serão registradas por lote, futuramente.</small></span></label>
            </div>
          </section>
          <section role="tabpanel" id="panel-fiscal" aria-labelledby="tab-fiscal" hidden={tab !== 'fiscal'}>
            <div className="section-heading"><h3>Classificação fiscal</h3><p>Preenchimento opcional. Valide o enquadramento com sua contabilidade.</p></div>
            <div className="notice">Estes dados não realizam cálculos tributários nem emissão de documentos fiscais.</div>
            <div className="form-grid">{fiscalFields.map(([key, label, length]) => <Field key={key} label={label} error={errors.dadosFiscais?.[key]?.message}><input inputMode={key === 'icmsAliquota' || key === 'icmsReducaoBase' ? 'decimal' : 'numeric'} maxLength={length} {...register(`dadosFiscais.${key}` as const)}/></Field>)}</div>
          </section>
        </fieldset>
        {tab === 'geral' && <section className="image-section"><div><h3>Imagem do produto</h3><p>PNG ou JPEG · até {Math.round(opcoes.imagemMaxBytes / 1024 / 1024)} MiB</p></div>
          {current?.imagemUrl && <img className="image-preview" src={current.imagemUrl + '?v=' + current.versao} alt={current.nome}/>}
          {!readOnly && can('EDITAR') && <><label className="upload"><Icon name="image"/><span>{arquivo ? arquivo.name : 'Selecionar imagem'}</span><input aria-label="Selecionar imagem" type="file" accept={opcoes.formatosImagem.join(',')} disabled={busy || imagePending} onChange={async event => {
            const file = event.target.files?.[0]
            event.target.value = ''
            if (!file) return
            setBusyImage(true)
            try { await validarImagem(file, opcoes.imagemMaxBytes, opcoes.formatosImagem); setArquivo(file); setError(null) } catch (err) { setError(err) } finally { setBusyImage(false) }
          }}/></label>
          {arquivo && !imagePending && <button className="button small secondary" type="button" disabled={busy} onClick={() => setArquivo(null)}>Cancelar seleção</button>}
          {current?.imagemUrl && !arquivo && <button className="button small danger" type="button" disabled={busy} onClick={async () => {
            if (!window.confirm('Remover a imagem deste produto?')) return
            setBusyImage(true); setError(null)
            try { const saved = await catalogo.removerImagem(current); setCurrent(saved); invalidate() } catch (err) { setError(err) } finally { setBusyImage(false) }
          }}>Remover imagem</button>}</>}
          {!current?.imagemUrl && readOnly && <span className="muted">Sem imagem cadastrada.</span>}
          {!readOnly && !can('EDITAR') && <p className="muted">O envio de imagem exige a permissão Editar produtos.</p>}
        </section>}
        {current && <p className="audit">ID #{current.id} · Atualizado em {new Date(current.atualizadoEm).toLocaleString('pt-BR')} · Usuário #{current.atualizadoPor}</p>}
      </div>
      <footer className="modal-footer"><span>{readOnly ? 'Modo de consulta' : '* Campos obrigatórios'}</span><div><button type="button" className="button secondary" disabled={busy} onClick={close}>{readOnly ? 'Fechar' : 'Cancelar'}</button>{!readOnly && <button type="submit" className="button" disabled={busy}>{busy ? 'Salvando…' : imagePending ? 'Tentar enviar imagem' : 'Salvar produto'}</button>}</div></footer>
    </form>
  </Modal>
}
