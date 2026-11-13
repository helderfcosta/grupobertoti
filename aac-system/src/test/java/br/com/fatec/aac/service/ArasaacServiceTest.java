package br.com.fatec.aac.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ArasaacServiceTest {

    private static final String API = "https://api.arasaac.org/v1/pictograms/pt/search/";
    private static final String IMG = "https://static.arasaac.org/pictograms/%d/%d_500.png";

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private static Map<String, Object> resultado(int id, boolean aac, String... keywords) {
        return Map.of(
                "_id", id,
                "aac", aac,
                "keywords", java.util.Arrays.stream(keywords)
                        .map(k -> Map.of("keyword", k)).toList());
    }

    @Test
    void escolheKeywordExataEPrefereAac() {
        List<Map<String, Object>> lista = List.of(
                resultado(27218, false, "bola de pingue-pongue"),
                resultado(2269, false, "bola"),
                resultado(3241, true, "bola"),
                resultado(6175, false, "perder a bola"));

        assertEquals(Optional.of(3241), ArasaacService.escolherMelhor("bola", lista));
    }

    @Test
    void ignoraAcentoECaixaEEspacos() {
        List<Map<String, Object>> lista = List.of(resultado(10, false, " Criança "));
        assertEquals(Optional.of(10), ArasaacService.escolherMelhor("crianca", lista));
    }

    @Test
    void semKeywordParecidaNaoEscolheNada() {
        List<Map<String, Object>> lista = List.of(resultado(1, true, "perder a bola"));
        assertTrue(ArasaacService.escolherMelhor("bola", lista).isEmpty());
        assertTrue(ArasaacService.escolherMelhor("bola", null).isEmpty());
    }

    @Test
    void buscaBaixaImagemEUsaCacheNaSegundaVez(@TempDir Path cache) throws Exception {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();

        server.expect(requestTo(API + "balan%C3%A7ar")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "[{\"_id\":555,\"aac\":true,\"keywords\":[{\"keyword\":\"balançar\"}]}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://static.arasaac.org/pictograms/555/555_500.png"))
                .andRespond(withSuccess(PNG, MediaType.IMAGE_PNG));

        ArasaacService servico = new ArasaacService(rest, API, IMG, cache.toString());

        assertEquals(Optional.of("/imagens/arasaac/555.png"), servico.buscarImagem("balançar"));
        assertTrue(Files.isRegularFile(cache.resolve("arasaac").resolve("555.png")));

        // segunda vez: nenhuma chamada de rede (o servidor só esperava 1 busca + 1 imagem)
        assertEquals(Optional.of("/imagens/arasaac/555.png"), servico.buscarImagem("balançar"));
        server.verify();

        // um novo serviço (programa reiniciado) lê o cache do disco, também sem rede
        ArasaacService reiniciado = new ArasaacService(new RestTemplate(), API, IMG, cache.toString());
        assertEquals(Optional.of("/imagens/arasaac/555.png"), reiniciado.buscarImagem("balançar"));
    }

    @Test
    void respostaQueNaoEPngEhDescartada(@TempDir Path cache) {
        RestTemplate rest = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();

        server.expect(requestTo(API + "bola"))
                .andRespond(withSuccess(
                        "[{\"_id\":1,\"aac\":true,\"keywords\":[{\"keyword\":\"bola\"}]}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://static.arasaac.org/pictograms/1/1_500.png"))
                .andRespond(withSuccess("<html>erro</html>", MediaType.TEXT_HTML));

        ArasaacService servico = new ArasaacService(rest, API, IMG, cache.toString());
        assertTrue(servico.buscarImagem("bola").isEmpty());
    }
}
