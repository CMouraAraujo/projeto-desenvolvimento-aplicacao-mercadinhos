package br.com.mercadinhos.identidade.internal;

import java.util.Arrays;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("provisionamento")
@RequiredArgsConstructor
class ProvisionamentoCommand implements ApplicationRunner {
    private final ProvisionamentoService provisionamento;
    private final ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments arguments) {
        var console = System.console();
        if (console == null) {
            throw new IllegalStateException("Execute o JAR em um terminal interativo para provisionar sem expor a senha.");
        }
        String organizacao = console.readLine("Organização: ");
        String administrador = console.readLine("Nome do administrador: ");
        String email = console.readLine("E-mail: ");
        char[] senha = console.readPassword("Senha (12 a 72 caracteres, no máximo 72 bytes): ");
        if (senha == null) { throw new IllegalStateException("Entrada cancelada."); }
        try {
            Long id = provisionamento.provisionar(new CadastroOrganizacao(organizacao, administrador, email, new String(senha)));
            console.printf("Organização criada com ID %d. Administrador pronto para entrar.%n", id);
        } catch (ConstraintViolationException exception) {
            // Never print rejected values or the registration DTO.
            throw new IllegalArgumentException("Cadastro inválido. Confira nomes, e-mail e tamanho da senha.");
        } finally {
            Arrays.fill(senha, '\0');
            context.close();
        }
    }
}
