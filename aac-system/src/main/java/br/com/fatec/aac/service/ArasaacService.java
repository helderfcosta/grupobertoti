package br.com.fatec.aac.service;

import br.com.fatec.aac.util.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Busca pictogramas prontos no ARASAAC (https://arasaac.org).
 *
 * Pictogramas: ARASAAC (Governo de Aragão), licença CC BY-NC-SA — uso não comercial,
 * com atribuição.
 *
 * Cache em disco (pasta definida em cache.dir):
 *  - arasaac-ids.properties : palavra -> id do pictograma
 *  - arasaac/{id}.png       : imagem baixada
 * Depois que uma palavra foi buscada uma vez, ela funciona sem internet.
 */
@Service
public class ArasaacService {

    private static final Logger log = LoggerFactory.getLogger(ArasaacService.class);

    private final RestTemplate restTemplate;
    private final String apiUrl;
    private final String imagemUrl;
    private final Path pastaImagens;
    private final Path arquivoIds;

    /** palavra normalizada -> id do pictograma */
    private final Map<String, Integer> cacheIds = new ConcurrentHashMap<>();

    /** Enquanto a rede estiver fora, não tenta de novo (evita esperar timeout a cada palavra). */
    private volatile long redeIndisponivelAte = 0;

    public ArasaacService(
            RestTemplate restTemplate,
            @Value("${arasaac.api.url:https://api.arasaac.org/v1/pictograms/pt/search/}") String apiUrl,
            @Value("${arasaac.imagem.url:https://static.arasaac.org/pictograms/%d/%d_500.png}") String imagemUrl,
            @Value("${cache.dir:./cache}") String cacheDir) {
        this.restTemplate = restTemplate;
        this.apiUrl = apiUrl;
        this.imagemUrl = imagemUrl;
        this.pastaImagens = Path.of(cacheDir, "arasaac");
        this.arquivoIds = Path.of(cacheDir, "arasaac-ids.properties");
        carregarCacheIds();
    }

    /**
     * Devolve o endereço da imagem do pictograma para a palavra, ou vazio se não houver.
     * O endereço é local (/imagens/arasaac/{id}.png) quando a imagem está no cache.
     */
    public Optional<String> buscarImagem(String palavra) {
        String chave = Textos.normalizar(palavra);
        if (chave.isEmpty()) {
            return Optional.empty();
        }

        Integer id = cacheIds.get(chave);
        if (id == null) {
            Optional<Integer> encontrado = pesquisarId(palavra.trim());
            if (encontrado.isEmpty()) {
                return Optional.empty();
            }
            id = encontrado.get();
            cacheIds.put(chave, id);
            salvarCacheIds();
        }
        return localizarImagem(id);
    }

    // ---------------------------------------------------------------- busca

    private Optional<Integer> pesquisarId(String palavra) {
        if (System.currentTimeMillis() < redeIndisponivelAte) {
            return Optional.empty();
        }
        try {
            List<Map<String, Object>> resultados = restTemplate.exchange(
                    apiUrl + "{palavra}",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    },
                    palavra).getBody();
            return escolherMelhor(palavra, resultados);
        } catch (ResourceAccessException e) {
            // sem internet / timeout: pausa as tentativas por 30 s
            redeIndisponivelAte = System.currentTimeMillis() + 30_000;
            log.warn("ARASAAC inacessível ({}). Pausando buscas por 30 s.", e.getMessage());
            return Optional.empty();
        } catch (RestClientException e) {
            // 404 etc.: palavra sem resultado
            log.info("ARASAAC sem resultado para '{}': {}", palavra, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * A busca do ARASAAC é por aproximação ("bola" traz "bola de tênis", "perder a bola"...).
     * Pontuação: palavra exata = 100; keyword que começa com a palavra + espaço = 50;
     * +10 se o pictograma é marcado como CAA (aac = true). Empate: vale o primeiro da lista.
     */
    static Optional<Integer> escolherMelhor(String palavra, List<Map<String, Object>> resultados) {
        if (resultados == null) {
            return Optional.empty();
        }
        String alvo = Textos.normalizar(palavra);
        int melhorPontos = 0;
        Integer melhorId = null;

        for (Map<String, Object> r : resultados) {
            if (!(r.get("_id") instanceof Number numero)) {
                continue;
            }
            int pontos = pontuarKeywords(alvo, r.get("keywords"));
            if (pontos == 0) {
                continue;
            }
            if (Boolean.TRUE.equals(r.get("aac"))) {
                pontos += 10;
            }
            if (pontos > melhorPontos) {
                melhorPontos = pontos;
                melhorId = numero.intValue();
            }
        }
        return Optional.ofNullable(melhorId);
    }

    private static int pontuarKeywords(String alvo, Object keywords) {
        if (!(keywords instanceof List<?> lista)) {
            return 0;
        }
        int melhor = 0;
        for (Object item : lista) {
            if (item instanceof Map<?, ?> mapa && mapa.get("keyword") instanceof String kw) {
                String k = Textos.normalizar(kw);
                if (k.equals(alvo)) {
                    melhor = Math.max(melhor, 100);
                } else if (k.startsWith(alvo + " ")) {
                    melhor = Math.max(melhor, 50);
                }
            }
        }
        return melhor;
    }

    // --------------------------------------------------------------- imagem

    private Optional<String> localizarImagem(int id) {
        Path destino = pastaImagens.resolve(id + ".png");
        String urlLocal = "/imagens/arasaac/" + id + ".png";
        if (Files.isRegularFile(destino)) {
            return Optional.of(urlLocal);
        }

        String urlRemota = String.format(imagemUrl, id, id);
        try {
            byte[] bytes = restTemplate.getForObject(urlRemota, byte[].class);
            if (!pareceUmPng(bytes)) {
                log.warn("Resposta de {} não é um PNG.", urlRemota);
                return Optional.empty();
            }
            Files.createDirectories(pastaImagens);
            Files.write(destino, bytes);
            return Optional.of(urlLocal);
        } catch (ResourceAccessException e) {
            redeIndisponivelAte = System.currentTimeMillis() + 30_000;
            log.warn("Não foi possível baixar {}: {}. Usando o link remoto.", urlRemota, e.getMessage());
            return Optional.of(urlRemota); // o navegador ainda pode conseguir carregar
        } catch (RestClientException | IOException e) {
            log.warn("Falha ao obter imagem {}: {}", urlRemota, e.getMessage());
            return Optional.empty();
        }
    }

    private static boolean pareceUmPng(byte[] b) {
        return b != null && b.length > 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    // ---------------------------------------------------------- cache de ids

    private void carregarCacheIds() {
        if (!Files.isRegularFile(arquivoIds)) {
            return;
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(arquivoIds, StandardCharsets.UTF_8)) {
            p.load(r);
            for (String chave : p.stringPropertyNames()) {
                try {
                    cacheIds.put(chave, Integer.parseInt(p.getProperty(chave).trim()));
                } catch (NumberFormatException ignorado) {
                    // linha inválida: ignora
                }
            }
            log.info("Cache do ARASAAC carregado: {} palavras.", cacheIds.size());
        } catch (IOException e) {
            log.warn("Não foi possível ler {}: {}", arquivoIds, e.getMessage());
        }
    }

    private synchronized void salvarCacheIds() {
        Properties p = new Properties();
        cacheIds.forEach((k, v) -> p.setProperty(k, String.valueOf(v)));
        try {
            Files.createDirectories(arquivoIds.toAbsolutePath().getParent());
            try (Writer w = Files.newBufferedWriter(arquivoIds, StandardCharsets.UTF_8)) {
                p.store(w, "palavra -> id do pictograma (ARASAAC)");
            }
        } catch (IOException e) {
            log.warn("Não foi possível salvar {}: {}", arquivoIds, e.getMessage());
        }
    }
}
