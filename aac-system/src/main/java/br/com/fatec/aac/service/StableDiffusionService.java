package br.com.fatec.aac.service;

import br.com.fatec.aac.util.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Geração local de imagens com stable-diffusion.cpp (Stable Diffusion 1.5), em CPU.
 *
 * DESLIGADO por padrão (sd.enabled=false). Só entra na cadeia se for ligado no
 * application.properties e depois de o teste isolado do executável funcionar.
 * Gera uma imagem por vez (lock) para não estourar a memória e guarda o resultado
 * em cache/ia/{palavra}.png.
 */
@Service
public class StableDiffusionService {

    private static final Logger log = LoggerFactory.getLogger(StableDiffusionService.class);

    private final boolean habilitado;
    private final String exe;
    private final String modelo;
    private final int passos;
    private final String cfgScale;
    private final int timeoutSegundos;
    private final Path pastaImagens;

    private final ReentrantLock lock = new ReentrantLock();

    public StableDiffusionService(
            @Value("${sd.enabled:false}") boolean habilitado,
            @Value("${sd.exe:}") String exe,
            @Value("${sd.model:}") String modelo,
            @Value("${sd.steps:20}") int passos,
            @Value("${sd.cfg-scale:}") String cfgScale,
            @Value("${sd.timeout-seconds:180}") int timeoutSegundos,
            @Value("${cache.dir:./cache}") String cacheDir) {
        this.habilitado = habilitado;
        this.exe = exe;
        this.modelo = modelo;
        this.passos = passos;
        this.cfgScale = cfgScale == null ? "" : cfgScale;
        this.timeoutSegundos = timeoutSegundos;
        this.pastaImagens = Path.of(cacheDir, "ia");
    }

    /** Descrições em inglês das palavras mais comuns. Palavra fora da lista segue em português. */
    private static final java.util.Map<String, String> EN = java.util.Map.ofEntries(
            // festa
            java.util.Map.entry("festa", "a party hat with confetti"),
            java.util.Map.entry("bolo", "a birthday cake with candles"),
            java.util.Map.entry("presente", "a gift box with a ribbon"),
            java.util.Map.entry("comemorar", "a happy child celebrating with arms up"),
            java.util.Map.entry("dancar", "a person dancing"),
            java.util.Map.entry("cantar", "a person singing with a microphone"),
            java.util.Map.entry("brinquedo", "a toy teddy bear"),
            java.util.Map.entry("brinquedos", "a toy teddy bear"),
            java.util.Map.entry("brincar", "two children playing"),
            java.util.Map.entry("balao", "a red balloon"),
            java.util.Map.entry("baloes", "a red balloon"),
            java.util.Map.entry("comidinhas", "a plate with snacks"),
            java.util.Map.entry("comida", "a plate of food"),
            java.util.Map.entry("comer", "a person eating"),
            java.util.Map.entry("beber", "a person drinking from a glass"),
            java.util.Map.entry("suco", "a glass of orange juice"),
            java.util.Map.entry("agua", "a glass of water"),
            java.util.Map.entry("refrigerante", "a bottle of soda"),
            java.util.Map.entry("doce", "a piece of candy"),
            java.util.Map.entry("docinho", "a piece of candy"),
            java.util.Map.entry("biscoito", "a cookie"),
            java.util.Map.entry("pizza", "a slice of pizza"),
            java.util.Map.entry("cores", "a paint palette"),
            java.util.Map.entry("musica", "a music note"),
            java.util.Map.entry("amigo", "two friends hugging"),
            java.util.Map.entry("amigo(a)", "two friends hugging"),
            java.util.Map.entry("amiga", "two friends hugging"),
            java.util.Map.entry("familia", "a family of four"),
            // emoções
            java.util.Map.entry("feliz", "a smiling happy face"),
            java.util.Map.entry("alegre", "a smiling happy face"),
            java.util.Map.entry("triste", "a sad crying face"),
            java.util.Map.entry("tristeza", "a sad crying face"),
            java.util.Map.entry("saudade", "a sad face looking at a heart"),
            java.util.Map.entry("bravo", "an angry red face"),
            java.util.Map.entry("medo", "a scared face"),
            java.util.Map.entry("legal", "a thumbs up"),
            java.util.Map.entry("bom", "a thumbs up"),
            java.util.Map.entry("mais", "a plus sign"),
            java.util.Map.entry("menos", "a minus sign"),
            java.util.Map.entry("sim", "a green check mark"),
            java.util.Map.entry("nao", "a red cross"),
            // ações
            java.util.Map.entry("correr", "a person running"),
            java.util.Map.entry("pular", "a person jumping"),
            java.util.Map.entry("dormir", "a person sleeping in a bed"),
            java.util.Map.entry("ler", "a person reading a book"),
            java.util.Map.entry("escrever", "a hand writing with a pencil"),
            java.util.Map.entry("desenhar", "a hand drawing with a crayon"),
            java.util.Map.entry("pintar", "a paint brush painting"),
            java.util.Map.entry("lavar", "hands being washed with soap"),
            java.util.Map.entry("abracar", "two people hugging"),
            java.util.Map.entry("ajudar", "a helping hand"),
            java.util.Map.entry("querer", "a hand reaching out"),
            java.util.Map.entry("ir", "an arrow pointing right"),
            // lugares e objetos
            java.util.Map.entry("casa", "a house"),
            java.util.Map.entry("escola", "a school building"),
            java.util.Map.entry("parquinho", "a playground slide"),
            java.util.Map.entry("parque", "a tree in a park"),
            java.util.Map.entry("bola", "a soccer ball"),
            java.util.Map.entry("banheiro", "a toilet"),
            java.util.Map.entry("livro", "a book"),
            java.util.Map.entry("lapis", "a pencil"),
            java.util.Map.entry("mesa", "a table"),
            java.util.Map.entry("cadeira", "a chair"),
            java.util.Map.entry("carro", "a car"),
            java.util.Map.entry("cachorro", "a dog"),
            java.util.Map.entry("gato", "a cat"),
            java.util.Map.entry("sol", "a sun"),
            java.util.Map.entry("chuva", "a rain cloud"),
            java.util.Map.entry("caixa", "a cardboard box"),
            java.util.Map.entry("baterista", "a drum set"),
            java.util.Map.entry("bateria", "a drum set"),
            java.util.Map.entry("surpresa", "a gift box with a question mark"),
            java.util.Map.entry("cumprimentar", "a hand waving hello"),
            java.util.Map.entry("torta", "a slice of cake"),
            java.util.Map.entry("caneca", "a coffee mug"),
            java.util.Map.entry("doces", "a piece of candy"),
            java.util.Map.entry("convidado", "a person with an invitation card"),
            java.util.Map.entry("convite", "an invitation card"),
            java.util.Map.entry("vela", "a lit candle"),
            java.util.Map.entry("palhaco", "a clown face"),
            java.util.Map.entry("fantasia", "a costume mask"),
            java.util.Map.entry("sorvete", "an ice cream cone"),
            java.util.Map.entry("fruta", "an apple"),
            java.util.Map.entry("leite", "a glass of milk"),
            java.util.Map.entry("pao", "a loaf of bread"),
            java.util.Map.entry("professor", "a teacher at a blackboard"),
            java.util.Map.entry("mochila", "a school backpack"),
            java.util.Map.entry("jogar", "a video game controller"),
            java.util.Map.entry("cha", "a cup of tea with a tea bag"),
            java.util.Map.entry("cafe", "a cup of coffee"),
            java.util.Map.entry("lanche", "a sandwich"),
            java.util.Map.entry("guardanapo", "a paper napkin"),
            java.util.Map.entry("cerveja", "a glass of beer"),
            java.util.Map.entry("risada", "a laughing face"),
            java.util.Map.entry("tchau", "a hand waving goodbye"),
            java.util.Map.entry("lar", "a house"),
            java.util.Map.entry("salgado", "a fried snack pastry"),
            java.util.Map.entry("salgados", "a fried snack pastry"),
            java.util.Map.entry("parabens", "a party popper with confetti"),
            java.util.Map.entry("comemoracao", "a party popper with confetti"),
            java.util.Map.entry("companhia", "two friends together"),
            java.util.Map.entry("gelado", "an ice cream cone"),
            java.util.Map.entry("engracado", "a laughing face"),
            java.util.Map.entry("divertido", "a laughing face"));

