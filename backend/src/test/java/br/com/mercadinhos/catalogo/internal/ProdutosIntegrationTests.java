package br.com.mercadinhos.catalogo.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import br.com.mercadinhos.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ProdutosIntegrationTests {
    private static final Path IMAGENS = Path.of(System.getProperty("java.io.tmpdir"), "mercadinhos-produtos-" + UUID.randomUUID());
    private static String hash;
    private final JsonMapper json = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserDetailsService identidades;
    @Autowired PasswordEncoder encoder;
    @Autowired ImagemProdutoService imagens;
    @Autowired PlatformTransactionManager transacoes;
    private Long orgA, orgB, catA, catB;
    private UserDetails admin, outroAdmin, consulta, cadastro, edicao, inativacao, semAcesso;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) { registry.add("app.imagens.diretorio", () -> IMAGENS.toString()); }

    @BeforeEach
    void preparar() {
        if (hash == null) { hash = encoder.encode("Senha-de-teste-2026!"); }
        orgA = inserir("organizacoes", Map.of("nome", "Produtos A"));
        orgB = inserir("organizacoes", Map.of("nome", "Produtos B"));
        catA = inserir("categorias", Map.of("organizacao_id", orgA, "nome", "Categoria A", "permitido_para_menores", false));
        catB = inserir("categorias", Map.of("organizacao_id", orgB, "nome", "Categoria B", "permitido_para_menores", false));
        admin = usuario(orgA, "ADMIN", List.of());
        outroAdmin = usuario(orgB, "ADMIN", List.of());
        consulta = usuario(orgA, "FUNCIONARIO", List.of("CONSULTAR"));
        cadastro = usuario(orgA, "FUNCIONARIO", List.of("CONSULTAR", "CADASTRAR"));
        edicao = usuario(orgA, "FUNCIONARIO", List.of("CONSULTAR", "EDITAR"));
        inativacao = usuario(orgA, "FUNCIONARIO", List.of("CONSULTAR", "INATIVAR"));
        semAcesso = usuario(orgA, "FUNCIONARIO", List.of());
    }

    private Long inserir(String tabela, Map<String, ?> valores) {
        return new SimpleJdbcInsert(jdbc).withTableName(tabela).usingColumns(valores.keySet().toArray(String[]::new))
                .usingGeneratedKeyColumns("id").executeAndReturnKey(valores).longValue();
    }

    private UserDetails usuario(Long org, String papel, List<String> acoes) {
        String email = UUID.randomUUID() + "@produtos.example";
        Long id = inserir("usuarios", Map.of("nome", papel, "email", email, "senha_hash", hash));
        Long vinculo = inserir("vinculos_organizacao", Map.of("usuario_id", id, "organizacao_id", org, "papel", papel));
        for (String acao : acoes) { jdbc.update("INSERT INTO permissoes_modulo VALUES (?, 'PRODUTOS', ?)", vinculo, acao); }
        return identidades.loadUserByUsername(email);
    }

    @AfterEach
    void limpar() throws Exception {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM produtos WHERE organizacao_id IN (?, ?)", orgA, orgB);
        jdbc.update("DELETE FROM categorias WHERE organizacao_id IN (?, ?)", orgA, orgB);
        var ids = jdbc.queryForList("SELECT usuario_id FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)", Long.class, orgA, orgB);
        jdbc.update("DELETE FROM vinculos_organizacao WHERE organizacao_id IN (?, ?)", orgA, orgB);
        for (Long id : ids) { jdbc.update("DELETE FROM usuarios WHERE id = ?", id); }
        jdbc.update("DELETE FROM organizacoes WHERE id IN (?, ?)", orgA, orgB);
        if (Files.isDirectory(IMAGENS)) { try (var paths = Files.list(IMAGENS)) { for (Path p : paths.toList()) { Files.delete(p); } } }
    }

    @AfterAll
    static void limparDiretorio() throws Exception { Files.deleteIfExists(IMAGENS); }

    @Test
    void cadastraComZerosEsquerdaMesmoNomeEAuditoria() throws Exception {
        Map<String, Object> dados = dados("000123", catA);
        dados.put("nome", "  Café   500 g ");
        String body = criar(cadastro, dados).andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Café 500 g"))
                .andExpect(jsonPath("$.codigoBarras").value("000123"))
                .andExpect(jsonPath("$.ativo").value(true)).andExpect(jsonPath("$.versao").value(0))
                .andExpect(jsonPath("$.criadoPor").exists()).andExpect(jsonPath("$.imagemUrl").isEmpty())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        assertThat(jdbc.queryForObject("SELECT organizacao_id FROM produtos WHERE id = ?", Long.class, id)).isEqualTo(orgA);
        criar(cadastro, dados("000124", catA)).andExpect(status().isCreated());
    }

    @Test
    void codigoUnicoPorOrganizacaoInclusiveInativo() throws Exception {
        long id = criarId(admin, "123", catA);
        criar(admin, dados("123", catA)).andExpect(status().isConflict());
        criar(outroAdmin, dados("123", catB)).andExpect(status().isCreated());
        situacao(admin, id, 0, false).andExpect(status().isOk());
        criar(admin, dados("123", catA)).andExpect(status().isConflict());
        situacao(admin, id, 1, true).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void categoriaDeOutraEmpresaOuInativaNaoPodeSerAssociada() throws Exception {
        criar(admin, dados("123", catB)).andExpect(status().isNotFound());
        jdbc.update("UPDATE categorias SET ativa = false WHERE id = ?", catA);
        criar(admin, dados("123", catA)).andExpect(status().isConflict());
    }

    @Test
    void categoriaInativaExistentePodeSerMantidaMasNaoReceberNovoVinculo() throws Exception {
        long id = criarId(admin, "123", catA);
        jdbc.update("UPDATE categorias SET ativa = false WHERE id = ?", catA);
        var dados = dados("123", catA); dados.put("nome", "Renomeado");
        editar(edicao, id, 0, dados).andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Renomeado"));
        Long outra = inserir("categorias", Map.of("nome", "Outra", "organizacao_id", orgA, "ativa", false));
        editar(edicao, id, 1, dados("123", outra)).andExpect(status().isConflict());
    }

    @Test
    void validaObrigatoriosCodigoTextoEFiscal() throws Exception {
        for (String campo : List.of("nome", "codigoBarras", "categoriaId", "unidadeMedida")) {
            var dados = dados("123", catA); dados.remove(campo);
            criar(admin, dados).andExpect(status().isBadRequest());
        }
        for (String codigo : List.of("", "1 23", "ABC", "1".repeat(65))) {
            criar(admin, dados(codigo, catA)).andExpect(status().isBadRequest());
        }
        for (Map<String, ?> fiscal : List.<Map<String, ?>>of(Map.of("ncm", "123"), Map.of("cest", "123"),
                Map.of("icmsCst", "00", "icmsCsosn", "102"), Map.of("icmsAliquota", 101), Map.of("pisCst", "1"))) {
            var dados = dados("123", catA); dados.put("dadosFiscais", fiscal);
            criar(admin, dados).andExpect(status().isBadRequest());
        }
    }

    @Test
    void persisteDadosFiscaisOpcionaisSemPerderZeros() throws Exception {
        var dados = dados("123", catA);
        dados.put("dadosFiscais", Map.of("ncm", "09012100", "pisCst", "01", "cofinsCst", "01", "icmsCst", "00", "cfopPadrao", "5102", "icmsAliquota", 18));
        String body = criar(admin, dados).andExpect(status().isCreated())
                .andExpect(jsonPath("$.dadosFiscais.ncm").value("09012100"))
                .andExpect(jsonPath("$.dadosFiscais.icmsCst").value("00"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(get("/api/produtos/" + id).with(user(consulta))).andExpect(status().isOk())
                .andExpect(jsonPath("$.dadosFiscais.pisCst").value("01"));
    }

    @Test
    void filtraBuscaCodigoCategoriaESituacao() throws Exception {
        long id = criarId(admin, "000123", catA);
        criarId(admin, "999", catA); criarId(outroAdmin, "000123", catB);
        situacao(admin, id, 0, false).andExpect(status().isOk());
        mvc.perform(get("/api/produtos").with(user(consulta)).param("busca", "000").param("ativo", "false")
                        .param("categoriaId", catA.toString()).param("tamanho", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].codigoBarras").value("000123"));
        mvc.perform(get("/api/produtos/por-codigo/000123").with(user(consulta)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/produtos/opcoes").with(user(consulta))).andExpect(status().isOk())
                .andExpect(jsonPath("$.imagemMaxBytes").value(ImagemProdutoService.MAX_BYTES));
    }

    @Test
    void naoAcessaNemAlteraProdutoDeOutraOrganizacao() throws Exception {
        long id = criarId(outroAdmin, "123", catB);
        mvc.perform(get("/api/produtos/" + id).with(user(admin))).andExpect(status().isNotFound());
        mvc.perform(get("/api/produtos/por-codigo/123").with(user(admin))).andExpect(status().isNotFound());
        editar(admin, id, 0, dados("123", catA)).andExpect(status().isNotFound());
        situacao(admin, id, 0, false).andExpect(status().isNotFound());
        enviarImagem(admin, id, 0, png(1), "../../arquivo.png").andExpect(status().isNotFound());
        mvc.perform(get("/api/produtos/" + id + "/imagem").with(user(admin))).andExpect(status().isNotFound());
    }

    @Test
    void permissoesSaoVerificadasPorAcao() throws Exception {
        long id = criarId(admin, "123", catA);
        criar(consulta, dados("124", catA)).andExpect(status().isForbidden());
        editar(cadastro, id, 0, dados("123", catA)).andExpect(status().isForbidden());
        situacao(edicao, id, 0, false).andExpect(status().isForbidden());
        editar(edicao, id, 0, dados("123", catA)).andExpect(status().isOk());
        situacao(inativacao, id, 1, false).andExpect(status().isOk());
        mvc.perform(get("/api/produtos").with(user(semAcesso))).andExpect(status().isForbidden());
    }

    @Test
    void rejeitaCamposInjetadosEConflitosDeVersao() throws Exception {
        for (String campo : List.of("organizacaoId", "id", "ativo", "imagemUrl", "criadoPor")) {
            var dados = dados("123", catA); dados.put(campo, 123);
            criar(admin, dados).andExpect(status().isBadRequest());
        }
        long id = criarId(admin, "123", catA);
        editar(admin, id, 0, dados("124", catA)).andExpect(status().isOk()).andExpect(jsonPath("$.versao").value(1));
        editar(admin, id, 0, dados("125", catA)).andExpect(status().isConflict());
        situacao(admin, id, 0, false).andExpect(status().isConflict());
        mvc.perform(delete("/api/produtos/" + id).with(user(admin)).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void imagemEhValidadaRegravadaEProtegida() throws Exception {
        long id = criarId(admin, "123", catA);
        enviarImagem(edicao, id, 0, png(1), "../../arquivo.png").andExpect(status().isOk())
                .andExpect(jsonPath("$.versao").value(1)).andExpect(jsonPath("$.imagemUrl").exists());
        mvc.perform(get("/api/produtos/" + id + "/imagem").with(user(consulta)))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/produtos/" + id + "/imagem")).andExpect(status().isUnauthorized());
        enviarImagem(consulta, id, 1, png(1), "foto.png").andExpect(status().isForbidden());
        try (var paths = Files.list(IMAGENS)) { assertThat(paths.map(p -> p.getFileName().toString()).toList()).allMatch(n -> n.matches("[0-9a-f-]{36}\\.png")); }
    }

    @Test
    void bloqueiaArquivoFalsoGrandeEVersaoAntiga() throws Exception {
        long id = criarId(admin, "123", catA);
        enviarImagem(admin, id, 0, "<svg onload='alert(1)'/>".getBytes(), "foto.png").andExpect(status().isBadRequest());
        enviarImagem(admin, id, 0, new byte[ImagemProdutoService.MAX_BYTES + 1], "foto.png").andExpect(status().is(413));
        enviarImagem(admin, id, 1, png(1), "foto.png").andExpect(status().isConflict());
        mvc.perform(get("/api/produtos/" + id + "/imagem").with(user(admin))).andExpect(status().isNotFound());
    }

    @Test
    void substituiERemoveImagemSemDeixarArquivosAntigos() throws Exception {
        long id = criarId(admin, "123", catA);
        enviarImagem(admin, id, 0, png(1), "foto.png").andExpect(status().isOk());
        enviarImagem(admin, id, 1, png(2), "foto.png").andExpect(status().isOk());
        try (var paths = Files.list(IMAGENS)) { assertThat(paths.count()).isEqualTo(1); }
        enviar(admin, delete("/api/produtos/" + id + "/imagem").param("versao", "2"), null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.imagemUrl").isEmpty());
        try (var paths = Files.list(IMAGENS)) { assertThat(paths.count()).isZero(); }
    }

    @Test
    void rollbackDeImagemPreservaAnteriorELimpaNovoArquivo() throws Exception {
        long id = criarId(admin, "123", catA);
        enviarImagem(admin, id, 0, png(1), "foto.png").andExpect(status().isOk());
        byte[] antes = mvc.perform(get("/api/produtos/" + id + "/imagem").with(user(admin))).andReturn().getResponse().getContentAsByteArray();
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()));
        SecurityContextHolder.setContext(contexto);
        try {
            new TransactionTemplate(transacoes).executeWithoutResult(status -> {
                imagens.enviar(id, 1, new MockMultipartFile("arquivo", "foto.png", "image/png", png(2)));
                status.setRollbackOnly();
            });
        } finally { SecurityContextHolder.clearContext(); }
        mvc.perform(get("/api/produtos/" + id + "/imagem").with(user(admin))).andExpect(content().bytes(antes));
        try (var paths = Files.list(IMAGENS)) { assertThat(paths.count()).isEqualTo(1); }
    }

    @Test
    void exigeCsrfENaoVazaCorpoDeErros() throws Exception {
        mvc.perform(post("/api/produtos").with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(dados("123", catA)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/produtos").with(user(admin)).param("tamanho", "101")).andExpect(status().isBadRequest());
    }

    @Test
    void cadastrosConcorrentesRespeitamCodigoUnico() throws Exception {
        var codigos = concorrer(() -> criar(admin, dados("123", catA)).andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(201, 409);
    }

    @Test
    void alteracoesConcorrentesNaoSobrescrevemProduto() throws Exception {
        long id = criarId(admin, "123", catA);
        var codigos = concorrer(() -> editar(admin, id, 0, dados("124", catA)).andReturn().getResponse().getStatus());
        assertThat(codigos).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void rejeitaImagemComDimensoesExcessivasAntesDeDecodificar() throws Exception {
        long id = criarId(admin, "123", catA);
        var grande = new BufferedImage(2100, 2000, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(grande, "png", bytes);
        enviarImagem(admin, id, 0, bytes.toByteArray(), "grande.png").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT imagem_chave FROM produtos WHERE id = ?", String.class, id)).isNull();
    }

    @Test
    void jpegValidoEhServidoComoPngERequisicaoSemArquivoRetorna400() throws Exception {
        long id = criarId(admin, "123", catA);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", bytes);
        enviarImagem(admin, id, 0, bytes.toByteArray(), "foto.jpg").andExpect(status().isOk());
        byte[] imagem = mvc.perform(get("/api/produtos/" + id + "/imagem").with(user(consulta)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(imagem).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47);
        mvc.perform(multipart("/api/produtos/" + id + "/imagem").param("versao", "1")
                .with(r -> { r.setMethod("PUT"); return r; }).with(user(admin)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bancoTambemImpedeCategoriaDeOutraOrganizacao() throws Exception {
        long id = criarId(admin, "123", catA);
        assertThatThrownBy(() -> jdbc.update("UPDATE produtos SET categoria_id = ? WHERE id = ?", catB, id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT categoria_id FROM produtos WHERE id = ?", Long.class, id)).isEqualTo(catA);
    }

    @Test
    void armazenamentoIndisponivelNaoExcluiArquivoExistente() throws Exception {
        Files.createDirectories(IMAGENS);
        Path bloqueado = IMAGENS.resolve("bloqueado"); Files.writeString(bloqueado, "preservar");
        var armazenamento = new ImagensLocais(bloqueado.toString());
        assertThatThrownBy(() -> armazenamento.salvar(png(1)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .satisfies(e -> assertThat(((org.springframework.web.server.ResponseStatusException) e).getStatusCode().value()).isEqualTo(503));
        assertThat(Files.readString(bloqueado)).isEqualTo("preservar");
    }
    @Test
    void produtoCopiaClassificacaoDaCategoriaAoCriarETrocarCategoria() throws Exception {
        Long livre = inserir("categorias", Map.of("organizacao_id", orgA, "nome", "Livre", "permitido_para_menores", true));
        long id = criarId(cadastro, "000123", catA);
        mvc.perform(get("/api/produtos/" + id).with(user(consulta))).andExpect(status().isOk())
                .andExpect(jsonPath("$.permitidoParaMenores").value(false));
        criar(cadastro, dados("000124", livre)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.permitidoParaMenores").value(true));
        editar(edicao, id, 0, dados("000123", livre)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permitidoParaMenores").value(true));
        assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM produtos WHERE id = ?", Boolean.class, id)).isTrue();

        // A classificação do produto é uma cópia: renomear ou editar a mesma categoria não reclassifica o histórico.
        jdbc.update("UPDATE categorias SET permitido_para_menores = false WHERE id = ?", livre);
        editar(edicao, id, 1, dados("000123", livre)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permitidoParaMenores").value(true));
        editar(edicao, id, 2, dados("000123", catA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.permitidoParaMenores").value(false));
        assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM produtos WHERE id = ?", Boolean.class, id)).isFalse();
        mvc.perform(get("/api/produtos/por-codigo/000123").with(user(consulta))).andExpect(status().isOk())
                .andExpect(jsonPath("$.permitidoParaMenores").value(false));
    }

    @Test
    void classificacaoDoProdutoNaoPodeSerInjetadaNemCopiadaDeOutraOrganizacao() throws Exception {
        var dados = dados("123", catA);
        dados.put("permitidoParaMenores", true);
        criar(admin, dados).andExpect(status().isBadRequest());
        long id = criarId(admin, "123", catA);
        editar(admin, id, 0, dados).andExpect(status().isBadRequest());
        jdbc.update("UPDATE categorias SET permitido_para_menores = true WHERE id = ?", catB);
        editar(edicao, id, 0, dados("123", catB)).andExpect(status().isNotFound());
        editar(consulta, id, 0, dados("123", catA)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT permitido_para_menores FROM produtos WHERE id = ?", Boolean.class, id)).isFalse();
        assertThat(jdbc.queryForObject("SELECT categoria_id FROM produtos WHERE id = ?", Long.class, id)).isEqualTo(catA);
    }
    @Test
    void filtraPresencaDeImagemComTotalPaginacaoEIsolamento() throws Exception {
        long comImagem = criarId(admin, "0001", catA);
        long semImagem = criarId(admin, "0002", catA);
        long inativo = criarId(admin, "0003", catA);
        Long outraCategoria = inserir("categorias", Map.of("nome", "Outra categoria", "organizacao_id", orgA));
        long outroProduto = criarId(admin, "0004", outraCategoria);
        long outraOrganizacao = criarId(outroAdmin, "0005", catB);
        for (long id : List.of(comImagem, inativo, outroProduto)) {
            enviarImagem(admin, id, 0, png(1), "foto.png").andExpect(status().isOk());
        }
        enviarImagem(outroAdmin, outraOrganizacao, 0, png(1), "foto.png").andExpect(status().isOk());
        situacao(admin, inativo, 1, false).andExpect(status().isOk());

        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "true").param("tamanho", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.itens.length()").value(1)).andExpect(jsonPath("$.itens[0].id").value(comImagem));
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "true").param("tamanho", "1").param("pagina", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.itens[0].id").value(inativo));
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "false").header("X-Organizacao-Id", orgB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(semImagem));
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "true").param("ativo", "true")
                        .param("categoriaId", catA.toString()).param("busca", "Produto"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(comImagem));
        mvc.perform(get("/api/produtos").with(user(consulta))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
        mvc.perform(get("/api/produtos").param("comImagem", "true")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/produtos").with(user(semAcesso)).param("comImagem", "false")).andExpect(status().isForbidden());
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "talvez")).andExpect(status().isBadRequest());
    }

    @Test
    void filtroRefleteEnvioERemocaoDeImagem() throws Exception {
        long id = criarId(admin, "123", catA);
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        enviarImagem(edicao, id, 0, png(1), "foto.png").andExpect(status().isOk());
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].imagemUrl").value("/api/produtos/" + id + "/imagem?versao=1"));
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        enviar(edicao, delete("/api/produtos/" + id + "/imagem").param("versao", "1"), null).andExpect(status().isOk());
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/produtos").with(user(consulta)).param("comImagem", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.itens[0].id").value(id));
    }
    private List<Integer> concorrer(Callable<Integer> chamada) throws Exception {
        var inicio = new CountDownLatch(1);
        Callable<Integer> tarefa = () -> { inicio.await(); return chamada.call(); };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(tarefa); var b = executor.submit(tarefa); inicio.countDown();
            return List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        }
    }

    private byte[] png(int cor) {
        try {
            var imagem = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB); imagem.setRGB(0, 0, cor);
            var output = new ByteArrayOutputStream(); ImageIO.write(imagem, "png", output); return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private Map<String, Object> dados(String codigo, Long categoria) {
        return new HashMap<>(Map.of("nome", "Produto teste", "codigoBarras", codigo, "categoriaId", categoria, "unidadeMedida", "UN"));
    }

    private ResultActions criar(UserDetails ator, Map<String, Object> dados) throws Exception { return enviar(ator, post("/api/produtos"), dados); }
    private long criarId(UserDetails ator, String codigo, Long categoria) throws Exception {
        String body = criar(ator, dados(codigo, categoria)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
    private ResultActions editar(UserDetails ator, long id, long versao, Map<String, Object> dados) throws Exception {
        return enviar(ator, put("/api/produtos/" + id), Map.of("versao", versao, "dados", dados));
    }
    private ResultActions situacao(UserDetails ator, long id, long versao, boolean ativo) throws Exception {
        return enviar(ator, patch("/api/produtos/" + id + "/situacao"), Map.of("versao", versao, "ativo", ativo));
    }
    private ResultActions enviar(UserDetails ator, MockHttpServletRequestBuilder request, Map<String, ?> dados) throws Exception {
        if (dados != null) { request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(dados)); }
        return mvc.perform(request.with(user(ator)).with(csrf()));
    }
    private ResultActions enviarImagem(UserDetails ator, long id, long versao, byte[] bytes, String nome) throws Exception {
        return mvc.perform(multipart("/api/produtos/" + id + "/imagem").file(new MockMultipartFile("arquivo", nome, "image/png", bytes))
                .param("versao", Long.toString(versao)).with(r -> { r.setMethod("PUT"); return r; }).with(user(ator)).with(csrf()));
    }
}
