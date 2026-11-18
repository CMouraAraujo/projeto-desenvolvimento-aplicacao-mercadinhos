import { describe, expect, it } from 'vitest'
import { dadosProduto, produtoSchema, valoresProduto } from './produtoSchema'
import { pode, type Produto, type Usuario } from '../shared/types'

const valid = () => {
  const form = { ...valoresProduto(), nome: 'Café', codigoBarras: '00012345', categoriaId: '3' }
  Object.assign(form.dadosFiscais, { ncm: '09012100', cfopPadrao: '5102', icmsCsosn: '102' })
  return form
}
describe('contrato do produto', () => {
  it('preserva zeros e converte campos fiscais vazios para null', () => {
    const result = dadosProduto(produtoSchema.parse(valid()))
    expect(result.codigoBarras).toBe('00012345')
    expect(result.categoriaId).toBe(3)
    expect(result.dadosFiscais).toMatchObject({ ncm: '09012100', cfopPadrao: '5102', icmsCsosn: '102', cest: null, pisCst: null })
    expect(result).not.toHaveProperty('organizacaoId')
  })
  it('aceita decimal com vírgula e preserva códigos fiscais', () => {
    const form = valid()
    form.dadosFiscais.icmsAliquota = '17,1234'
    form.dadosFiscais.pisCst = '01'
    expect(dadosProduto(produtoSchema.parse(form)).dadosFiscais).toMatchObject({ icmsAliquota: 17.1234, pisCst: '01' })
  })
  it.each(['-1', '100,0001', '101', '0.12345', 'NaN'])('rejeita percentual inválido: %s', valor => {
    const form = valid()
    form.dadosFiscais.icmsAliquota = valor
    expect(produtoSchema.safeParse(form).success).toBe(false)
  })
  it('rejeita CST e CSOSN simultâneos', () => {
    const form = valid()
    form.dadosFiscais.icmsCst = '00'
    form.dadosFiscais.icmsCsosn = '102'
    expect(produtoSchema.safeParse(form).success).toBe(false)
  })
  it('mantém dados opcionais ao carregar edição e exclui campos de controle ao enviar', () => {
    const produto = { ...dadosProduto(produtoSchema.parse(valid())), id: 1, ativo: false, versao: 9, marca: 'Marca', descricao: 'Descrição', controlaLote: true, dadosFiscais: { ncm: '09012100' } } as Produto
    const sent = dadosProduto(valoresProduto(produto))
    expect(sent).toMatchObject({ marca: 'Marca', descricao: 'Descrição', controlaLote: true, dadosFiscais: { ncm: '09012100' } })
    expect(sent).not.toHaveProperty('id')
    expect(sent).not.toHaveProperty('versao')
    expect(sent).not.toHaveProperty('ativo')
  })
  it.each(['', 'abc', '123-45', '1'.repeat(65)])('rejeita código inválido: %s', codigoBarras => {
    expect(produtoSchema.safeParse({ ...valid(), codigoBarras }).success).toBe(false)
  })
})
describe('permissões de interface', () => {
  const funcionario = { papel: 'FUNCIONARIO' } as Usuario
  it('nega ações não concedidas', () => {
    expect(pode(funcionario, [], 'CONSULTAR')).toBe(false)
    expect(pode(funcionario, [{ modulo: 'PRODUTOS', acao: 'CONSULTAR' }], 'EDITAR')).toBe(false)
  })
  it('concede ações conhecidas ao administrador', () => {
    expect(pode({ papel: 'ADMIN' } as Usuario, [], 'EDITAR')).toBe(true)
  })
})

describe('campos obrigatórios do formulário de produto', () => {
  it.each(['nome', 'categoriaId'] as const)('rejeita %s vazio sem preencher valores por padrão', campo => {
    const form = valid()
    form[campo] = ''
    const result = produtoSchema.safeParse(form)
    expect(result.success).toBe(false)
    if (!result.success) expect(result.error.issues.some(issue => issue.path.join('.') === campo)).toBe(true)
  })
  it.each(['ncm', 'cfopPadrao'] as const)('rejeita %s ausente', campo => {
    const form = valid()
    form.dadosFiscais[campo] = ''
    const result = produtoSchema.safeParse(form)
    expect(result.success).toBe(false)
    if (!result.success) expect(result.error.issues.some(issue => issue.path.join('.') === 'dadosFiscais.' + campo)).toBe(true)
  })
  it('exige ICMS e aceita tanto CST quanto CSOSN', () => {
    const form = valid()
    form.dadosFiscais.icmsCsosn = ''
    expect(produtoSchema.safeParse(form).success).toBe(false)
    form.dadosFiscais.icmsCst = '60'
    expect(produtoSchema.safeParse(form).success).toBe(true)
    form.dadosFiscais.icmsCst = ''
    form.dadosFiscais.icmsCsosn = '102'
    expect(produtoSchema.safeParse(form).success).toBe(true)
  })
})
