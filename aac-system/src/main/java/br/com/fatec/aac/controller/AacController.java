package br.com.fatec.aac.controller;

import br.com.fatec.aac.dto.BoardRequest;
import br.com.fatec.aac.dto.ImagemRequest;
import br.com.fatec.aac.dto.ItemPrancha;
import br.com.fatec.aac.dto.ModoImagem;
import br.com.fatec.aac.service.ImagemService;
import br.com.fatec.aac.service.OllamaIndisponivelException;
import br.com.fatec.aac.service.PranchaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/aac")
public class AacController {

    private static final int TAMANHO_MAX_PALAVRA = 60;

    private final PranchaService pranchaService;
    private final ImagemService imagemService;

    public AacController(PranchaService pranchaService, ImagemService imagemService) {
        this.pranchaService = pranchaService;
        this.imagemService = imagemService;
    }

    /** Palavras + imagens numa só chamada (mantido para compatibilidade). */
    @PostMapping("/gerar")
    public List<ItemPrancha> gerar(@RequestBody BoardRequest request) {
        return pranchaService.gerar(temaValido(request), ModoImagem.de(request.getModo()));
    }

    /** Só as palavras da prancha (rápido). As imagens são pedidas depois, uma a uma. */
    @PostMapping("/palavras")
    public List<ItemPrancha> palavras(@RequestBody BoardRequest request) {
        return pranchaService.montarPalavras(temaValido(request));
    }

    /** Imagem de uma palavra, conforme o modo (online/offline). */
    @PostMapping("/imagem")
    public ImagemService.Resultado imagem(@RequestBody ImagemRequest request) {
        String palavra = request.palavra();
        if (palavra == null || palavra.isBlank() || palavra.length() > TAMANHO_MAX_PALAVRA) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Palavra inválida.");
        }
        return imagemService.resolver(palavra.trim(), request.sinonimo(), ModoImagem.de(request.modo()));
    }

    private static String temaValido(BoardRequest request) {
        String tema = request.getTema();
        if (tema == null || tema.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um tema.");
        }
        return tema.trim();
    }

    @ExceptionHandler(OllamaIndisponivelException.class)
    public ResponseEntity<Map<String, String>> ollamaIndisponivel(OllamaIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", e.getMessage()));
    }
}
