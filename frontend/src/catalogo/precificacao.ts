// Cálculo em inteiros evita diferenças de ponto flutuante em relação ao BigDecimal do backend.
export function centesimos(valor: string): bigint | null {
  if (!/^\d{1,9}([.,]\d{1,2})?$/.test(valor)) return null
  const [inteiro, fracao = ''] = valor.replace(',', '.').split('.')
  return BigInt(inteiro) * 100n + BigInt(fracao.padEnd(2, '0'))
}
export function precoSugerido(compra: string, percentual: string): string | null {
  const custo = centesimos(compra), acrescimo = centesimos(percentual)
  if (custo === null || custo <= 0n || custo > 99999999999n || acrescimo === null || acrescimo > 1000000n) return null
  const preco = (custo * (10000n + acrescimo) + 5000n) / 10000n
  if (preco > 99999999999n) return null
  return (preco / 100n).toString() + ',' + (preco % 100n).toString().padStart(2, '0')
}
