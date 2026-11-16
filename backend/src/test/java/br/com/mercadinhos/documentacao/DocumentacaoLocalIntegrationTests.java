package br.com.mercadinhos.documentacao;

import br.com.mercadinhos.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DocumentacaoLocalIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void especificacaoIncluiSomenteApiESemParametrosInternosDaSessao() throws Exception {
        String body = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API de Mercadinhos"))
                .andExpect(jsonPath("$.paths.length()").value(5))
                .andExpect(jsonPath("$.paths['/api/auth/csrf'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/organizacao'].get.security[0].sessao").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.parameters").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("senhaHash", "senha_hash", "SessaoUsuario", "Provisionamento");
    }

    @Test
    void documentaFormularioDeLoginLogoutECsrfSemEndpointsFicticios() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.requestBody.content['application/x-www-form-urlencoded'].schema.required.length()").value(2))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.requestBody.content['application/x-www-form-urlencoded'].schema.properties.senha.format").value("password"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security[0].csrf").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['429']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.responses['204']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.csrf.name").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.components.securitySchemes.sessao.in").value("cookie"));
    }

    @Test
    void interfaceEConfiguracaoDoSwaggerSaoPublicasNoPerfilLocal() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui-bundle.js")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.persistAuthorization").value(false));
    }

    @Test
    void swaggerNaoLiberaOperacoesDeNegocioNemDesativaCsrf() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/produtos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("email", "teste@example.invalid").param("senha", "teste"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isForbidden());
    }
}
