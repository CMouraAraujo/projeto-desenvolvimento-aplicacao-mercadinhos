package br.com.mercadinhos.catalogo.internal;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
class ImagensLocais implements ArmazenamentoImagens {
    private final Path raiz;

    ImagensLocais(@Value("${app.imagens.diretorio}") String diretorio) { raiz = Path.of(diretorio).toAbsolutePath().normalize(); }

    @Override
    public String salvar(byte[] png) {
        String chave = UUID.randomUUID() + ".png";
        Path destino = caminho(chave);
        boolean criado = false;
        try {
            Files.createDirectories(raiz);
            try (var output = Files.newOutputStream(destino, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                criado = true;
                output.write(png);
            }
            return chave;
        } catch (IOException exception) {
            if (criado) { try { Files.deleteIfExists(destino); } catch (IOException ignored) { } }
            throw indisponivel();
        }
    }

    @Override
    public byte[] ler(String chave) {
        Path arquivo = caminho(chave);
        if (!Files.isRegularFile(arquivo, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada");
        }
        try { return Files.readAllBytes(arquivo); } catch (IOException exception) { throw indisponivel(); }
    }

    @Override
    public void excluir(String chave) {
        if (chave == null) { return; }
        try { Files.deleteIfExists(caminho(chave)); } catch (IOException exception) { throw indisponivel(); }
    }

    private Path caminho(String chave) {
        if (chave == null || !chave.matches("[0-9a-f-]{36}\\.png")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada");
        }
        Path arquivo = raiz.resolve(chave).normalize();
        if (!arquivo.startsWith(raiz)) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        return arquivo;
    }

    private static ResponseStatusException indisponivel() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Armazenamento de imagens indisponível");
    }
}
