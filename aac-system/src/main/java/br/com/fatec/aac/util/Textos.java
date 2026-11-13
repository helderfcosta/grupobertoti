package br.com.fatec.aac.util;

import java.text.Normalizer;
import java.util.Locale;

public final class Textos {

    private Textos() {
    }

    /** minúsculas, sem acentos e sem espaços nas pontas ("Criança " -> "crianca"). */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT);
    }

    /** Nome seguro para arquivo: só a-z, 0-9 e hífen ("balançar!" -> "balancar"). */
    public static String slug(String texto) {
        String s = normalizar(texto).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.length() > 40 ? s.substring(0, 40) : s;
    }
}
