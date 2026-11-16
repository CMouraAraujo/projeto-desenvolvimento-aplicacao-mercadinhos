package br.com.mercadinhos.identidade.internal;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

record NovoFuncionario(
        @NotBlank @Size(max = 160) String nome,
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[\\x00-\\x7F]+") String email,
        @NotBlank @Size(min = 12, max = 72)
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "Senha inicial de 12 a 72 caracteres, no máximo 72 bytes UTF-8") String senha,
        @NotNull @Size(max = 100) Set<@NotNull @Valid PermissaoDTO> permissoes) {
    @AssertTrue(message = "Senha deve ter no máximo 72 bytes UTF-8")
    @JsonIgnore
    public boolean isSenhaCompativel() {
        return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() { return "NovoFuncionario[conteudo protegido]"; }
}
