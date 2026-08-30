import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.OllamaResult;
import io.github.ollama4j.utils.Options;
import io.github.ollama4j.utils.OptionsBuilder;

import java.util.*;

public class Main {

    private static final Set<String> TIPOS_VALIDOS = Set.of("v", "s", "a", "e", "l", "p");
    private static final String MODELO_GERADOR = "hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q8_0";
    private static final String MODELO_EMBEDDINGS = "embeddinggemma";

    // Catálogo simplificado de pictogramas (demonstração — o real seria o catálogo ARASAAC completo)
    private static final List<String> CATALOGO_PICTOGRAMAS = List.of(
            "brincar", "escorregador", "balanço", "areia", "correr", "pular",
            "amigo", "balde", "cantar", "água", "comer", "beber", "banheiro",
            "dormir", "escovar os dentes", "roupa", "sapato", "carro", "escola",
            "professor", "livro", "feliz", "triste", "com raiva", "cansado",
            "ajuda", "sim", "não", "obrigado", "por favor"
    );

    public static void main(String[] args) throws Exception {

        OllamaAPI ollamaAPI = new OllamaAPI("http://localhost:11434");
        ollamaAPI.setRequestTimeoutSeconds(180);

        // ---- Pedido dinâmico via entrada do usuário ----
        Scanner scanner = new Scanner(System.in);
        System.out.print("Digite o pedido da prancha (ex: monta uma prancha de brincar no parquinho): ");
        String pedido = scanner.nextLine();

        String instrucao = "Você monta pranchas de CAA (pictogramas, pt-BR). "
                + "Para o PEDIDO, liste ~12 itens concretos e relevantes, "
                + "um por linha, EXATAMENTE no formato palavra|tipo|sinonimos, "
                + "onde tipo é OBRIGATORIAMENTE uma destas letras: v (verbo), s (substantivo), "
                + "a (adjetivo), e (expressão), l (locução), p (pronome). "
                + "Exemplo de linha correta: escorregador|s|tobogã. "
                + "Nunca repita a palavra no campo tipo. Só a lista.";

        String prompt = "<start_of_turn>user\n" + instrucao + "\n\nPEDIDO: " + pedido
                + "<end_of_turn>\n<start_of_turn>model\n";

        Options opcoes = new OptionsBuilder().setTemperature(0.0f).build();

        System.out.println("\nGerando prancha, aguarde...");
        OllamaResult resultado = ollamaAPI.generate(MODELO_GERADOR, prompt, false, opcoes);

        // ---- Pré-calcula os embeddings do catálogo de pictogramas (uma vez) ----
        Map<String, List<Double>> embeddingsCatalogo = new LinkedHashMap<>();
        for (String pictograma : CATALOGO_PICTOGRAMAS) {
            List<Double> vetorPictograma = ollamaAPI.generateEmbeddings(MODELO_EMBEDDINGS, pictograma);
            embeddingsCatalogo.put(pictograma, vetorPictograma);
        }

        // ---- Processa cada item gerado: valida o tipo + acha o pictograma mais parecido ----
        System.out.println("\n--- Prancha gerada ---");
        for (String linha : resultado.getResponse().split("\n")) {
            linha = linha.replaceFirst("^[\\*\\-]\\s*", "").trim();
            if (linha.isEmpty()) continue;

            String[] partes = linha.split("\\|");
            if (partes.length < 2) continue;

            String palavra = partes[0].trim();
            String tipo = normalizarTipo(partes[1]);
            String sinonimos = partes.length > 2 ? partes[2].trim() : "";

            List<Double> vetorPalavra = ollamaAPI.generateEmbeddings(MODELO_EMBEDDINGS, palavra);

            String melhorPictograma = null;
            double melhorSimilaridade = -1.0;
            for (Map.Entry<String, List<Double>> entrada : embeddingsCatalogo.entrySet()) {
                double similaridade = similaridadeCosseno(vetorPalavra, entrada.getValue());
                if (similaridade > melhorSimilaridade) {
                    melhorSimilaridade = similaridade;
                    melhorPictograma = entrada.getKey();
                }
            }

            System.out.printf("%s|%s|%s -> pictograma sugerido: %s (similaridade: %.2f)%n",
                    palavra, tipo, sinonimos, melhorPictograma, melhorSimilaridade);
        }
    }

    private static String normalizarTipo(String tipoBruto) {
        String tipo = tipoBruto.trim().toLowerCase();
        return TIPOS_VALIDOS.contains(tipo) ? tipo : "s";
    }

    private static double similaridadeCosseno(List<Double> vetorA, List<Double> vetorB) {
        double produtoInterno = 0.0, normaA = 0.0, normaB = 0.0;
        for (int i = 0; i < vetorA.size(); i++) {
            produtoInterno += vetorA.get(i) * vetorB.get(i);
            normaA += Math.pow(vetorA.get(i), 2);
            normaB += Math.pow(vetorB.get(i), 2);
        }
        return produtoInterno / (Math.sqrt(normaA) * Math.sqrt(normaB));
    }
}