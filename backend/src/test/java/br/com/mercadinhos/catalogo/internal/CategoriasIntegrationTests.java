package br.com.mercadinhos.catalogo.internal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import br.com.mercadinhos.TestcontainersConfiguration;
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
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CategoriasIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDetailsService identidades;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CategoriasService servico;
    private final JsonMapper json = JsonMapper.builder().build();
    private static String hashTeste;
    private Long orgA;
    private Long orgB;
    private UserDetails admin;
    private UserDetails outroAdmin;
    private UserDetails consulta;
    private UserDetails editor;
    private UserDetails semAcesso;

    @BeforeEach
    void preparar() {
        String prefixo = UUID.randomUUID().toString();
        if (hashTeste == null) { hashTeste = passwordEncoder.encode("Senha-de-fixture-2026!"); }
        orgA = inserir("organizacoes", Map.of("nome", "Categorias A"));
        orgB = inserir("organizacoes", Map.of("nome", "Categorias B"));
        admin = usuario(orgA, prefixo + "-admin@teste.example", "ADMIN", List.of());
        outroAdmin = usuario(orgB, prefixo + "-outro@teste.example", "ADMIN", List.of());
        consulta = usuario(orgA, prefixo + "-consulta@teste.example", "FUNCIONARIO", List.of("CONSULTAR"));
        editor = usuario(orgA, prefixo + "-editor@teste.example", "FUNCIONARIO", List.of("CONSULTAR", "CADASTRAR", "EDITAR", "INATIVAR"));
        semAcesso = usuario(orgA, prefixo + "-sem@teste.example", "FUNCIONARIO", List.of());
    }

    private Long inserir(String tabela, Map<String, ?> valores) {
        return new SimpleJdbcInsert(jdbc).withTableName(tabela).usingColumns(valores.keySet().toArray(String[]::new)).usingGeneratedKeyColumns("id")
                .executeAndReturnKey(valores).longValue();
    }

    private UserDetails usuario(Long org, String email, String papel, List<String> acoes) {
        Long id = inserir("usuarios", Map.of("nome", papel, "email", email, "senha_hash", hashTeste));
        Long vinculo = inserir("vinculos_organizacao", Map.of("usuario_id", id, "organizacao_id", org, "papel", papel));
        for (String acao : acoes) {
            jdbc.update("INSERT INTO permissoes_modulo (vinculo_id, modulo, acao) VALUES (?, 'PRODUTOS', ?)", vinculo, acao);
        }
        return identidades.loadUserByUsername(email);
    }

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM categorias WHERE organizacao_id IN (?, ?)", orgA, orgB);
        var ids = jdbc.queryForList("SELECT usuario_id FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)", Long.class, orgA, orgB);
        jdbc.update("DELETE FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)", orgA, orgB);
        for (Long id : ids) { jdbc.update("DELETE FROM usuarios WHERE id = ?", id); }
        jdbc.update("DELETE FROM organizacoes WHERE id IN (?, ?)", orgA, orgB);
    }

    @Test
    void criaCategoriaAtivaComNomeNormalizadoEAuditoria() throws Exception {
        String resposta = criar(admin, "  Cafe\u0301   e\u00a0Cha\u0301  ").andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Café e Chá"))
                .andExpect(jsonPath("$.ativa").value(true)).andExpect(jsonPath("$.versao").value(0))
                .andExpect(jsonPath("$.criadoEm").exists()).andExpect(jsonPath("$.atualizadoEm").exists())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT organizacao_id FROM categorias WHERE id = ?", Long.class, id)).isEqualTo(orgA);
    }

    @Test
    void nomeUnicoIgnoraCaixaAcentosEEspacosMasPermiteOutraOrganizacao() throws Exception {
        criar(admin, "  Café   e Chá ").andExpect(status().isCreated());
        criar(admin, "cafe e cha").andExpect(status().isConflict());
        criar(outroAdmin, "cafe e cha").andExpect(status().isCreated());
    }

    @Test
    void categoriaInativaReservaNomeEPodeSerReativada() throws Exception {
        long id = criarId(admin, "Bebidas");
        situacao(admin, id, 0, false).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
        criar(admin, "bebidas").andExpect(status().isConflict());
        situacao(admin, id, 1, true).andExpect(status().isOk()).andExpect(jsonPath("$.ativa").value(true))
                .andExpect(jsonPath("$.versao").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categorias WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void consultaPaginadaFiltraBuscaESituacaoNaPropriaOrganizacao() throws Exception {
        criarId(admin, "Bebidas");
        long inativa = criarId(admin, "Bebidas especiais");
        criarId(admin, "Limpeza");
        criarId(outroAdmin, "Bebidas de outra empresa");
        situacao(admin, inativa, 0, false).andExpect(status().isOk());
        mvc.perform(get("/api/categorias").with(user(consulta)).param("busca", "BEBIDAS")
                        .param("ativa", "true").param("tamanho", "1").header("X-Organizacao-Id", orgB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Bebidas"));
        mvc.perform(get("/api/categorias").with(user(consulta)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/categorias").with(user(consulta)).param("ativa", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].id").value(inativa));
    }

    @Test
    void buscaTrataPercentualESublinhadoComoCaracteresLiterais() throws Exception {
        criarId(admin, "100% Natural");
        criarId(admin, "Sem_gluten");
        criarId(admin, "Limpeza");
        mvc.perform(get("/api/categorias").with(user(consulta)).param("busca", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("100% Natural"));
        mvc.perform(get("/api/categorias").with(user(consulta)).param("busca", "_"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Sem_gluten"));
    }

    @Test
    void categoriaDeOutraOrganizacaoRetorna404ParaConsultaEdicaoESituacao() throws Exception {
        long id = criarId(outroAdmin, "Privada");
        mvc.perform(get("/api/categorias/" + id).with(user(admin))).andExpect(status().isNotFound());
        editar(admin, id, 0, "Inválida").andExpect(status().isNotFound());
        situacao(admin, id, 0, false).andExpect(status().isNotFound());
        mvc.perform(get("/api/categorias/9223372036854775807").with(user(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/categorias/" + id).with(user(outroAdmin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Privada"))
                .andExpect(jsonPath("$.ativa").value(true));
    }

    @Test
    void editarPreservaIdEAtualizaVersaoSemAlterarOutraCategoria() throws Exception {
        long id = criarId(admin, "Original");
        editar(admin, id, 0, "  Novo   nome ").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.nome").value("Novo nome"))
                .andExpect(jsonPath("$.versao").value(1));
        editar(admin, id, 0, "Nome antigo").andExpect(status().isConflict());
        situacao(admin, id, 0, false).andExpect(status().isConflict());
    }

    @Test
    void renomearParaNomeExistenteRetorna409EPreservaCategoria() throws Exception {
        criarId(admin, "Café");
        long id = criarId(admin, "Outra");
        editar(admin, id, 0, "cafe").andExpect(status().isConflict());
        mvc.perform(get("/api/categorias/" + id).with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Outra"))
                .andExpect(jsonPath("$.versao").value(0));
    }

    @Test
    void funcionarioComTodosOsAcessosDeProdutosContinuaSemManterCategorias() throws Exception {
        long id = criarId(admin, "Protegida");
        criar(editor, "Não autorizada").andExpect(status().isForbidden());
        editar(editor, id, 0, "Proibida").andExpect(status().isForbidden());
        situacao(editor, id, 0, false).andExpect(status().isForbidden());
        mvc.perform(get("/api/categorias/" + id).with(user(editor))).andExpect(status().isOk());
    }

    @Test
    void usuarioSemConsultarNaoListaNemConsultaCategorias() throws Exception {
        long id = criarId(admin, "Protegida");
        mvc.perform(get("/api/categorias").with(user(semAcesso))).andExpect(status().isForbidden());
        mvc.perform(get("/api/categorias/" + id).with(user(semAcesso))).andExpect(status().isForbidden());
    }

    @Test
    void protecaoDeServicoImpedeManutencaoPorFuncionario() {
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(editor, null, editor.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        assertThatThrownBy(() -> servico.cadastrar(new NovaCategoria("Proibida", false)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void exigeAutenticacaoECsrf() throws Exception {
        mvc.perform(get("/api/categorias")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/categorias").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("nome", "Anônima")))).andExpect(status().isUnauthorized());
        long id = criarId(admin, "Segura");
        mvc.perform(post("/api/categorias").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Sem token\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/categorias/" + id).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Sem token\",\"versao\":0}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/categorias/" + id + "/situacao").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"ativa\":false,\"versao\":0}")).andExpect(status().isForbidden());
    }

    @Test
    void validaNomeVersaoEPaginacao() throws Exception {
        for (String nome : List.of("", "   ", "\u00a0\u00a0", "x".repeat(101))) {
            criar(admin, nome).andExpect(status().isBadRequest());
        }
        for (String query : List.of("pagina=-1", "tamanho=0", "tamanho=101", "ativa=talvez", "busca=" + "x".repeat(101))) {
            mvc.perform(get("/api/categorias?" + query).with(user(admin))).andExpect(status().isBadRequest());
        }
        enviar(admin, post("/api/categorias"), Map.of()).andExpect(status().isBadRequest());
        long id = criarId(admin, "Válida");
        enviar(admin, put("/api/categorias/" + id), Map.of("nome", "Sem versão"))
                .andExpect(status().isBadRequest());
        situacao(admin, id, -1, false).andExpect(status().isBadRequest());
        enviar(admin, patch("/api/categorias/" + id + "/situacao"), Map.of("versao", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejeitaOrganizacaoIdEEstadoInjetadosNoCadastro() throws Exception {
        for (String campo : List.of("organizacaoId", "id", "ativa", "versao")) {
            enviar(admin, post("/api/categorias"), Map.of("nome", "Injetada", campo, 123))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categorias WHERE organizacao_id = ?", Integer.class, orgA)).isZero();
    }

    @Test
    void naoOfereceExclusaoFisica() throws Exception {
        long id = criarId(admin, "Histórico");
        mvc.perform(delete("/api/categorias/" + id).with(user(admin)).with(csrf())).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categorias WHERE id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void cadastrosConcorrentesRespeitamUnicidade() throws Exception {
        var codigos = concorrer(() -> criar(admin, "Concorrente").andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(201, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categorias WHERE organizacao_id = ? AND nome = ?",
                Integer.class, orgA, "Concorrente")).isEqualTo(1);
    }

    @Test
    void alteracoesConcorrentesNaoSobrescrevemAVersaoAtual() throws Exception {
        long id = criarId(admin, "Concorrente");
        var codigos = concorrer(() -> editar(admin, id, 0, "Novo nome").andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(200, 409);
        mvc.perform(get("/api/categorias/" + id).with(user(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
    }

    @Test
    void classificacaoParaMenoresTemPadraoLivreEPodeSerAlteradaComVersao() throws Exception {
        criar(admin, "Sem classificação").andExpect(status().isCreated())
                .andExpect(jsonPath("$.permitidoParaMenores").value(true));
        String body = enviar(admin, post("/api/categorias"), Map.of("nome", "Livre", "permitidoParaMenores", true))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.permitidoParaMenores").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        editar(admin, id, 0, "Livre renomeada").andExpect(status().isOk())
                .andExpect(jsonPath("$.permitidoParaMenores").value(true));
        enviar(admin, put("/api/categorias/" + id), Map.of("nome", "Livre renomeada", "versao", 1, "permitidoParaMenores", false))
                .andExpect(status().isOk()).andExpect(jsonPath("$.permitidoParaMenores").value(false))
                .andExpect(jsonPath("$.versao").value(2));
        enviar(admin, put("/api/categorias/" + id), Map.of("nome", "Livre renomeada", "versao", 1, "permitidoParaMenores", true))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM categorias WHERE id = ?", Boolean.class, id)).isFalse();
    }

    @Test
    void classificacaoNaoPodeSerAlteradaPorFuncionarioOuOutraOrganizacao() throws Exception {
        String body = enviar(admin, post("/api/categorias"), Map.of("nome", "Protegida", "permitidoParaMenores", true))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        var dados = Map.of("nome", "Protegida", "versao", 0, "permitidoParaMenores", false);
        enviar(editor, put("/api/categorias/" + id), dados).andExpect(status().isForbidden());
        enviar(outroAdmin, put("/api/categorias/" + id), dados).andExpect(status().isNotFound());
        mvc.perform(put("/api/categorias/" + id).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dados))).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM categorias WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT versao FROM categorias WHERE id = ?", Long.class, id)).isZero();
    }

    @Test
    void classificacoesConcorrentesNaoSobrescrevemAVersaoAtual() throws Exception {
        long id = criarId(admin, "Concorrente classificação");
        var codigos = concorrer(() -> enviar(admin, put("/api/categorias/" + id),
                Map.of("nome", "Concorrente classificação", "versao", 0, "permitidoParaMenores", true))
                .andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM categorias WHERE id = ?", Boolean.class, id)).isTrue();
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

    private ResultActions criar(UserDetails ator, String nome) throws Exception {
        return enviar(ator, post("/api/categorias"), Map.of("nome", nome));
    }

    private long criarId(UserDetails ator, String nome) throws Exception {
        String body = criar(ator, nome).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private ResultActions editar(UserDetails ator, long id, long versao, String nome) throws Exception {
        return enviar(ator, put("/api/categorias/" + id), Map.of("nome", nome, "versao", versao));
    }

    private ResultActions situacao(UserDetails ator, long id, long versao, boolean ativa) throws Exception {
        return enviar(ator, patch("/api/categorias/" + id + "/situacao"), Map.of("ativa", ativa, "versao", versao));
    }

    private ResultActions enviar(UserDetails ator, MockHttpServletRequestBuilder request, Map<String, ?> dados) throws Exception {
        return mvc.perform(request.with(user(ator)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dados)));
    }
}
