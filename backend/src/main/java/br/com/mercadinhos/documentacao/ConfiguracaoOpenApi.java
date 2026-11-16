package br.com.mercadinhos.documentacao;

import java.util.List;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
class ConfiguracaoOpenApi {
    @Bean
    OpenAPI mercadinhosOpenApi() {
        return new OpenAPI()
                .info(new Info().title("API de Mercadinhos").version("v1")
                        .description("Ambiente local. Execute GET /api/auth/csrf e copie o token em Authorize > csrf. "
                                + "Faça login com email e senha; o navegador guarda o cookie HttpOnly automaticamente. "
                                + "Depois do login, obtenha um novo token e atualize Authorize antes do logout. "
                                + "Não é necessário preencher o campo sessao: o cookie é gerenciado pelo navegador."))
                .components(new Components()
                        .addSecuritySchemes("csrf", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER).name("X-CSRF-TOKEN")
                                .description("Token retornado por GET /api/auth/csrf. Atualize após login e logout."))
                        .addSecuritySchemes("sessao", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("JSESSIONID")
                                .description("Cookie HttpOnly automático após login. Não informe manualmente no Swagger.")));
    }

    @Bean
    OpenApiCustomizer endpointsDosFiltrosDeSeguranca() {
        return api -> {
            ObjectSchema formulario = new ObjectSchema();
            formulario.addProperty("email", new StringSchema().format("email").maxLength(254));
            formulario.addProperty("senha", new StringSchema().format("password").writeOnly(true).maxLength(72));
            formulario.setRequired(List.of("email", "senha"));
            api.path("/api/auth/login", new PathItem().post(new Operation()
                    .operationId("login").tags(List.of("Autenticação"))
                    .summary("Entrar com e-mail e senha")
                    .description("Exige token CSRF da sessão anônima. Recebe formulário, não JSON. "
                            + "No sucesso, rotaciona o ID da sessão e invalida o token CSRF anterior. "
                            + "A senha aceita no máximo 72 bytes em UTF-8. Limite: 30 tentativas por IP em 15 minutos.")
                    .security(List.of(new SecurityRequirement().addList("csrf")))
                    .requestBody(new RequestBody().required(true).content(new Content()
                            .addMediaType("application/x-www-form-urlencoded", new MediaType().schema(formulario))))
                    .responses(respostasLogin())));
            api.path("/api/auth/logout", new PathItem().post(new Operation()
                    .operationId("logout").tags(List.of("Autenticação"))
                    .summary("Encerrar a sessão")
                    .description("Exige token CSRF atual. Invalida a sessão existente e remove o cookie. "
                            + "Pode ser executado sem conta autenticada quando existe token CSRF válido.")
                    .security(List.of(new SecurityRequirement().addList("csrf")))
                    .responses(new ApiResponses()
                            .addApiResponse("204", resposta("Sessão encerrada"))
                            .addApiResponse("403", resposta("Token CSRF ausente ou inválido")))));
            api.getPaths().forEach((caminho, item) -> {
                if (caminho.startsWith("/api/usuarios")) {
                    for (var operacao : java.util.Arrays.asList(item.getPost(), item.getPut(), item.getPatch())) {
                        if (operacao != null) {
                            operacao.setSecurity(List.of(new SecurityRequirement().addList("sessao").addList("csrf")));
                        }
                    }
                }
            });
            var csrf = api.getPaths().get("/api/auth/csrf");
            if (csrf != null && csrf.getGet() != null) { csrf.getGet().setSecurity(List.of()); }
        };
    }

    private static ApiResponses respostasLogin() {
        return new ApiResponses()
                .addApiResponse("204", resposta("Login realizado; cookie de sessão atualizado"))
                .addApiResponse("401", resposta("Credenciais inválidas ou conta indisponível"))
                .addApiResponse("403", resposta("Token CSRF ausente ou inválido"))
                .addApiResponse("429", resposta("Limite de tentativas excedido; consulte Retry-After"));
    }

    private static ApiResponse resposta(String descricao) {
        return new ApiResponse().description(descricao);
    }
}