    private static String emIngles(String palavra) {
        String chave = Textos.normalizar(palavra);
        return EN.getOrDefault(chave, palavra.trim());
    }

    public boolean habilitado() {
        return habilitado && !exe.isBlank() && !modelo.isBlank();
    }

    /** Gera (ou reaproveita do cache) a imagem da palavra. Vazio se falhar ou estiver desligado. */
    public Optional<String> gerar(String palavra) {
        String slug = Textos.slug(palavra);
        if (slug.isEmpty()) {
            return Optional.empty();
        }

        Path destino = pastaImagens.resolve(slug + ".png");
        String urlLocal = "/imagens/ia/" + slug + ".png";

        // Imagem já existente no cache vale mesmo com o motor desligado
        // (permite copiar imagens pré-geradas para cache/ia/ em outro computador).
        if (Files.isRegularFile(destino)) {
            return Optional.of(urlLocal);
        }
        if (!habilitado()) {
            return Optional.empty();
        }

        lock.lock();
        try {
            if (Files.isRegularFile(destino)) { // outra requisição pode ter gerado enquanto esperávamos
                return Optional.of(urlLocal);
            }
            Files.createDirectories(pastaImagens);

            // O Stable Diffusion 1.5 não entende português: a palavra vai traduzida para o inglês.
            // (a palavra "pictogram" faz o modelo desenhar grades de ícones, por isso não é usada)
            // Mesmo prompt validado no teste manual (bolo, suco): "icon" fazia o SD desenhar grades.
            String prompt = "A simple colorful educational pictogram of: " + emIngles(palavra)
                    + ", single object centered, clean flat vector illustration, white background, "
                    + "thick clear outlines, no text, no letters, no shadows, no extra objects";

            // Sem shell: cada argumento é passado separado (a palavra não vira comando).
            List<String> comando = new ArrayList<>(List.of(
                    exe,
                    "-m", modelo,
                    "-p", prompt,
                    "-n", "dark background, black background, multiple objects, many items, fruits around, "
                            + "text, letters, watermark, photo, realistic, shadow, border, frame",
                    "--steps", String.valueOf(passos),
                    "-o", destino.toAbsolutePath().toString()));
            if (!cfgScale.isBlank()) {
                // SD-Turbo pede cfg-scale 1; no SD 1.5 deixe vazio (usa o padrão do motor)
                comando.add("--cfg-scale");
                comando.add(cfgScale.trim());
            }

            Process processo = new ProcessBuilder(comando)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();

            if (!processo.waitFor(timeoutSegundos, TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                log.warn("Geração de '{}' passou de {} s e foi cancelada.", palavra, timeoutSegundos);
                return Optional.empty();
            }
            if (processo.exitValue() != 0 || !Files.isRegularFile(destino)) {
                log.warn("stable-diffusion.cpp terminou com código {} para '{}'.",
                        processo.exitValue(), palavra);
                return Optional.empty();
            }
            return Optional.of(urlLocal);

        } catch (IOException e) {
            log.warn("Não foi possível executar {}: {}", exe, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }
}
