package br.com.mercadinhos.identidade.internal;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import br.com.mercadinhos.identidade.Acao;
import br.com.mercadinhos.identidade.Modulo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Transactional(readOnly = true)
class UsuariosService {
    private final UsuarioRepository usuarios;
    private final VinculoRepository vinculos;
    private final OrganizacaoRepository organizacoes;
    private final PasswordEncoder passwordEncoder;
    private final ContextoAcesso contexto;

    record Pagina(List<UsuarioDTO> itens, int pagina, int tamanho, long total) { }
    record ModuloDTO(Modulo modulo, List<Acao> acoes) { }

    public Pagina listar(@Min(0) int pagina, @Min(1) @Max(100) int tamanho) {
        Long organizacaoId = contexto.organizacaoId();
        var resultado = vinculos.findByOrganizacaoId(organizacaoId, PageRequest.of(pagina, tamanho,
                Sort.by("usuario.nome").ascending().and(Sort.by("usuario.id"))));
        return new Pagina(resultado.stream().map(UsuariosService::resposta).toList(),
                pagina, tamanho, resultado.getTotalElements());
    }

    public UsuarioDTO obter(Long usuarioId) {
        return resposta(vinculos.findByUsuarioIdAndOrganizacaoId(usuarioId, contexto.organizacaoId())
                .orElseThrow(UsuariosService::naoEncontrado));
    }

    public List<ModuloDTO> modulos() {
        contexto.organizacaoId();
        return Arrays.stream(Modulo.values()).map(m -> new ModuloDTO(m, List.of(Acao.values()))).toList();
    }

    @Transactional
    public UsuarioDTO cadastrar(@Valid NovoFuncionario dados) {
        Long organizacaoId = contexto.organizacaoId();
        var permissoes = validarPermissoes(dados.permissoes());
        String email = dados.email().strip().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível cadastrar com os dados informados");
        }
        var organizacao = organizacoes.getReferenceById(organizacaoId);
        var usuario = usuarios.save(new Usuario(email, dados.nome().strip(), passwordEncoder.encode(dados.senha())));
        var vinculo = new VinculoOrganizacao(organizacao, usuario, Papel.FUNCIONARIO);
        vinculo.substituirPermissoes(permissoes);
        return resposta(vinculos.saveAndFlush(vinculo));
    }

    @Transactional
    public UsuarioDTO atualizarPermissoes(Long usuarioId, @Valid AtualizarPermissoes dados) {
        var vinculo = funcionarioParaAlterar(usuarioId, dados.versao());
        vinculo.substituirPermissoes(validarPermissoes(dados.permissoes()));
        vinculos.flush();
        return resposta(vinculo);
    }

    @Transactional
    public UsuarioDTO atualizarSituacao(Long usuarioId, @Valid AtualizarSituacao dados) {
        var vinculo = funcionarioParaAlterar(usuarioId, dados.versao());
        vinculo.alterarSituacao(dados.ativo());
        vinculos.flush();
        return resposta(vinculo);
    }

    private VinculoOrganizacao funcionarioParaAlterar(Long usuarioId, Long versao) {
        var vinculo = vinculos.buscarParaAlterar(usuarioId, contexto.organizacaoId())
                .orElseThrow(UsuariosService::naoEncontrado);
        if (vinculo.getPapel() != Papel.FUNCIONARIO || usuarioId.equals(PrincipalAtual.obter().getUsuarioId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administradores não podem ser alterados por esta API");
        }
        if (vinculo.getVersao() != versao) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cadastro alterado; consulte a versão atual e tente novamente");
        }
        return vinculo;
    }

    private static Set<PermissaoModulo> validarPermissoes(Set<PermissaoDTO> dados) {
        var permissoes = dados.stream().map(p -> new PermissaoModulo(p.modulo(), p.acao())).collect(Collectors.toSet());
        for (var permissao : permissoes) {
            if (permissao.getAcao() != Acao.CONSULTAR
                    && !permissoes.contains(new PermissaoModulo(permissao.getModulo(), Acao.CONSULTAR))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Outras ações exigem CONSULTAR no mesmo módulo");
            }
        }
        return permissoes;
    }

    static List<PermissaoDTO> permissoesEfetivas(VinculoOrganizacao vinculo) {
        if (vinculo.getPapel() == Papel.ADMIN) {
            return Arrays.stream(Modulo.values()).flatMap(m -> Arrays.stream(Acao.values())
                    .map(a -> new PermissaoDTO(m, a))).toList();
        }
        return vinculo.getPermissoes().stream().map(p -> new PermissaoDTO(p.getModulo(), p.getAcao()))
                .sorted(Comparator.comparing(PermissaoDTO::modulo).thenComparing(PermissaoDTO::acao)).toList();
    }

    private static UsuarioDTO resposta(VinculoOrganizacao vinculo) {
        var usuario = vinculo.getUsuario();
        return new UsuarioDTO(usuario.getId(), usuario.getNome(), usuario.getEmail(), vinculo.getPapel().name(),
                usuario.isAtivo() && vinculo.isAtivo(), vinculo.getVersao(), permissoesEfetivas(vinculo));
    }

    private static ResponseStatusException naoEncontrado() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado");
    }
}
