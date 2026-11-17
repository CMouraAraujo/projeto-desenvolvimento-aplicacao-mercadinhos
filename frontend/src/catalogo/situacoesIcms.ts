export const situacoesIcms = [
  { codigo: '101', resumo: 'Simples Nacional com crédito', descricao: 'Tributada pelo Simples Nacional com permissão de crédito.' },
  { codigo: '102', resumo: 'Simples Nacional sem crédito', descricao: 'Tributada pelo Simples Nacional sem permissão de crédito.' },
  { codigo: '103', resumo: 'Isenção por faixa de receita', descricao: 'Isenção do ICMS no Simples Nacional para faixa de receita bruta.' },
  { codigo: '201', resumo: 'Com crédito e substituição tributária', descricao: 'Tributada pelo Simples Nacional com permissão de crédito e com cobrança do ICMS por substituição tributária.' },
  { codigo: '202', resumo: 'Sem crédito e substituição tributária', descricao: 'Tributada pelo Simples Nacional sem permissão de crédito e com cobrança do ICMS por substituição tributária.' },
  { codigo: '203', resumo: 'Isenção e substituição tributária', descricao: 'Isenção do ICMS no Simples Nacional para faixa de receita bruta e com cobrança do ICMS por substituição tributária.' },
  { codigo: '300', resumo: 'Imune', descricao: 'Imune.' },
  { codigo: '400', resumo: 'Não tributada pelo Simples Nacional', descricao: 'Não tributada pelo Simples Nacional.' },
  { codigo: '500', resumo: 'ICMS cobrado anteriormente por ST', descricao: 'ICMS cobrado anteriormente por substituição tributária (substituído) ou por antecipação.' },
  { codigo: '60', resumo: 'ICMS cobrado anteriormente por ST', descricao: 'ICMS cobrado anteriormente por substituição tributária.' },
] as const

export function camposSituacaoIcms(codigo: string) {
  return {
    icmsCst: codigo.length === 2 ? codigo : '',
    icmsCsosn: codigo.length === 3 ? codigo : '',
  }
}
