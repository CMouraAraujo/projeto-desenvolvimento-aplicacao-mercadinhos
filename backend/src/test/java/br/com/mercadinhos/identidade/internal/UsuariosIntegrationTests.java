package br.com.mercadinhos.identidade.internal;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import br.com.mercadinhos.TestcontainersConfiguration;
import br.com.mercadinhos.identidade.Acao;
import br.com.mercadinhos.identidade.AcessoAtual;
import br.com.mercadinhos.identidade.Modulo;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class UsuariosIntegrationTests {
    private static final String SENHA = "Senha-funcionario-2026!";
    private static final String CONSULTAR = "[{\"modulo\":\"PRODUTOS\",\"acao\":\"CONSULTAR\"}]";
    private static final String EDITAR = "[{\"modulo\":\"PRODUTOS\",\"acao\":\"CONSULTAR\"},{\"modulo\":\"PRODUTOS\",\"acao\":\"EDITAR\"}]";
    @Autowired MockMvc mvc;
    @Autowired ProvisionamentoService provisionamento;
    @Autowired UsuarioRepository usuarios;
    @Autowired VinculoRepository vinculos;
    @Autowired OrganizacaoRepository organizacoes;
    @Autowired AutenticacaoService autenticacao;
    @Autowired UsuariosService servico;
    @Autowired AcessoAtual acesso;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    private Long orgA;
    private Long orgB;
    private Long funcionarioA;
    private Long funcionarioB;
    private SessaoUsuario admin;
    private SessaoUsuario funcionario;
    private String prefixo;
    private String emailFuncionario;

    @BeforeEach
    void preparar() {
        prefixo = UUID.randomUUID().toString();
        orgA = provisionamento.provisionar(new CadastroOrganizacao("Empresa A", "Admin A", prefixo + "-a@teste.example", SENHA));
        orgB = provisionamento.provisionar(new CadastroOrganizacao("Empresa B", "Admin B", prefixo + "-b@teste.example", SENHA));
        emailFuncionario = prefixo + "-func-a@teste.example";
        String hash = passwordEncoder.encode(SENHA);
        funcionarioA = criarFuncionario(orgA, emailFuncionario, hash);
        funcionarioB = criarFuncionario(orgB, prefixo + "-func-b@teste.example", hash);
        admin = (SessaoUsuario) autenticacao.loadUserByUsername(prefixo + "-a@teste.example");
        funcionario = (SessaoUsuario) autenticacao.loadUserByUsername(emailFuncionario);
    }

    private Long criarFuncionario(Long org, String email, String hash) {
        var usuario = usuarios.saveAndFlush(new Usuario(email, "Funcionário", hash));
        vinculos.saveAndFlush(new VinculoOrganizacao(organizacoes.getReferenceById(org), usuario, Papel.FUNCIONARIO));
        return usuario.getId();
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        List<Long> ids = jdbc.queryForList("SELECT usuario_id FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)",
                Long.class, orgA, orgB);
        jdbc.update("DELETE FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)", orgA, orgB);
        for (Long id : ids) { jdbc.update("DELETE FROM usuarios WHERE id = ?", id); }
        jdbc.update("DELETE FROM organizacoes WHERE id IN (?, ?)", orgA, orgB);
    }

    @Test
    void administradorCadastraFuncionarioComHashEVinculoNaPropriaOrganizacao() throws Exception {
        String email = prefixo + "-novo@teste.example";
        String body = cadastrar(cadastro(email, EDITAR)).andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.papel").value("FUNCIONARIO"))
                .andExpect(jsonPath("$.permissoes.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(SENHA, "senhaHash", "senha_hash");
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE email = ?", String.class, email);
        assertThat(passwordEncoder.matches(SENHA, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT organizacao_id FROM vinculos_organizacao WHERE usuario_id = (SELECT id FROM usuarios WHERE email = ?)",
                Long.class, email)).isEqualTo(orgA);
    }

    @Test
    void listagemPaginadaNaoIncluiUsuariosDeOutraOrganizacao() throws Exception {
        mvc.perform(get("/api/usuarios").with(user(admin)).param("tamanho", "1").header("X-Organizacao-Id", orgB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.total").value(2));
        String body = mvc.perform(get("/api/usuarios").with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itens.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(prefixo + "-b@teste.example", prefixo + "-func-b@teste.example");
    }

    @Test
    void consultaEAlteracoesDeOutraOrganizacaoRetornam404() throws Exception {
        mvc.perform(get("/api/usuarios/" + funcionarioB).with(user(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/usuarios/9223372036854775807").with(user(admin))).andExpect(status().isNotFound());
        permissoes(funcionarioB, 0, CONSULTAR).andExpect(status().isNotFound());
        situacao(funcionarioB, 0, false).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT ativo FROM usuarios WHERE id = ?", Boolean.class, funcionarioB)).isTrue();
    }

    @Test
    void funcionarioNaoPodeGerenciarUsuariosNemAsPropriasPermissoes() throws Exception {
        for (String rota : List.of("/api/usuarios", "/api/usuarios/" + funcionarioA, "/api/modulos")) {
            mvc.perform(get(rota).with(user(funcionario))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/usuarios").with(user(funcionario)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cadastro(prefixo + "-negado@teste.example", "[]")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/" + funcionarioA + "/permissoes").with(user(funcionario)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(atualizacao(0, EDITAR)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/" + funcionarioA + "/situacao").with(user(funcionario)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"versao\":0,\"ativo\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorNaoPodeDesativarASiMesmoOuAlterarSeuPapel() throws Exception {
        situacao(admin.getUsuarioId(), 0, false).andExpect(status().isForbidden());
        permissoes(admin.getUsuarioId(), 0, "[]").andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT ativo FROM usuarios WHERE id = ?", Boolean.class, admin.getUsuarioId())).isTrue();
    }

    @Test
    void rejeitaTentativaDeInjetarPapelOrganizacaoOuId() throws Exception {
        String body = cadastro(prefixo + "-injetado@teste.example", "[]");
        for (String campo : List.of("\"papel\":\"ADMIN\"", "\"organizacaoId\":" + orgB, "\"id\":123")) {
            cadastrar(body.substring(0, body.length() - 1) + "," + campo + "}").andExpect(status().isBadRequest());
        }
        assertThat(usuarios.existsByEmail(prefixo + "-injetado@teste.example")).isFalse();
    }

    @Test
    void validaPermissoesNulasDesconhecidasEOuSemConsultar() throws Exception {
        for (String permissoes : List.of("null", "[null]", "[{\"modulo\":\"DESCONHECIDO\",\"acao\":\"CONSULTAR\"}]",
                "[{\"modulo\":\"PRODUTOS\",\"acao\":\"EXCLUIR\"}]", "[{\"modulo\":\"PRODUTOS\",\"acao\":\"EDITAR\"}]")) {
            cadastrar(cadastro(prefixo + "-invalido@teste.example", permissoes)).andExpect(status().isBadRequest());
        }
        assertThat(usuarios.existsByEmail(prefixo + "-invalido@teste.example")).isFalse();
    }

    @Test
    void senhaInvalidaNaoApareceNoErroENaoCriaConta() throws Exception {
        String body = cadastro(prefixo + "-senha@teste.example", "[]").replace(SENHA, "ç".repeat(40));
        String erro = cadastrar(body).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(erro).doesNotContain("ç".repeat(40), "senhaHash", "SQLException");
        assertThat(usuarios.existsByEmail(prefixo + "-senha@teste.example")).isFalse();
    }

    @Test
    void emailDuplicadoIncluindoOutraEmpresaRetornaConflitoSemDadosInternos() throws Exception {
        String erro = cadastrar(cadastro((prefixo + "-b@teste.example").toUpperCase(), "[]"))
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(erro).doesNotContain("Empresa B", "uk_usuarios", "senha_hash");
    }

    @Test
    void exigeAutenticacaoECsrfParaAlteracoes() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(cadastro(prefixo + "-anonimo@teste.example", "[]"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(cadastro(prefixo + "-semcsrf@teste.example", "[]"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/usuarios/" + funcionarioA + "/permissoes").with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content(atualizacao(0, CONSULTAR))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/usuarios/" + funcionarioA + "/situacao").with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versao\":0,\"ativo\":false}")).andExpect(status().isForbidden());
    }

    @Test
    void versaoDesatualizadaNaoSobrescrevePermissoes() throws Exception {
        String body = permissoes(funcionarioA, 0, CONSULTAR).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(((Number) JsonPath.read(body, "$.versao")).longValue()).isEqualTo(1);
        permissoes(funcionarioA, 0, EDITAR).andExpect(status().isConflict());
        mvc.perform(get("/api/usuarios/" + funcionarioA).with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permissoes.length()").value(1));
    }

    @Test
    void revogarPermissoesInvalidaSessaoEExigeNovoLogin() throws Exception {
        permissoes(funcionarioA, 0, CONSULTAR).andExpect(status().isOk());
        MockHttpSession sessao = entrarFuncionario();
        mvc.perform(get("/api/auth/permissoes").session(sessao)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        permissoes(funcionarioA, 1, "[]").andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isUnauthorized());
        assertThat(sessao.isInvalid()).isTrue();
        mvc.perform(get("/api/auth/permissoes").session(entrarFuncionario()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void desativacaoBloqueiaSessaoELoginEReativacaoNaoRestauraSessaoAntiga() throws Exception {
        MockHttpSession sessao = entrarFuncionario();
        situacao(funcionarioA, 0, false).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
        mvc.perform(get("/api/auth/me").session(sessao)).andExpect(status().isUnauthorized());
        assertThat(sessao.isInvalid()).isTrue();
        mvc.perform(post("/api/auth/login").with(csrf()).param("email", emailFuncionario).param("senha", SENHA))
                .andExpect(status().isUnauthorized());
        situacao(funcionarioA, 0, true).andExpect(status().isConflict());
        situacao(funcionarioA, 1, true).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(2));
        assertThat(entrarFuncionario()).isNotNull();
    }

    @Test
    void contratoDeAcessoExigeAcaoEspecificaENegaPorPadrao() throws Exception {
        executarComo(funcionario, () -> assertThatThrownBy(() -> acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR))
                .isInstanceOf(ResponseStatusException.class));
        permissoes(funcionarioA, 0, CONSULTAR).andExpect(status().isOk());
        SessaoUsuario atualizado = (SessaoUsuario) autenticacao.loadUserByUsername(emailFuncionario);
        executarComo(atualizado, () -> {
            assertThat(acesso.organizacaoId()).isEqualTo(orgA);
            assertThatCode(() -> acesso.exigir(Modulo.PRODUTOS, Acao.CONSULTAR)).doesNotThrowAnyException();
            assertThatThrownBy(() -> acesso.exigir(Modulo.PRODUTOS, Acao.EDITAR))
                    .isInstanceOf(ResponseStatusException.class);
        });
        executarComo(admin, () -> {
            for (Acao acao : Acao.values()) {
                assertThatCode(() -> acesso.exigir(Modulo.PRODUTOS, acao)).doesNotThrowAnyException();
            }
        });
    }

    @Test
    void protecaoDoServicoNaoDependeSomenteDaRotaHttp() {
        executarComo(funcionario, () -> assertThatThrownBy(() -> servico.listar(0, 20))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class));
    }

    @Test
    void catalogoDeModulosEPermissoesEfetivasDoAdmin() throws Exception {
        mvc.perform(get("/api/modulos").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modulo").value("PRODUTOS"))
                .andExpect(jsonPath("$[0].acoes.length()").value(4));
        mvc.perform(get("/api/auth/permissoes").with(user(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    void limitaPaginacaoERejeitaJsonMalformado() throws Exception {
        for (String query : List.of("pagina=-1", "tamanho=0", "tamanho=101")) {
            mvc.perform(get("/api/usuarios?" + query).with(user(admin))).andExpect(status().isBadRequest());
        }
        cadastrar("{\"senha\":").andExpect(status().isBadRequest());
    }

    @Test
    void criacoesConcorrentesNaoDuplicamContaOuVinculo() throws Exception {
        String email = prefixo + "-concorrente@teste.example";
        List<Integer> codigos = concorrer(() -> cadastrar(cadastro(email, CONSULTAR)).andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vinculos_organizacao WHERE usuario_id = (SELECT id FROM usuarios WHERE email = ?)",
                Integer.class, email)).isEqualTo(1);
    }

    @Test
    void alteracoesConcorrentesComMesmaVersaoNaoPerdemAtualizacao() throws Exception {
        List<Integer> codigos = concorrer(() -> permissoes(funcionarioA, 0, CONSULTAR).andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(200, 409);
    }

    private List<Integer> concorrer(Callable<Integer> chamada) throws Exception {
        var inicio = new CountDownLatch(1);
        Callable<Integer> tarefa = () -> { inicio.await(); return chamada.call(); };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(tarefa);
            var b = executor.submit(tarefa);
            inicio.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }

    private void executarComo(SessaoUsuario principal, Runnable tarefa) {
        var anterior = SecurityContextHolder.getContext();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        try { tarefa.run(); } finally { SecurityContextHolder.setContext(anterior); }
    }

    private MockHttpSession entrarFuncionario() throws Exception {
        var csrfResult = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        var sessao = (MockHttpSession) csrfResult.getRequest().getSession();
        mvc.perform(post("/api/auth/login").session(sessao).header("X-CSRF-TOKEN", token)
                .param("email", emailFuncionario).param("senha", SENHA)
                .with(r -> { r.setRemoteAddr("198.51.100.70"); return r; })).andExpect(status().isNoContent());
        return sessao;
    }

    private ResultActions cadastrar(String json) throws Exception {
        return enviar(post("/api/usuarios"), json);
    }

    private ResultActions permissoes(Long id, long versao, String json) throws Exception {
        return enviar(put("/api/usuarios/" + id + "/permissoes"), atualizacao(versao, json));
    }

    private ResultActions situacao(Long id, long versao, boolean ativo) throws Exception {
        return enviar(patch("/api/usuarios/" + id + "/situacao"), "{\"versao\":" + versao + ",\"ativo\":" + ativo + "}");
    }

    private ResultActions enviar(MockHttpServletRequestBuilder request, String json) throws Exception {
        return mvc.perform(request.with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String atualizacao(long versao, String permissoes) {
        return "{\"versao\":" + versao + ",\"permissoes\":" + permissoes + "}";
    }

    private String cadastro(String email, String permissoes) {
        return "{\"nome\":\"Funcionário novo\",\"email\":\"" + email + "\",\"senha\":\"" + SENHA
                + "\",\"permissoes\":" + permissoes + "}";
    }
}
