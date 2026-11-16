package br.com.mercadinhos.identidade.internal;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AutenticacaoService implements UserDetailsService {
    private final VinculoRepository vinculos;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return vinculos.findByUsuarioEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(SessaoUsuario::new)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    @Transactional(readOnly = true)
    public boolean sessaoValida(SessaoUsuario sessao) {
        return vinculos.findByUsuarioId(sessao.getUsuarioId())
                .filter(v -> v.isAtivo() && v.getUsuario().isAtivo() && v.getOrganizacao().isAtiva())
                .filter(v -> v.getOrganizacao().getId().equals(sessao.getOrganizacaoId()))
                .filter(v -> v.getPapel() == sessao.getPapel())
                .filter(v -> v.getUsuario().getVersao() == sessao.getVersaoUsuario())
                .isPresent();
    }
}
