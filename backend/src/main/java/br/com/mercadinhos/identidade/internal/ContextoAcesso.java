package br.com.mercadinhos.identidade.internal;

import br.com.mercadinhos.identidade.Acao;
import br.com.mercadinhos.identidade.AcessoAtual;
import br.com.mercadinhos.identidade.Modulo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ContextoAcesso implements AcessoAtual {
    private final VinculoRepository vinculos;

    @Override
    public Long organizacaoId() { return vinculoAtual().getOrganizacao().getId(); }

    @Override
    public Long usuarioId() { return vinculoAtual().getUsuario().getId(); }

    @Override
    public void exigir(Modulo modulo, Acao acao) {
        var vinculo = vinculoAtual();
        if (modulo == null || acao == null || (vinculo.getPapel() != Papel.ADMIN
                && !vinculo.getPermissoes().contains(new PermissaoModulo(modulo, acao)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ação não autorizada");
        }
    }

    VinculoOrganizacao vinculoAtual() {
        var sessao = PrincipalAtual.obter();
        return vinculos.findByUsuarioId(sessao.getUsuarioId())
                .filter(v -> v.isAtivo() && v.getUsuario().isAtivo() && v.getOrganizacao().isAtiva())
                .filter(v -> v.getOrganizacao().getId().equals(sessao.getOrganizacaoId()))
                .filter(v -> v.getPapel() == sessao.getPapel())
                .filter(v -> v.getUsuario().getVersao() == sessao.getVersaoUsuario())
                .filter(v -> v.getVersao() == sessao.getVersaoVinculo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));
    }
}
