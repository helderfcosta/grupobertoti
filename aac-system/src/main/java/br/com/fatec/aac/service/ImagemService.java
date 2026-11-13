package br.com.fatec.aac.service;

import br.com.fatec.aac.dto.ModoImagem;
import br.com.fatec.aac.util.Textos;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Escolhe a fonte da imagem conforme o modo do menu.
 *
 * ONLINE  : ARASAAC pela palavra -> ARASAAC pelo sinônimo -> IA local -> nenhuma
 * OFFLINE : somente IA local (Stable Diffusion) -> nenhuma
 *
 * A IA local usa o cache em disco (cache/ia): imagens já geradas ou copiadas para lá
 * são servidas mesmo sem o motor instalado. Gerar uma imagem nova exige sd.enabled=true.
 */
@Service
public class ImagemService {

    public record Resultado(String url, String fonte) {
        public static final Resultado NENHUMA = new Resultado(null, "nenhuma");
    }

    private final ArasaacService arasaac;
    private final StableDiffusionService stableDiffusion;

    public ImagemService(ArasaacService arasaac, StableDiffusionService stableDiffusion) {
        this.arasaac = arasaac;
        this.stableDiffusion = stableDiffusion;
    }

    public Resultado resolver(String palavra, String sinonimo, ModoImagem modo) {
        if (modo == ModoImagem.OFFLINE) {
            return viaIaLocal(palavra);
        }

        // ONLINE: ARASAAC é a fonte principal
        Optional<String> imagem = arasaac.buscarImagem(palavra);
        if (imagem.isPresent()) {
            return new Resultado(imagem.get(), "arasaac");
        }

        boolean sinonimoUtil = sinonimo != null && !sinonimo.isBlank()
                && !Textos.normalizar(sinonimo).equals(Textos.normalizar(palavra));
        if (sinonimoUtil) {
            imagem = arasaac.buscarImagem(sinonimo);
            if (imagem.isPresent()) {
                return new Resultado(imagem.get(), "arasaac");
            }
        }

        // apoio: IA local, só quando o ARASAAC não tem a imagem
        return viaIaLocal(palavra);
    }

    private Resultado viaIaLocal(String palavra) {
        return stableDiffusion.gerar(palavra)
                .map(url -> new Resultado(url, "ia-local"))
                .orElse(Resultado.NENHUMA);
    }
}
