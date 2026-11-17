export type CodigoFiscal = 'ncm' | 'cest'
export const formatosFiscais = {
  ncm: { grupos: [4, 2, 2], tamanho: 8, exemplo: '0000.00.00' },
  cest: { grupos: [2, 3, 2], tamanho: 7, exemplo: '00.000.00' },
} as const

export function digitosCodigoFiscal(valor: string, tipo: CodigoFiscal) {
  return valor.replace(/\D/g, '').slice(0, formatosFiscais[tipo].tamanho)
}

export function formatarCodigoFiscal(valor: string, tipo: CodigoFiscal) {
  const digitos = digitosCodigoFiscal(valor, tipo)
  let inicio = 0
  return formatosFiscais[tipo].grupos.map(tamanho => {
    const grupo = digitos.slice(inicio, inicio + tamanho)
    inicio += tamanho
    return grupo
  }).filter(Boolean).join('.')
}
