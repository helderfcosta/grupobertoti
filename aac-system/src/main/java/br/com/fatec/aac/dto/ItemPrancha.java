package br.com.fatec.aac.dto;

/**
 * Um item (cartão) da prancha de CAA devolvido ao front-end.
 *
 * @param palavra  palavra gerada pelo modelo
 * @param tipo     letra do tipo (v, s, a, e, l, p)
 * @param tipoNome nome do tipo por extenso (verbo, substantivo...)
 * @param sinonimo sinônimo sugerido
 * @param imagem   caminho/URL da imagem (null quando nenhuma fonte encontrou)
 * @param fonte    de onde veio a imagem: "arasaac", "ia-local" ou "nenhuma"
 */
public record ItemPrancha(
        String palavra,
        String tipo,
        String tipoNome,
        String sinonimo,
        String imagem,
        String fonte
) {
}
