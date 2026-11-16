package br.com.mercadinhos.identidade.internal;

import java.util.List;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

@Getter
final class SessaoUsuario extends User {
    private static final long serialVersionUID = 1L;
    private final Long usuarioId;
    private final Long organizacaoId;
    private final long versaoUsuario;
    private final Papel papel;

    SessaoUsuario(VinculoOrganizacao vinculo) {
        super(vinculo.getUsuario().getEmail(), vinculo.getUsuario().getSenhaHash(),
                vinculo.getUsuario().isAtivo() && vinculo.isAtivo() && vinculo.getOrganizacao().isAtiva(),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + vinculo.getPapel().name())));
        usuarioId = vinculo.getUsuario().getId();
        organizacaoId = vinculo.getOrganizacao().getId();
        versaoUsuario = vinculo.getUsuario().getVersao();
        papel = vinculo.getPapel();
    }
}
