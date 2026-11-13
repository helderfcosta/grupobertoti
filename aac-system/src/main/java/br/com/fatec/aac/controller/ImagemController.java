package br.com.fatec.aac.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Serve as imagens guardadas no cache (pictogramas baixados e imagens geradas). */
@RestController
@RequestMapping("/imagens")
public class ImagemController {

    private static final Set<String> PASTAS = Set.of("arasaac", "ia");
    private static final Pattern NOME = Pattern.compile("[a-z0-9_-]{1,60}\\.png");

    private final Path raiz;

    public ImagemController(@Value("${cache.dir:./cache}") String cacheDir) {
        this.raiz = Path.of(cacheDir).toAbsolutePath().normalize();
    }

    @GetMapping("/{pasta}/{arquivo}")
    public ResponseEntity<Resource> imagem(@PathVariable("pasta") String pasta,
                                           @PathVariable("arquivo") String arquivo) {
        if (!PASTAS.contains(pasta) || !NOME.matcher(arquivo).matches()) {
            return ResponseEntity.notFound().build();
        }
        Path caminho = raiz.resolve(pasta).resolve(arquivo).normalize();
        if (!caminho.startsWith(raiz) || !Files.isRegularFile(caminho)) {
            return ResponseEntity.notFound().build();
        }
        // Pictogramas do ARASAAC não mudam (o nome é o id): podem ficar em cache no navegador.
        // Imagens da IA podem ser regeneradas com o mesmo nome (ex.: bolo.png), então
        // o navegador não deve reaproveitar a versão antiga.
        CacheControl cache = "ia".equals(pasta)
                ? CacheControl.noStore()
                : CacheControl.maxAge(1, TimeUnit.DAYS);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(cache)
                .body(new FileSystemResource(caminho));
    }
}
