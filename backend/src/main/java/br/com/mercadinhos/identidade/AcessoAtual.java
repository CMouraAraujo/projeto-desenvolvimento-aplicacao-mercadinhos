package br.com.mercadinhos.identidade;

/** Contrato para os módulos obterem o contexto e exigirem autorização no backend. */
public interface AcessoAtual {
    Long organizacaoId();
    Long usuarioId();
    void exigir(Modulo modulo, Acao acao);
}
