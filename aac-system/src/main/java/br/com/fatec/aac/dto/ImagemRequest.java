package br.com.fatec.aac.dto;

/**
 * Pedido de imagem para UMA palavra (usado pelo front-end para carregar
 * as imagens da prancha uma a uma e mostrar o progresso).
 *
 * @param palavra  palavra do cartão
 * @param sinonimo sinônimo (opcional; usado na segunda tentativa do ARASAAC)
 * @param modo     "online" (padrão) ou "offline"
 */
public record ImagemRequest(String palavra, String sinonimo, String modo) {
}
