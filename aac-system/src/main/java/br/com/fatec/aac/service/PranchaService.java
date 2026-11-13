package br.com.fatec.aac.service;

import br.com.fatec.aac.dto.ItemPrancha;
import br.com.fatec.aac.dto.ModoImagem;
import br.com.fatec.aac.util.Textos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orquestra a prancha: pede as palavras ao Ollama (OllamaService, sem alterações)
 * e depois busca a imagem de cada palavra (ImagemService).
 */
@Service
public class PranchaService {

    private static final Logger log = LoggerFactory.getLogger(PranchaService.class);

    private static final Map<String, String> TIPOS = Map.of(
            "v", "verbo",
            "s", "substantivo",
            "a", "adjetivo",
            "e", "expressão",
            "l", "local",
            "p", "pronome");

    private final OllamaService ollamaService;
    private final ImagemService imagemService;
    /** Onde as listas de palavras de cada tema ficam guardadas (null = sem cache). */
    private final Path pastaPranchas;

    @Autowired
    public PranchaService(OllamaService ollamaService, ImagemService imagemService,
                          @Value("${cache.dir:./cache}") String cacheDir) {
        this(ollamaService, imagemService, Path.of(cacheDir, "pranchas"));
    }

    /** Sem cache de temas (usado nos testes). */
    PranchaService(OllamaService ollamaService, ImagemService imagemService) {
        this(ollamaService, imagemService, (Path) null);
    }

    private PranchaService(OllamaService ollamaService, ImagemService imagemService, Path pastaPranchas) {
        this.ollamaService = ollamaService;
        this.imagemService = imagemService;
        this.pastaPranchas = pastaPranchas;
    }

    /**
     * Só as palavras, sem imagens. A lista de cada tema é guardada em cache/pranchas/:
     * o modelo muda as palavras a cada geração, e assim a mesma prancha (e as imagens
     * já geradas para ela) se repete, mesmo sem o Ollama aberto.
     */
    public List<ItemPrancha> montarPalavras(String tema) {
        String texto = lerDoCache(tema);
        if (texto == null) {
            texto = ollamaService.gerarPrancha(tema);
            log.info("Resposta do modelo para '{}':\n{}", tema, texto);
            if (texto == null || texto.startsWith("ERRO")) {
                throw new OllamaIndisponivelException(
                        "Não foi possível comunicar com o Ollama. "
                                + "Verifique se ele está aberto e se o modelo foi baixado.");
            }
            List<ItemPrancha> itens = interpretar(texto);
            if (!itens.isEmpty()) {
                guardarNoCache(tema, texto);
            }
            return itens;
        }
        return interpretar(texto);
    }

    private Path arquivoDoTema(String tema) {
        String slug = Textos.slug(tema);
        return (pastaPranchas == null || slug.isEmpty()) ? null : pastaPranchas.resolve(slug + ".txt");
    }

    private String lerDoCache(String tema) {
        Path arquivo = arquivoDoTema(tema);
        try {
            if (arquivo != null && Files.isRegularFile(arquivo)) {
                String texto = Files.readString(arquivo, StandardCharsets.UTF_8);
                return texto.isBlank() ? null : texto;
            }
        } catch (IOException e) {
            log.warn("Não foi possível ler a prancha em cache '{}': {}", arquivo, e.getMessage());
        }
        return null;
    }

    private void guardarNoCache(String tema, String texto) {
        Path arquivo = arquivoDoTema(tema);
        if (arquivo == null) {
            return;
        }
        try {
            Files.createDirectories(arquivo.getParent());
            Files.writeString(arquivo, texto, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Não foi possível guardar a prancha '{}': {}", arquivo, e.getMessage());
        }
    }

    /** Palavras + imagens numa só chamada (o front-end atual usa /palavras e /imagem separados). */
    public List<ItemPrancha> gerar(String tema, ModoImagem modo) {
        List<ItemPrancha> itens = new ArrayList<>();
        for (ItemPrancha base : montarPalavras(tema)) {
            ImagemService.Resultado imagem = ImagemService.Resultado.NENHUMA;
            try {
                imagem = imagemService.resolver(base.palavra(), base.sinonimo(), modo);
            } catch (RuntimeException e) {
                // uma imagem com problema não derruba a prancha inteira
                log.warn("Falha ao obter imagem de '{}': {}", base.palavra(), e.getMessage());
            }
            itens.add(new ItemPrancha(base.palavra(), base.tipo(), base.tipoNome(),
                    base.sinonimo(), imagem.url(), imagem.fonte()));
        }
        return itens;
    }

    /** Converte as linhas "palavra|tipo|sinonimo" em itens (ainda sem imagem). */
    static List<ItemPrancha> interpretar(String texto) {
        List<ItemPrancha> itens = new ArrayList<>();
        for (String linha : texto.split("\n")) {
            String[] partes = linha.split("\\|");
            if (partes.length != 3) {
                if (!linha.isBlank()) {
                    log.warn("Linha do modelo ignorada (formato inesperado): '{}'", linha);
                }
                continue;
            }
            String palavra = partes[0].trim();
            String tipo = partes[1].trim().toLowerCase();
            String sinonimo = partes[2].trim();
            if (palavra.isEmpty()) {
                continue;
            }
            itens.add(new ItemPrancha(palavra, tipo, TIPOS.getOrDefault(tipo, "outro"),
                    sinonimo, null, "nenhuma"));
        }
        return itens;
    }
}
