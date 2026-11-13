package br.com.fatec.aac.dto;

/**
 * Modo de geração das imagens, escolhido no menu:
 *
 *  ONLINE  - ARASAAC é a fonte principal; a IA local (Stable Diffusion) só entra
 *            quando o ARASAAC não tem a imagem.
 *  OFFLINE - somente a IA local (Stable Diffusion); o ARASAAC não é usado.
 */
public enum ModoImagem {
    ONLINE,
    OFFLINE;

    /** Qualquer valor diferente de "offline" (inclusive vazio/nulo) vira ONLINE. */
    public static ModoImagem de(String valor) {
        return valor != null && valor.trim().equalsIgnoreCase("offline") ? OFFLINE : ONLINE;
    }
}
