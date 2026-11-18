import { describe, expect, it } from 'vitest'
import { camposSituacaoIcms, situacoesIcms } from './situacoesIcms'
import { dadosProduto, produtoSchema, valoresProduto } from './produtoSchema'

describe('situação tributária de ICMS', () => {
  it.each(situacoesIcms)('envia $codigo somente no campo correspondente da API', ({ codigo }) => {
    const form = valoresProduto()
    Object.assign(form, { nome: 'Produto', codigoBarras: '0001', categoriaId: '1', valorCompra: '10,00', percentualMarkup: '50' })
    Object.assign(form.dadosFiscais, { ncm: '09012100', cfopPadrao: '5102' }, camposSituacaoIcms(codigo))
    const result = dadosProduto(produtoSchema.parse(form)).dadosFiscais!
    expect(result.icmsCst).toBe(codigo === '60' ? '60' : null)
    expect(result.icmsCsosn).toBe(codigo === '60' ? null : codigo)
  })
  it('limpa o código anterior ao trocar CST por CSOSN ou remover a seleção', () => {
    expect(camposSituacaoIcms('500')).toEqual({ icmsCst: '', icmsCsosn: '500' })
    expect(camposSituacaoIcms('60')).toEqual({ icmsCst: '60', icmsCsosn: '' })
    expect(camposSituacaoIcms('')).toEqual({ icmsCst: '', icmsCsosn: '' })
  })
})
