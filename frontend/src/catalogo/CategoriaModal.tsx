import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import type { Categoria } from '../shared/types'
import { ErrorNotice, Field, Modal } from '../shared/ui'
import { request } from '../shared/api'
import { catalogo } from './api'
const schema = z.object({ nome: z.string().trim().min(1, 'Informe o nome.').max(100, 'Use até 100 caracteres.'), proibidaParaMenores: z.boolean() })
export function CategoriaModal({ atual, onClose, onSaved }: { atual?: Categoria; onClose: () => void; onSaved: (categoria: Categoria) => void }) {
  const [error, setError] = useState<unknown>(null)
  const [current, setCurrent] = useState(atual)
  const [reloading, setReloading] = useState(false)
  const { register, handleSubmit, reset, formState: { errors, isDirty, isSubmitting } } = useForm({ resolver: zodResolver(schema), defaultValues: { nome: atual?.nome ?? '', proibidaParaMenores: !(atual?.permitidoParaMenores ?? true) } })
  const busy = isSubmitting || reloading
  const close = () => { if (!busy && (!isDirty || window.confirm('Descartar as alterações não salvas?'))) onClose() }
  return <Modal title={current ? 'Editar categoria' : 'Nova categoria'} onClose={close} busy={busy}><form onSubmit={handleSubmit(async data => {
    setError(null)
    try { const categoria = await catalogo.salvarCategoria(data.nome, !data.proibidaParaMenores, current); onSaved(categoria) } catch (err) { setError(err) }
  })} noValidate><div className="modal-body">
    {error != null && <ErrorNotice error={error}>{current && <button type="button" className="button small secondary" disabled={busy} onClick={async () => {
      if (!window.confirm('Recarregar a categoria e descartar o preenchimento?')) return
      setReloading(true)
      try {
        const latest = await request<Categoria>('/categorias/' + current.id)
        setCurrent(latest); reset({ nome: latest.nome, proibidaParaMenores: !(latest.permitidoParaMenores ?? true) }); setError(null)
      } catch (err) { setError(err) } finally { setReloading(false) }
    }}>Recarregar categoria</button>}</ErrorNotice>}
    <Field label="Nome da categoria *" error={errors.nome?.message} hint="O nome deve ser único na organização, inclusive entre categorias inativas."><input autoFocus maxLength={100} disabled={busy} {...register('nome')} aria-invalid={!!errors.nome}/></Field>
    <div className="check-list"><label><input type="checkbox" disabled={busy} {...register('proibidaParaMenores')}/>
      <span><strong>Proibida para menores de 18 anos</strong><small>Marque apenas quando a categoria não for permitida para menores de 18 anos. Desmarcada significa livre.</small></span>
    </label></div></div>
    <footer className="modal-footer"><span>* Campo obrigatório</span><div><button className="button secondary" type="button" disabled={busy} onClick={close}>Cancelar</button><button className="button" disabled={busy}>{busy ? 'Salvando…' : 'Salvar categoria'}</button></div></footer></form></Modal>
}
