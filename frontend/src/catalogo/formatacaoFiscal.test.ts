import { describe, expect, it } from 'vitest'
import { digitosCodigoFiscal, formatarCodigoFiscal } from './formatacaoFiscal'

describe('apresentação de códigos fiscais', () => {
  it.each([
    ['', ''], ['0', '0'], ['0901', '0901'], ['09012', '0901.2'],
    ['090121', '0901.21'], ['0901210', '0901.21.0'], ['09012100', '0901.21.00'],
  ])('formata NCM progressivamente: %s', (entrada, esperado) => {
    expect(formatarCodigoFiscal(entrada, 'ncm')).toBe(esperado)
  })
  it.each([
    ['', ''], ['0', '0'], ['01', '01'], ['010', '01.0'], ['01001', '01.001'],
    ['010010', '01.001.0'], ['0100100', '01.001.00'],
  ])('formata CEST progressivamente: %s', (entrada, esperado) => {
    expect(formatarCodigoFiscal(entrada, 'cest')).toBe(esperado)
  })
  it('aceita colagem formatada e preserva zeros à esquerda', () => {
    expect(digitosCodigoFiscal('0901.21.00', 'ncm')).toBe('09012100')
    expect(digitosCodigoFiscal('01.001.00', 'cest')).toBe('0100100')
    expect(formatarCodigoFiscal('01.001.00', 'cest')).toBe('01.001.00')
  })
  it('limita o número de dígitos e remove caracteres estranhos', () => {
    expect(digitosCodigoFiscal('NCM: 0901.21.001234', 'ncm')).toBe('09012100')
    expect(formatarCodigoFiscal('01.001.00123', 'cest')).toBe('01.001.00')
  })
})
