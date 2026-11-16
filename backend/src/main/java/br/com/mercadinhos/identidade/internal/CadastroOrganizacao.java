package br.com.mercadinhos.identidade.internal;

import java.nio.charset.StandardCharsets;
import jakarta.validation.constraints.*;

record CadastroOrganizacao(
        @NotBlank @Size(max = 160) String organizacao,
        @NotBlank @Size(max = 160) String administrador,
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "[\\x00-\\x7F]+") String email,
        @NotBlank @Size(min = 12, max = 72) String senha) {
    @AssertTrue(message = "A senha deve ter no máximo 72 bytes em UTF-8")
    public boolean isSenhaCompativel() {
        return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() { return "CadastroOrganizacao[conteudo protegido]"; }
}
