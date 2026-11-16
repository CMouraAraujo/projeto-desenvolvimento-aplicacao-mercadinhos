package br.com.mercadinhos.identidade.internal;

import java.util.Locale;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
class ProvisionamentoService {
    private final OrganizacaoRepository organizacoes;
    private final UsuarioRepository usuarios;
    private final VinculoRepository vinculos;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Long provisionar(@Valid CadastroOrganizacao dados) {
        String email = dados.email().strip().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmail(email)) {
            throw new IllegalArgumentException("E-mail já cadastrado; nenhuma organização foi criada.");
        }
        Organizacao organizacao = organizacoes.save(new Organizacao(dados.organizacao().strip()));
        Usuario usuario = usuarios.save(new Usuario(email, dados.administrador().strip(),
                passwordEncoder.encode(dados.senha())));
        vinculos.saveAndFlush(new VinculoOrganizacao(organizacao, usuario, Papel.ADMIN));
        return organizacao.getId();
    }
}
