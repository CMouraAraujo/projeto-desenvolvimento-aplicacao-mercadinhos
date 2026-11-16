package br.com.mercadinhos.identidade.internal;

import java.util.UUID;
import br.com.mercadinhos.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AutenticacaoIntegrationTests {
    private static final String SENHA = "Senha-de-teste-2026!";
    @Autowired MockMvc mvc;
    @Autowired ProvisionamentoService provisionamento;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    private String email;
    private String outroEmail;
    private Long organizacaoId;
    private Long outraOrganizacaoId;

    record Navegador(MockHttpSession sessao, String token) { }

    @BeforeEach
    void criarOrganizacoes() {
        String prefix = UUID.randomUUID().toString();
        email = prefix + "@teste.example";
        outroEmail = prefix + "-outro@teste.example";
        organizacaoId = provisionamento.provisionar(new CadastroOrganizacao("Mercadinho A", "Admin A", email, SENHA));
        outraOrganizacaoId = provisionamento.provisionar(new CadastroOrganizacao("Mercadinho B", "Admin B", outroEmail, SENHA));
    }

    @AfterEach
    void removerDadosDoTeste() {
        jdbc.update("DELETE FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)", organizacaoId, outraOrganizacaoId);
        jdbc.update("DELETE FROM usuarios WHERE email IN (?, ?)", email, outroEmail);
        jdbc.update("DELETE FROM organizacoes WHERE id IN (?, ?)", organizacaoId, outraOrganizacaoId);
    }

    @Test
    void provisionamentoArmazenaHashEFazVinculoAdmin() {
        String hash = jdbc.queryForObject("SELECT senha_hash FROM usuarios WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain(SENHA);
        assertThat(passwordEncoder.matches(SENHA, hash)).isTrue();
        assertThat(jdbc.queryForObject("SELECT papel FROM vinculos_organizacao WHERE organizacao_id = ?",
                String.class, organizacaoId)).isEqualTo("ADMIN");
    }

    @Test
    void emailDuplicadoNaoCriaOrganizacaoOrfa() {
        int antes = jdbc.queryForObject("SELECT COUNT(*) FROM organizacoes", Integer.class);
        assertThatThrownBy(() -> provisionamento.provisionar(
                new CadastroOrganizacao("Duplicada", "Admin", email.toUpperCase(), SENHA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organizacoes", Integer.class)).isEqualTo(antes);
    }

    @Test
    void cadastroInvalidoNaoPersisteNemExibeSenha() {
        var cadastro = new CadastroOrganizacao("Inválida", "Admin", "invalido@teste.example", "ç".repeat(40));
        assertThatThrownBy(() -> provisionamento.provisionar(cadastro))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(cadastro.toString()).doesNotContain(cadastro.senha());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class,
                cadastro.email())).isZero();
    }

    @Test
    void loginConsultaSessaoRotacionaIdEApagaCredenciaisDaSessao() throws Exception {
        Navegador navegador = csrf(null);
        String idAnterior = navegador.sessao().getId();
        login(navegador, email.toUpperCase(), SENHA).andExpect(status().isNoContent());
        assertThat(navegador.sessao().getId()).isNotEqualTo(idAnterior);
        var context = (org.springframework.security.core.context.SecurityContext) navegador.sessao()
                .getAttribute("SPRING_SECURITY_CONTEXT");
        assertThat(((SessaoUsuario) context.getAuthentication().getPrincipal()).getPassword()).isNull();
        mvc.perform(get("/api/auth/me").session(navegador.sessao()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.organizacaoId").value(organizacaoId))
                .andExpect(jsonPath("$.papel").value("ADMIN"))
                .andExpect(jsonPath("$.senhaHash").doesNotExist());
    }

    @Test
    void senhaInvalidaEContaInexistenteRetornamMesmaResposta() throws Exception {
        Navegador navegador = csrf(null);
        login(navegador, email, "senha-incorreta").andExpect(status().isUnauthorized()).andExpect(content().string(""));
        login(navegador, "inexistente@teste.example", SENHA).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(get("/api/auth/me").session(navegador.sessao())).andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioOrganizacaoEVinculoInativosNaoEntram() throws Exception {
        Navegador navegador = csrf(null);
        jdbc.update("UPDATE usuarios SET ativo = false WHERE email = ?", email);
        login(navegador, email, SENHA).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE usuarios SET ativo = true WHERE email = ?", email);
        jdbc.update("UPDATE organizacoes SET ativa = false WHERE id = ?", organizacaoId);
        login(navegador, email, SENHA).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE organizacoes SET ativa = true WHERE id = ?", organizacaoId);
        jdbc.update("UPDATE vinculos_organizacao SET ativo = false WHERE organizacao_id = ?", organizacaoId);
        login(navegador, email, SENHA).andExpect(status().isUnauthorized());
    }

    @Test
    void contextoDaOrganizacaoNaoPodeSerForjadoPorParametroOuCabecalho() throws Exception {
        Navegador a = entrar(email);
        Navegador b = entrar(outroEmail);
        mvc.perform(get("/api/organizacao").session(a.sessao())
                        .param("organizacaoId", outraOrganizacaoId.toString())
                        .header("X-Organizacao-Id", outraOrganizacaoId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(organizacaoId))
                .andExpect(jsonPath("$.nome").value("Mercadinho A"));
        mvc.perform(get("/api/organizacao").session(b.sessao()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(outraOrganizacaoId));
        mvc.perform(get("/api/organizacoes/" + outraOrganizacaoId).session(a.sessao()))
                .andExpect(status().isForbidden());
    }

    @Test
    void sessaoExistenteERevogadaQuandoUsuarioOuOrganizacaoDesativado() throws Exception {
        Navegador a = entrar(email);
        jdbc.update("UPDATE usuarios SET ativo = false WHERE email = ?", email);
        mvc.perform(get("/api/auth/me").session(a.sessao())).andExpect(status().isUnauthorized());
        assertThat(a.sessao().isInvalid()).isTrue();
        Navegador b = entrar(outroEmail);
        jdbc.update("UPDATE organizacoes SET ativa = false WHERE id = ?", outraOrganizacaoId);
        mvc.perform(get("/api/auth/me").session(b.sessao())).andExpect(status().isUnauthorized());
        assertThat(b.sessao().isInvalid()).isTrue();
    }

    @Test
    void versaoDaContaAlteradaRevogaSessaoExistente() throws Exception {
        Navegador navegador = entrar(email);
        jdbc.update("UPDATE usuarios SET versao = versao + 1 WHERE email = ?", email);
        mvc.perform(get("/api/auth/me").session(navegador.sessao())).andExpect(status().isUnauthorized());
        assertThat(navegador.sessao().isInvalid()).isTrue();
    }

    @Test
    void loginLogoutExigemCsrfELogoutInvalidaSessao() throws Exception {
        mvc.perform(post("/api/auth/login").param("email", email).param("senha", SENHA))
                .andExpect(status().isForbidden());
        Navegador navegador = entrar(email);
        mvc.perform(post("/api/auth/logout").session(navegador.sessao())).andExpect(status().isForbidden());
        Navegador atualizado = csrf(navegador.sessao());
        mvc.perform(post("/api/auth/logout").session(atualizado.sessao()).header("X-CSRF-TOKEN", atualizado.token()))
                .andExpect(status().isNoContent());
        assertThat(atualizado.sessao().isInvalid()).isTrue();
        mvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", atualizado.sessao().getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenAnteriorAoLoginNaoPodeSerReutilizado() throws Exception {
        Navegador navegador = csrf(null);
        login(navegador, email, SENHA).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").session(navegador.sessao()).header("X-CSRF-TOKEN", navegador.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void excessoDeTentativasRecebe429() throws Exception {
        Navegador navegador = csrf(null);
        for (int tentativa = 0; tentativa < 31; tentativa++) {
            var request = post("/api/auth/login").session(navegador.sessao())
                    .header("X-CSRF-TOKEN", navegador.token()).param("email", "")
                    .param("senha", "").with(r -> { r.setRemoteAddr("203.0.113.200"); return r; });
            mvc.perform(request).andExpect(status().is(tentativa < 30 ? 401 : 429));
        }
    }

    @Test
    void loginComSenhaGrandeNaoCausaErroInterno() throws Exception {
        Navegador navegador = csrf(null);
        login(navegador, email, "x".repeat(100)).andExpect(status().isUnauthorized());
    }

    @Test
    void cadastroConcorrenteNaoDuplicaEmailNemDeixaOrganizacaoOrfa() throws Exception {
        String emailConcorrente = UUID.randomUUID() + "@concorrencia.example";
        String nomeConcorrente = "Concorrente " + UUID.randomUUID();
        var dados = new CadastroOrganizacao(nomeConcorrente, "Admin", emailConcorrente, SENHA);
        var inicio = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> tentativa = () -> {
            inicio.await();
            try {
                provisionamento.provisionar(dados);
                return true;
            } catch (IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException expected) {
                return false;
            }
        };
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(tentativa);
            var b = executor.submit(tentativa);
            inicio.countDown();
            assertThat(java.util.List.of(a.get(30, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(30, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organizacoes WHERE nome = ?", Integer.class,
                    nomeConcorrente)).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM vinculos_organizacao WHERE usuario_id IN (SELECT id FROM usuarios WHERE email = ?)", emailConcorrente);
            jdbc.update("DELETE FROM usuarios WHERE email = ?", emailConcorrente);
            jdbc.update("DELETE FROM organizacoes WHERE nome = ?", nomeConcorrente);
        }
    }

    @Test
    void mudancaDePapelOuDesativacaoDoVinculoRevogaSessao() throws Exception {
        Navegador a = entrar(email);
        Navegador b = entrar(outroEmail);
        jdbc.update("UPDATE vinculos_organizacao SET papel = 'FUNCIONARIO' WHERE organizacao_id = ?", organizacaoId);
        jdbc.update("UPDATE vinculos_organizacao SET ativo = false WHERE organizacao_id = ?", outraOrganizacaoId);
        mvc.perform(get("/api/auth/me").session(a.sessao())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").session(b.sessao())).andExpect(status().isUnauthorized());
        assertThat(a.sessao().isInvalid()).isTrue();
        assertThat(b.sessao().isInvalid()).isTrue();
    }
    private Navegador entrar(String usuario) throws Exception {
        Navegador navegador = csrf(null);
        login(navegador, usuario, SENHA).andExpect(status().isNoContent());
        return navegador;
    }

    private Navegador csrf(MockHttpSession sessao) throws Exception {
        var request = get("/api/auth/csrf");
        if (sessao != null) { request.session(sessao); }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        return new Navegador((MockHttpSession) result.getRequest().getSession(), token);
    }

    private org.springframework.test.web.servlet.ResultActions login(Navegador navegador, String usuario, String senha)
            throws Exception {
        return mvc.perform(post("/api/auth/login").session(navegador.sessao())
                .header("X-CSRF-TOKEN", navegador.token()).param("email", usuario).param("senha", senha)
                .with(request -> {
                    request.setRequestedSessionId(navegador.sessao().getId());
                    request.setRequestedSessionIdValid(true);
                    return request;
                }));
    }
}
