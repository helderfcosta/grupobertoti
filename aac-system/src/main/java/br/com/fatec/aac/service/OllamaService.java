package br.com.fatec.aac.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaService {

    @Value("${ollama.url}")
    private String ollamaUrl;

    public String gerarPrancha(String tema) {

        try {

            RestTemplate restTemplate = new RestTemplate();

            String instrucao = """
                    Você monta pranchas de CAA (pictogramas, pt-BR).
                     
                    Retorne exatamente 12 linhas.
                     
                    Cada linha deve seguir EXATAMENTE este padrão:
                     
                    palavra|tipo|sinonimos
                     
                    Onde tipo deve ser apenas uma destas letras:
                     
                    v = verbo
                    s = substantivo
                    a = adjetivo
                    e = expressão
                    l = local
                    p = pronome
                     
                    Exemplo:
                     
                    parquinho|l|playground
                    escorregador|s|escorrega
                    balançar|v|balanço
                    amigo|s|colega
                     
                    Não escreva títulos.
                    Não escreva explicações.
                    Não escreva texto fora das linhas.
                    """;

            String prompt =
                    "<start_of_turn>user\n" +
                            instrucao +
                            "\n\nPEDIDO: monta uma prancha de " +
                            tema +
                            "<end_of_turn>\n" +
                            "<start_of_turn>model\n";

            Map<String, Object> body = new HashMap<>();

            body.put(
                    "model",
                    "hf.co/tardellirs/aac-board-generator-770m-ptbr-GGUF:Q8_0"
            );

            body.put("prompt", prompt);
            body.put("stream", false);
            body.put("temperature", 0);
            body.put("num_predict", 320);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(body, headers);

            ResponseEntity<Map> response =
                    restTemplate.postForEntity(
                            ollamaUrl,
                            request,
                            Map.class
                    );

            String resposta =
                    response.getBody()
                            .get("response")
                            .toString();

            return corrigirSinonimos(resposta);

        } catch (Exception e) {

            return "ERRO: Não foi possível comunicar com o Ollama.";

        }
    }

    private String corrigirSinonimos(String texto) {

        String[] linhas = texto.split("\n");

        StringBuilder resultado =
                new StringBuilder();

        for (String linha : linhas) {

            String[] partes =
                    linha.split("\\|");

            if (partes.length == 3) {

                String palavra = partes[0].trim();
                String tipo = partes[1].trim();
                String sinonimo = partes[2].trim();

                if (sinonimo.equalsIgnoreCase(palavra)) {

                    sinonimo = buscarSinonimo(palavra);

                }

                resultado.append(
                        palavra + "|" +
                                tipo + "|" +
                                sinonimo + "\n"
                );
            }
        }

        return resultado.toString();
    }

    private String buscarSinonimo(String palavra) {

        return switch (palavra.toLowerCase()) {

            case "brincar" -> "divertir";

            case "parquinho" -> "playground";

            case "amigo" -> "colega";

            case "escorregador" -> "escorrega";

            case "comer" -> "alimentar";

            case "beber" -> "ingerir";

            case "correr" -> "disparar";

            case "bola" -> "bolinha";

            case "criança" -> "menino";

            default -> palavra;
        };
    }
}