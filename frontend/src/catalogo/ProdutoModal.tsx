import { useEffect, useState } from 'react'
import { Controller, useForm, type FieldPath } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/Auth'
import { ApiError } from '../shared/api'
import type { Categoria, Produto } from '../shared/types'
import { ErrorNotice, Field, Icon, Loading, Modal } from '../shared/ui'
import { catalogo } from './api'
import { CategoriaModal } from './CategoriaModal'
import { CodigoFiscalInput } from './CodigoFiscalInput'
import { camposSituacaoIcms, situacoesIcms } from './situacoesIcms'
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
  const { can, usuario } = useAuth()
  const client = useQueryClient()
  const [current, setCurrent] = useState(initial)
  const [novaCategoria, setNovaCategoria] = useState(false)
  const [categoriaCriada, setCategoriaCriada] = useState('')
  const [categoriaParaSelecionar, setCategoriaParaSelecionar] = useState<number | null>(null)
  const readOnly = initial ? !can('EDITAR') : !can('CADASTRAR')
  const [error, setError] = useState<unknown>(null)
  const [arquivo, setArquivo] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [imagePending, setImagePending] = useState(false)
  const [busyImage, setBusyImage] = useState(false)
  const { register, control, handleSubmit, formState: { errors, isDirty, isSubmitting }, reset, setValue, watch, trigger, setError: fieldError } = useForm<ProdutoForm>({
    resolver: zodResolver(produtoSchema), defaultValues: valoresProduto(initial), shouldFocusError: true,
  })
  useEffect(() => {
    if (categoriaParaSelecionar !== null && categorias.some(c => c.id === categoriaParaSelecionar)) {
      setValue('categoriaId', String(categoriaParaSelecionar), { shouldDirty: true, shouldValidate: true })
      setCategoriaParaSelecionar(null)
    }
  }, [categoriaParaSelecionar, categorias, setValue])
  useEffect(() => {
    if (!arquivo) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(arquivo)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [arquivo])
  const icmsCst = watch('dadosFiscais.icmsCst')
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
          fieldError(path as FieldPath<ProdutoForm>, { message: 'Confira este campo.' }, { shouldFocus: true })
        }
      }
    }
  }
  return <Modal className="product-modal" title={readOnly ? 'Detalhes do produto' : current ? 'Editar produto' : 'Novo produto'} subtitle="Informações do catálogo da sua organização." onClose={close} busy={busy}>
    <form onSubmit={handleSubmit(save)} noValidate>
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
          <section className="product-section" aria-labelledby="produto-geral">
            <div className="section-heading"><span className="eyebrow">01 · IDENTIFICAÇÃO</span><h3 id="produto-geral">Geral</h3><p>O essencial para encontrar e organizar seu produto.</p></div>
            <div className="form-grid">
              <Field label="Nome do produto" required error={errors.nome?.message}><input maxLength={200} placeholder="Ex.: Café torrado 500 g" {...register('nome')} aria-invalid={!!errors.nome}/></Field>
              <Field label="Código de barras" required error={errors.codigoBarras?.message} hint="Somente números. Os zeros à esquerda serão mantidos."><input inputMode="numeric" maxLength={64} {...register('codigoBarras')} aria-invalid={!!errors.codigoBarras}/></Field>
              <div className="category-picker">
                <Field label="Categoria" required error={errors.categoriaId?.message}><select {...register('categoriaId')} aria-invalid={!!errors.categoriaId}><option value="">Selecione uma categoria</option>{categorias.filter(c => c.ativa || c.id === current?.categoriaId).map(c => <option key={c.id} value={c.id}>{c.nome}{c.ativa ? '' : ' (inativa)'}</option>)}</select></Field>
                {!readOnly && usuario.papel === 'ADMIN' && <button type="button" className="button small secondary" onClick={() => setNovaCategoria(true)}><Icon name="plus" size={15}/>Nova categoria</button>}
                {categoriaCriada && <small className="category-feedback" role="status">Categoria “{categoriaCriada}” criada e selecionada.</small>}
              </div>
              <Field label="Marca" error={errors.marca?.message}><input maxLength={100} placeholder="Opcional" {...register('marca')}/></Field>
              <div className="span-2"><Field label="Descrição" error={errors.descricao?.message}><textarea rows={2} maxLength={4000} placeholder="Informações adicionais sobre o produto" {...register('descricao')}/></Field></div>
            </div>
            {!categorias.some(c => c.ativa) && !current && <div className="notice">Cadastre uma categoria ativa antes de criar um produto. A manutenção de categorias é exclusiva do administrador.</div>}
            <div className="image-section"><div><h3>Imagem do produto</h3><p>PNG ou JPEG · até {Math.round(opcoes.imagemMaxBytes / 1024 / 1024)} MiB</p></div>
              {(previewUrl || current?.imagemUrl) && <img className="image-preview"
                src={previewUrl ?? current?.imagemUrl ?? undefined}
                alt={previewUrl ? 'Prévia da imagem selecionada' : current!.nome}/>}
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
            </div>
          </section>
          <section className="product-section" aria-labelledby="produto-operacional">
            <div className="section-heading"><span className="eyebrow">02 · CONFIGURAÇÃO</span><h3 id="produto-operacional">Operacional</h3><p>Estas opções preparam o cadastro para os próximos módulos.</p></div>
            <Field label="Unidade de medida" required error={errors.unidadeMedida?.message}><select {...register('unidadeMedida')}>{opcoes.unidadesMedida.map(u => <option key={u} value={u}>{u}</option>)}</select></Field>
            <div className="check-list">
              <label><input type="checkbox" {...register('permiteVendaFracionada')}/><span><strong>Permite venda fracionada</strong><small>Indica se o produto pode ser vendido em frações.</small></span></label>
              <label><input type="checkbox" {...register('controlaLote')}/><span><strong>Controla lote</strong><small>O registro dos lotes será feito em uma etapa futura.</small></span></label>
              <label><input type="checkbox" {...register('controlaValidade')}/><span><strong>Controla validade</strong><small>As datas de validade serão registradas por lote, futuramente.</small></span></label>
            </div>
          </section>
          <section className="product-section" aria-labelledby="produto-fiscal">
            <div className="section-heading"><span className="eyebrow">03 · CLASSIFICAÇÃO</span><h3 id="produto-fiscal">Fiscal</h3><p>NCM, CFOP e situação tributária de ICMS são obrigatórios. Valide o enquadramento com sua contabilidade.</p></div>
            <div className="notice">Estes dados não realizam cálculos tributários nem emissão de documentos fiscais.</div>
            <div className="form-grid">{fiscalFields.map(([key, label, length]) =>
              key === 'icmsCsosn' ? null
                : key === 'icmsCst'
                  ? <Controller key={key} name="dadosFiscais.icmsCsosn" control={control} render={({ field, fieldState }) => {
                      const codigo = field.value || icmsCst
                      const conhecida = situacoesIcms.find(s => s.codigo === codigo)
                      return <Field label="Situação tributária de ICMS" required error={fieldState.error?.message || errors.dadosFiscais?.icmsCst?.message}>
                        <select {...field} value={codigo} title={conhecida?.descricao} onChange={event => {
                          const campos = camposSituacaoIcms(event.target.value)
                          setValue('dadosFiscais.icmsCst', campos.icmsCst, { shouldDirty: true })
                          field.onChange(campos.icmsCsosn)
                          void trigger(['dadosFiscais.icmsCst', 'dadosFiscais.icmsCsosn'])
                        }}>
                          <option value="">Selecione a situação tributária</option>
                          {codigo && !conhecida && <option value={codigo}>{codigo} – Código já cadastrado</option>}
                          {situacoesIcms.map(s => <option key={s.codigo} value={s.codigo} title={s.descricao}>{s.codigo} – {s.resumo}</option>)}
                        </select>
                      </Field>
                    }}/>
                  : key === 'ncm' || key === 'cest'
                ? <Controller key={key} name={`dadosFiscais.${key}`} control={control} render={({ field, fieldState }) =>
                    <Field label={label} required={key === 'ncm'} error={fieldState.error?.message}><CodigoFiscalInput {...field} tipo={key}/></Field>
                  }/>
                : <Field key={key} label={label} required={key === 'cfopPadrao'} error={errors.dadosFiscais?.[key]?.message}><input inputMode={key === 'icmsAliquota' || key === 'icmsReducaoBase' ? 'decimal' : 'numeric'} maxLength={length} {...register(`dadosFiscais.${key}` as const)}/></Field>,
            )}</div>
          </section>
        </fieldset>
        {current && <p className="audit">ID #{current.id} · Atualizado em {new Date(current.atualizadoEm).toLocaleString('pt-BR')} · Usuário #{current.atualizadoPor}</p>}
      </div>
      <footer className="modal-footer"><span>{readOnly ? 'Modo de consulta' : <><span className="required-indicator" aria-hidden="true">*</span> Campos obrigatórios</>}</span><div><button type="button" className="button secondary" disabled={busy} onClick={close}>{readOnly ? 'Fechar' : 'Cancelar'}</button>{!readOnly && <button type="submit" className="button" disabled={busy}>{busy ? 'Salvando…' : imagePending ? 'Tentar enviar imagem' : 'Salvar produto'}</button>}</div></footer>
    </form>
    {novaCategoria && <CategoriaModal onClose={() => setNovaCategoria(false)} onSaved={categoria => {
      client.setQueryData<Categoria[]>(['categorias', 'seletor'], atuais =>
        [...(atuais ?? []).filter(c => c.id !== categoria.id), categoria].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')),
      )
      void client.invalidateQueries({ queryKey: ['categorias', 'lista'] })
      setCategoriaParaSelecionar(categoria.id)
      setCategoriaCriada(categoria.nome)
      setNovaCategoria(false)
    }}/>}
  </Modal>
}
