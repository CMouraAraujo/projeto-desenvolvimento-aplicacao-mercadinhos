package br.com.mercadinhos;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class MercadinhosApplicationTests {
    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void migrationCreatesSchemaAndRecordsSuccessfulVersion() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM organizacoes", Integer.class)).isZero();
    }

    @Test
    void databasePreservesUnicodeNamesAndOrganizationDefaults() {
        jdbc.update("INSERT INTO organizacoes (nome) VALUES (?)", "Mercadinho São João 🛒");
        try {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT nome, ativa, versao, criado_em FROM organizacoes WHERE nome = ?",
                    "Mercadinho São João 🛒");
            assertThat(row.get("nome")).isEqualTo("Mercadinho São João 🛒");
            assertThat(row.get("ativa")).isEqualTo(true);
            assertThat(((Number) row.get("versao")).longValue()).isZero();
            assertThat(row.get("criado_em")).isNotNull();
        } finally {
            jdbc.update("DELETE FROM organizacoes WHERE nome = ?", "Mercadinho São João 🛒");
        }
    }

    @Test
    void healthIsPublicWithoutExposingDatabaseDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "jdbc", "password");
    }

    @Test
    void businessAndOtherManagementEndpointsAreNotPublic() throws Exception {
        for (String endpoint : new String[] {"/api/produtos", "/api/organizacoes", "/actuator/env", "/actuator"}) {
            assertThat(get(endpoint).statusCode()).as(endpoint).isEqualTo(401);
        }
    }

    @Test
    void modifyingRequestsWithoutCsrfAreRejectedEvenOnPublicHealthRoute() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void authenticatedUsersStillCannotAccessBusinessRoutesBeforeAuthorizationIsImplemented() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/produtos"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }
    @Test
    void productionSessionCookieHasHttpOnlySecureAndSameSite() throws Exception {
        HttpResponse<String> response = get("/api/auth/csrf");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Set-Cookie")).hasValueSatisfying(cookie ->
                assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/"));
    }

    @Test
    void documentacaoPermaneceBloqueadaSemPerfilLocal() throws Exception {
        for (String endpoint : new String[] {"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config"}) {
            assertThat(get(endpoint).statusCode()).as(endpoint).isEqualTo(401);
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administradorTambemNaoAcessaDocumentacaoForaDoPerfilLocal() throws Exception {
        for (String endpoint : new String[] {"/swagger-ui/index.html", "/v3/api-docs"}) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(endpoint))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        }
    }
    private HttpResponse<String> get(String endpoint) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + endpoint))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
