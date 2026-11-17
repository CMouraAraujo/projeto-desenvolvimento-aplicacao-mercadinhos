import { json, queryString, request } from '../shared/api'
import type { Categoria, DadosProduto, Opcoes, Pagina, Produto } from '../shared/types'

export const catalogo = {
  produtos: (filtros: Record<string, string | number | undefined>, signal?: AbortSignal) => request<Pagina<Produto>>('/produtos?' + queryString(filtros), { signal }),
  produto: (id: number, signal?: AbortSignal) => request<Produto>('/produtos/' + id, { signal }),
  opcoes: (signal?: AbortSignal) => request<Opcoes>('/produtos/opcoes', { signal }),
  salvar: (dados: DadosProduto, atual?: Produto) => atual
    ? request<Produto>('/produtos/' + atual.id, json('PUT', { versao: atual.versao, dados }))
    : request<Produto>('/produtos', json('POST', dados)),
  situacao: (produto: Produto) => request<Produto>('/produtos/' + produto.id + '/situacao', json('PATCH', { versao: produto.versao, ativo: !produto.ativo })),
  imagem: (produto: Produto, arquivo: File) => {
    const body = new FormData()
    body.set('arquivo', arquivo); body.set('versao', String(produto.versao))
    return request<Produto>('/produtos/' + produto.id + '/imagem', { method: 'PUT', body })
  },
  removerImagem: (produto: Produto) => request<Produto>('/produtos/' + produto.id + '/imagem?versao=' + produto.versao, { method: 'DELETE' }),
  categorias: (filtros: Record<string, string | number | undefined>, signal?: AbortSignal) => request<Pagina<Categoria>>('/categorias?' + queryString(filtros), { signal }),
  // O seletor precisa de todas as páginas, sem assumir que existem no máximo 100 categorias.
  todasCategorias: async (signal?: AbortSignal) => {
    const itens: Categoria[] = []
    let pagina = 0
    let total = 0
    do {
      const result = await catalogo.categorias({ pagina, tamanho: 100 }, signal)
      itens.push(...result.itens)
      total = result.total
      pagina++
      if (!result.itens.length) break
    } while (itens.length < total)
    return itens
  },
  salvarCategoria: (nome: string, atual?: Categoria) => atual
    ? request<Categoria>('/categorias/' + atual.id, json('PUT', { nome, versao: atual.versao }))
    : request<Categoria>('/categorias', json('POST', { nome })),
  situacaoCategoria: (categoria: Categoria) => request<Categoria>('/categorias/' + categoria.id + '/situacao', json('PATCH', { versao: categoria.versao, ativa: !categoria.ativa })),
}
