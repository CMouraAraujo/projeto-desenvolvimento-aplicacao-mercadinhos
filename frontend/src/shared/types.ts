export type Acao = 'CONSULTAR' | 'CADASTRAR' | 'EDITAR' | 'INATIVAR'
export type Permissao = { modulo: 'PRODUTOS'; acao: Acao }
export type Usuario = { usuarioId: number; nome: string; email: string; organizacaoId: number; papel: 'ADMIN' | 'FUNCIONARIO' }
export type Organizacao = { id: number; nome: string }
export type Pagina<T> = { itens: T[]; pagina: number; tamanho: number; total: number }
export type Categoria = { id: number; nome: string; ativa: boolean; versao: number; criadoEm: string; atualizadoEm: string }
export type Unidade = 'UN' | 'KG' | 'G' | 'L' | 'ML' | 'CX' | 'PCT'
export type DadosFiscais = {
  ncm?: string | null; cest?: string | null; cfopPadrao?: string | null
  pisCst?: string | null; cofinsCst?: string | null; origem?: string | null
  icmsCst?: string | null; icmsCsosn?: string | null; icmsModalidadeBaseCalculo?: string | null
  icmsAliquota?: number | null; icmsReducaoBase?: number | null
}
export type DadosProduto = {
  nome: string; codigoBarras: string; categoriaId: number; unidadeMedida: Unidade
  descricao: string | null; marca: string | null; permiteVendaFracionada: boolean
  controlaLote: boolean; controlaValidade: boolean; dadosFiscais: DadosFiscais | null
}
export type Produto = DadosProduto & {
  id: number; ativo: boolean; versao: number; imagemUrl: string | null
  criadoEm: string; atualizadoEm: string; criadoPor: number; atualizadoPor: number
}
export type Opcoes = { unidadesMedida: Unidade[]; imagemMaxBytes: number; formatosImagem: string[] }
export function pode(usuario: Usuario, permissoes: Permissao[], acao: Acao) {
  return usuario.papel === 'ADMIN' || permissoes.some(p => p.modulo === 'PRODUTOS' && p.acao === acao)
}
