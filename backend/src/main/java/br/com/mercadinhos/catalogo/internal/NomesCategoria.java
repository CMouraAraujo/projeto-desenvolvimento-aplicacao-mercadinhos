package br.com.mercadinhos.catalogo.internal;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class NomesCategoria {
    private static final Pattern ESPACOS = Pattern.compile("[\\p{javaWhitespace}\\p{Zs}]+");

    private NomesCategoria() { }

    static String normalizar(String nome) {
        return ESPACOS.matcher(Normalizer.normalize(nome, Normalizer.Form.NFC)).replaceAll(" ").strip();
    }

    static String filtroLiteral(String busca) {
        if (busca == null) { return null; }
        String normalizada = normalizar(busca);
        if (normalizada.isEmpty()) { return null; }
        return "%" + normalizada.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
