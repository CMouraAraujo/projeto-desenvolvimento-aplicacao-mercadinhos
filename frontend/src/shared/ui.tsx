import { cloneElement, useEffect, useId, useRef, type ReactElement, type ReactNode } from 'react'
import { errorMessage } from './api'

export function Icon({ name, size = 20 }: { name: 'box' | 'grid' | 'search' | 'plus' | 'arrow' | 'logout' | 'close' | 'leaf' | 'edit' | 'image'; size?: number }) {
  const paths = {
    box: <><path d="m12 3 9 5v8l-9 5-9-5V8z"/><path d="m3 8 9 5 9-5M12 13v8M7.5 5.5l9 5"/></>,
    grid: <><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></>,
    search: <><circle cx="10.5" cy="10.5" r="7"/><path d="m16 16 5 5"/></>,
    plus: <path d="M12 5v14M5 12h14"/>,
    arrow: <path d="m9 5 7 7-7 7"/>,
    logout: <><path d="M9 4H4v16h5M10 12h11m-4-4 4 4-4 4"/></>,
    close: <path d="m6 6 12 12M6 18 18 6"/>,
    leaf: <><path d="M20 3C6 2 2 8 5 15c7 5 15-1 15-12Z"/><path d="M4 21 15 9"/></>,
    edit: <><path d="m15 4 5 5-11 11-6 1 1-6zM13 6l5 5"/></>,
    image: <><rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="8" cy="8" r="1"/><path d="m3 17 5-5 4 4 4-6 5 7"/></>,
  }
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
export function ErrorNotice({ error, children }: { error: unknown; children?: ReactNode }) {
  return <div className="notice error" role="alert">{errorMessage(error)}{children}</div>
}
export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return <div className="empty"><span className="empty-icon"><Icon name="box" size={30}/></span><h3>{title}</h3>{children}</div>
}
export function Loading({ label = 'Carregando…' }: { label?: string }) {
  return <div className="loading" role="status"><span className="spinner"/>{label}</div>
}
export function Badge({ active }: { active: boolean }) {
  return <span className={'badge ' + (active ? 'active' : 'inactive')}><span/>{active ? 'Ativo' : 'Inativo'}</span>
}
export function Pagination({ pagina, tamanho, total, onChange, onSizeChange, loading = false }: {
  pagina: number; tamanho: number; total: number; onChange: (page: number) => void
  onSizeChange: (size: number) => void; loading?: boolean
}) {
  const pages = Math.max(1, Math.ceil(total / tamanho))
  return <div className="pagination">
    <span role="status">{loading ? 'Carregando…' : (total ? pagina * tamanho + 1 : 0) + '–' + Math.min((pagina + 1) * tamanho, total) + ' de ' + total + ' registros'}</span>
    <div className="pagination-controls">
      <label className="pagination-size">Itens por página
        <select value={tamanho} onChange={event => onSizeChange(Number(event.target.value))}>
          {[10, 25, 50].map(size => <option key={size} value={size}>{size}</option>)}
        </select>
      </label>
      <nav className="pagination-pages" aria-label="Paginação da listagem">
        <button className="button small secondary" disabled={loading || pagina === 0} onClick={() => onChange(pagina - 1)}>Anterior</button>
        <span aria-live="polite">{loading ? '…' : pagina + 1 + ' / ' + pages}</span>
        <button className="button small secondary" disabled={loading || pagina + 1 >= pages} onClick={() => onChange(pagina + 1)}>Próxima</button>
      </nav>
    </div>
  </div>
}
export function Modal({ title, subtitle, children, onClose, busy = false, className = '' }: { title: string; subtitle?: string; children: ReactNode; onClose: () => void; busy?: boolean; className?: string }) {
  const titleId = useId()
  const subtitleId = useId()
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current!
    const previous = document.activeElement as HTMLElement | null
    dialog.showModal()
    dialog.querySelector<HTMLElement>('input:not([disabled]), select:not([disabled]), textarea:not([disabled])')?.focus()
    return () => { dialog.close(); previous?.focus() }
  }, [])
  return <dialog className={'modal ' + className} ref={ref} aria-labelledby={titleId} aria-describedby={subtitle ? subtitleId : undefined} onCancel={event => { event.preventDefault(); event.stopPropagation(); if (!busy) onClose() }}>
    <header className="modal-header"><div><span className="eyebrow">CATÁLOGO</span><h2 id={titleId}>{title}</h2>{subtitle && <p id={subtitleId}>{subtitle}</p>}</div><button type="button" className="icon-button" aria-label="Fechar" disabled={busy} onClick={onClose}><Icon name="close"/></button></header>{children}
  </dialog>
}
export function Field({ label, error, children, hint, required = false }: { label: string; error?: string; children: ReactElement; hint?: string; required?: boolean }) {
  const id = useId()
  const descriptions = [error ? id + '-error' : '', hint ? id + '-hint' : ''].filter(Boolean).join(' ')
  return <div className={'field' + (error ? ' invalid' : '')}><label htmlFor={id}>{label}{required && <span className="required-indicator" aria-hidden="true"> *</span>}</label>{cloneElement(children as ReactElement<{ id: string; 'aria-describedby'?: string; 'aria-invalid'?: boolean; required?: boolean; 'aria-required'?: boolean }>, {
    id, 'aria-describedby': descriptions || undefined, 'aria-invalid': !!error,
    ...(required ? { required: true, 'aria-required': true } : {}),
  })}{error && <small id={id + '-error'} role="alert">{error}</small>}{hint && <small id={id + '-hint'} className="hint">{hint}</small>}</div>
}
