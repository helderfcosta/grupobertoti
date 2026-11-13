package br.com.fatec.aac.service;

import br.com.fatec.aac.dto.ItemPrancha;
import br.com.fatec.aac.dto.ModoImagem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PranchaFluxoTest {

    @Test
    void interpretaLinhasDoModeloEIgnoraInvalidas() {
        String texto = "parquinho|l|playground\n"
                + "linha sem barras\n"
                + "balançar|v|balanço\n"
                + "x|y\n"
                + "amigo|S|colega\n";

        List<ItemPrancha> itens = PranchaService.interpretar(texto);

        assertEquals(3, itens.size());
        assertEquals("local", itens.get(0).tipoNome());
        assertEquals("verbo", itens.get(1).tipoNome());
        assertEquals("s", itens.get(2).tipo());
        assertNull(itens.get(0).imagem());
    }

    @Test
    void montarPalavrasDevolveItensSemImagem() {
        OllamaService ollama = mock(OllamaService.class);
        when(ollama.gerarPrancha("parque")).thenReturn("bola|s|bolinha\ncorrer|v|disparar\n");
        PranchaService servico = new PranchaService(ollama, mock(ImagemService.class));

        List<ItemPrancha> itens = servico.montarPalavras("parque");

        assertEquals(2, itens.size());
        assertNull(itens.get(0).imagem());
        assertEquals("nenhuma", itens.get(0).fonte());
    }

    @Test
    void montarPalavrasLancaExcecaoQuandoOllamaFalha() {
        OllamaService ollama = mock(OllamaService.class);
        when(ollama.gerarPrancha("parque")).thenReturn("ERRO: Não foi possível comunicar com o Ollama.");
        PranchaService servico = new PranchaService(ollama, mock(ImagemService.class));

        assertThrows(OllamaIndisponivelException.class, () -> servico.montarPalavras("parque"));
    }

    @Test
    void modoDesconhecidoOuNuloViraOnline() {
        assertEquals(ModoImagem.ONLINE, ModoImagem.de(null));
        assertEquals(ModoImagem.ONLINE, ModoImagem.de(""));
        assertEquals(ModoImagem.ONLINE, ModoImagem.de("qualquer"));
        assertEquals(ModoImagem.ONLINE, ModoImagem.de("online"));
        assertEquals(ModoImagem.OFFLINE, ModoImagem.de("offline"));
        assertEquals(ModoImagem.OFFLINE, ModoImagem.de(" OFFLINE "));
    }

    @Test
    void onlineUsaArasaacDepoisSinonimoDepoisIaLocalDepoisNenhuma() {
        ArasaacService arasaac = mock(ArasaacService.class);
        StableDiffusionService sd = mock(StableDiffusionService.class);
        ImagemService servico = new ImagemService(arasaac, sd);

        // 1) palavra encontrada no ARASAAC (a IA local nem é consultada)
        when(arasaac.buscarImagem("bola")).thenReturn(Optional.of("/imagens/arasaac/1.png"));
        assertEquals(new ImagemService.Resultado("/imagens/arasaac/1.png", "arasaac"),
                servico.resolver("bola", "bolinha", ModoImagem.ONLINE));
        verifyNoInteractions(sd);

        // 2) palavra não achada, sinônimo achado
        when(arasaac.buscarImagem("escorregador")).thenReturn(Optional.empty());
        when(arasaac.buscarImagem("escorrega")).thenReturn(Optional.of("/imagens/arasaac/2.png"));
        assertEquals("arasaac", servico.resolver("escorregador", "escorrega", ModoImagem.ONLINE).fonte());

        // 3) nada no ARASAAC -> IA local de apoio
        when(arasaac.buscarImagem(anyString())).thenReturn(Optional.empty());
        when(sd.gerar("xyz")).thenReturn(Optional.of("/imagens/ia/xyz.png"));
        assertEquals(new ImagemService.Resultado("/imagens/ia/xyz.png", "ia-local"),
                servico.resolver("xyz", "abc", ModoImagem.ONLINE));

        // 4) nada em nenhuma fonte -> nenhuma
        when(sd.gerar("qwe")).thenReturn(Optional.empty());
        assertEquals(ImagemService.Resultado.NENHUMA,
                servico.resolver("qwe", "asd", ModoImagem.ONLINE));
    }

    @Test
    void offlineUsaSomenteIaLocalENuncaChamaArasaac() {
        ArasaacService arasaac = mock(ArasaacService.class);
        StableDiffusionService sd = mock(StableDiffusionService.class);
        ImagemService servico = new ImagemService(arasaac, sd);

        when(sd.gerar("bola")).thenReturn(Optional.of("/imagens/ia/bola.png"));
        assertEquals(new ImagemService.Resultado("/imagens/ia/bola.png", "ia-local"),
                servico.resolver("bola", "bolinha", ModoImagem.OFFLINE));

        when(sd.gerar("correr")).thenReturn(Optional.empty());
        assertEquals(ImagemService.Resultado.NENHUMA,
                servico.resolver("correr", "disparar", ModoImagem.OFFLINE));

        verifyNoInteractions(arasaac);
    }

    @Test
    void sinonimoIgualAPalavraNaoRepeteABuscaNoArasaac() {
        ArasaacService arasaac = mock(ArasaacService.class);
        StableDiffusionService sd = mock(StableDiffusionService.class);
        when(arasaac.buscarImagem(anyString())).thenReturn(Optional.empty());

        new ImagemService(arasaac, sd).resolver("Bola", "bola", ModoImagem.ONLINE);

        verify(arasaac, times(1)).buscarImagem(anyString());
    }
}
