package br.com.fatec.aac.controller;

import br.com.fatec.aac.dto.BoardRequest;
import br.com.fatec.aac.service.OllamaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/aac")
public class AacController {

    private final OllamaService ollamaService;

    public AacController(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    @PostMapping("/gerar")
    public String gerar(@RequestBody BoardRequest request) {

        return ollamaService.gerarPrancha(
                request.getTema()
        );

    }

}