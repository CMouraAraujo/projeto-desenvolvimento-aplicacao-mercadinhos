import { forwardRef, type InputHTMLAttributes } from 'react'
import { digitosCodigoFiscal, formatarCodigoFiscal, formatosFiscais, type CodigoFiscal } from './formatacaoFiscal'

type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange'> & {
  tipo: CodigoFiscal
  value: string
  onChange: (value: string) => void
}

// O formulário mantém somente dígitos; a máscara pertence à apresentação.
export const CodigoFiscalInput = forwardRef<HTMLInputElement, Props>(function CodigoFiscalInput(
  { tipo, value, onChange, ...props }, ref,
) {
  return <input {...props} ref={ref} inputMode="numeric" placeholder={formatosFiscais[tipo].exemplo}
    maxLength={formatosFiscais[tipo].exemplo.length} value={formatarCodigoFiscal(value, tipo)}
    onKeyDown={event => {
      const input = event.currentTarget
      const cursor = input.selectionStart
      if (cursor === null || cursor !== input.selectionEnd) return
      // Ao apagar junto a um ponto, remove o dígito vizinho, sem prender o cursor na máscara.
      if (event.key === 'Backspace' && input.value[cursor - 1] === '.') input.setSelectionRange(cursor - 1, cursor - 1)
      if (event.key === 'Delete' && input.value[cursor] === '.') input.setSelectionRange(cursor + 1, cursor + 1)
    }}
    onChange={event => {
      const input = event.currentTarget
      const antesDoCursor = input.value.slice(0, input.selectionStart ?? input.value.length)
      const digitosAntes = digitosCodigoFiscal(antesDoCursor, tipo).length
      const digitos = digitosCodigoFiscal(input.value, tipo)
      const formatado = formatarCodigoFiscal(digitos, tipo)
      onChange(digitos)
      requestAnimationFrame(() => {
        if (document.activeElement !== input) return
        let cursor = 0
        let encontrados = 0
        while (cursor < formatado.length && encontrados < digitosAntes) {
          if (formatado[cursor] !== '.') encontrados++
          cursor++
        }
        input.setSelectionRange(cursor, cursor)
      })
    }}
  />
})
