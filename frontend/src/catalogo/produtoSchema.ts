import { z } from 'zod'
import type { DadosFiscais, DadosProduto, Produto } from '../shared/types'

const codigo = (digits: number, label: string) => z.string().refine(v => v === '' || new RegExp('^[0-9]{' + digits + '}$').test(v), label + ': informe ' + digits + ' dígitos.')
const percentual = z.string().refine(v => v === '' || (/^\d{1,3}([.,]\d{1,4})?$/.test(v) && Number(v.replace(',', '.')) <= 100), 'Informe de 0 a 100, com até 4 casas decimais.')
export const fiscalFields = [
  ['ncm', 'NCM', 8], ['cest', 'CEST', 7], ['cfopPadrao', 'CFOP padrão', 4],
  ['pisCst', 'CST de PIS', 2], ['cofinsCst', 'CST de COFINS', 2], ['origem', 'Origem (0 a 8)', 1],
  ['icmsCst', 'CST de ICMS', 2], ['icmsCsosn', 'CSOSN de ICMS', 3], ['icmsModalidadeBaseCalculo', 'Modalidade da base de ICMS', 2],
  ['icmsAliquota', 'Alíquota de ICMS (%)', 8], ['icmsReducaoBase', 'Redução da base de ICMS (%)', 8],
] as const
export const produtoSchema = z.object({
  nome: z.string().trim().min(1, 'Informe o nome.').max(200, 'Use até 200 caracteres.'),
  codigoBarras: z.string().regex(/^[0-9]{1,64}$/, 'Informe de 1 a 64 dígitos.'),
  categoriaId: z.string().regex(/^[1-9][0-9]*$/, 'Selecione uma categoria.'),
  unidadeMedida: z.enum(['UN', 'KG', 'G', 'L', 'ML', 'CX', 'PCT']),
  descricao: z.string().max(4000), marca: z.string().max(100),
  permiteVendaFracionada: z.boolean(), controlaLote: z.boolean(), controlaValidade: z.boolean(),
  dadosFiscais: z.object({
    ncm: codigo(8, 'NCM'), cest: codigo(7, 'CEST'), cfopPadrao: codigo(4, 'CFOP'),
    pisCst: codigo(2, 'PIS'), cofinsCst: codigo(2, 'COFINS'),
    origem: z.string().regex(/^$|^[0-8]$/, 'Informe de 0 a 8.'),
    icmsCst: codigo(2, 'CST'), icmsCsosn: codigo(3, 'CSOSN'),
    icmsModalidadeBaseCalculo: z.string().regex(/^$|^[0-9]{1,2}$/, 'Informe 1 ou 2 dígitos.'),
    icmsAliquota: percentual, icmsReducaoBase: percentual,
  }).refine(v => !(v.icmsCst && v.icmsCsosn), { message: 'Informe CST ou CSOSN, nunca os dois.', path: ['icmsCsosn'] }),
})
export type ProdutoForm = z.infer<typeof produtoSchema>
export function valoresProduto(produto?: Produto): ProdutoForm {
  const fiscal = Object.fromEntries(fiscalFields.map(([key]) => [key, produto?.dadosFiscais?.[key]?.toString() ?? ''])) as ProdutoForm['dadosFiscais']
  return {
    nome: produto?.nome ?? '', codigoBarras: produto?.codigoBarras ?? '', categoriaId: produto?.categoriaId.toString() ?? '',
    unidadeMedida: produto?.unidadeMedida ?? 'UN', descricao: produto?.descricao ?? '', marca: produto?.marca ?? '',
    permiteVendaFracionada: produto?.permiteVendaFracionada ?? false, controlaLote: produto?.controlaLote ?? false,
    controlaValidade: produto?.controlaValidade ?? false, dadosFiscais: fiscal,
  }
}
export function dadosProduto(form: ProdutoForm): DadosProduto {
  const fiscal = Object.fromEntries(Object.entries(form.dadosFiscais).map(([key, value]) => [
    key, value === '' ? null : ['icmsAliquota', 'icmsReducaoBase'].includes(key) ? Number(value.replace(',', '.')) : value,
  ])) as DadosFiscais
  return {
    ...form, categoriaId: Number(form.categoriaId), descricao: form.descricao || null, marca: form.marca || null,
    dadosFiscais: Object.values(fiscal).every(v => v === null) ? null : fiscal,
  }
}
export async function validarImagem(arquivo: File, maxBytes: number, formatos: string[]) {
  if (!formatos.includes(arquivo.type)) throw new Error('Selecione uma imagem PNG ou JPEG.')
  if (arquivo.size > maxBytes) throw new Error('A imagem deve ter no máximo ' + Math.round(maxBytes / 1024 / 1024) + ' MiB.')
  const bitmap = await createImageBitmap(arquivo)
  try {
    if (bitmap.width > 4096 || bitmap.height > 4096 || bitmap.width * bitmap.height > 4_000_000) {
      throw new Error('Use uma imagem com até 4096 px por lado e 4 milhões de pixels no total.')
    }
  } finally { bitmap.close() }
}
