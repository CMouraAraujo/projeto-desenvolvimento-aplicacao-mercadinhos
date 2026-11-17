package br.com.mercadinhos.catalogo.internal;

interface ArmazenamentoImagens {
    String salvar(byte[] png);
    byte[] ler(String chave);
    void excluir(String chave);
}
