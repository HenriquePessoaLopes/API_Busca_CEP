package org.aplicacao.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aplicacao.ViaCepFalso;
import org.aplicacao.servico.BuscaCepServico;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ServidorTest {

    private static ViaCepFalso viaCep;
    private static Servidor servidor;
    private static final HttpClient http = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeAll
    static void subir() throws Exception {
        viaCep = new ViaCepFalso();
        servidor = new Servidor(new BuscaCepServico(HttpClient.newHttpClient(), viaCep.url()), 0);
        servidor.iniciar();
    }

    @AfterAll
    static void derrubar() {
        servidor.parar();
        viaCep.close();
    }

    private static HttpResponse<String> requisitar(String metodo, String caminho) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + servidor.porta() + caminho))
                .method(metodo, HttpRequest.BodyPublishers.noBody())
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> get(String caminho) throws Exception {
        return requisitar("GET", caminho);
    }

    @Test
    void cepExistenteDevolveOEnderecoEmJson() throws Exception {
        HttpResponse<String> resposta = get("/api/cep/55650000");
        assertEquals(200, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        JsonNode json = mapper.readTree(resposta.body());
        assertEquals("Passira", json.get("localidade").asText());
        assertEquals("PE", json.get("uf").asText());
        assertEquals("55650-000", json.get("cep").asText());
    }

    @Test
    void cepComHifenFunciona() throws Exception {
        assertEquals(200, get("/api/cep/55650-000").statusCode());
    }

    @Test
    void cepInvalidoResponde400() throws Exception {
        HttpResponse<String> resposta = get("/api/cep/12");
        assertEquals(400, resposta.statusCode());
        JsonNode json = mapper.readTree(resposta.body());
        assertEquals("CEP_INVALIDO", json.get("tipo").asText());
        assertTrue(json.get("erro").asText().contains("CEP inválido"));
    }

    @Test
    void cepInexistenteResponde404() throws Exception {
        HttpResponse<String> resposta = get("/api/cep/99999999");
        assertEquals(404, resposta.statusCode());
        assertEquals("CEP_NAO_ENCONTRADO", mapper.readTree(resposta.body()).get("tipo").asText());
    }

    @Test
    void falhaNoViaCepResponde502() throws Exception {
        HttpResponse<String> resposta = get("/api/cep/50000000");
        assertEquals(502, resposta.statusCode());
        assertEquals("SERVICO_INDISPONIVEL", mapper.readTree(resposta.body()).get("tipo").asText());
    }

    @Test
    void textoNoLugarDoCepNaoEhTratadoComoHtml() throws Exception {
        HttpResponse<String> resposta = get("/api/cep/%3Cscript%3E");
        assertEquals(400, resposta.statusCode());
        assertTrue(resposta.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    }

    @Test
    void rotaDeApiDesconhecidaResponde404EmJson() throws Exception {
        HttpResponse<String> resposta = get("/api/nada");
        assertEquals(404, resposta.statusCode());
        assertTrue(resposta.body().contains("erro"));
    }

    @Test
    void servePaginaPrincipalEArquivosEstaticos() throws Exception {
        HttpResponse<String> index = get("/");
        assertEquals(200, index.statusCode());
        assertTrue(index.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(index.body().contains("Busca CEP"));
        assertTrue(index.headers().firstValue("Content-Security-Policy").isPresent());

        assertEquals(200, get("/style.css").statusCode());
        assertEquals(200, get("/app.js").statusCode());
        assertEquals(200, get("/icone.svg").statusCode());
    }

    @Test
    void naoSaiDaPastaDeArquivosEstaticos() throws Exception {
        for (String caminho : new String[]{"/../pom.xml", "/%2e%2e/pom.xml", "/..%5Cpom.xml", "/static/index.html", "/a/b.html", "/pom.xml", "/Main.class"}) {
            assertEquals(404, get(caminho).statusCode(), caminho);
        }
    }

    @Test
    void soAceitaGet() throws Exception {
        assertEquals(405, requisitar("POST", "/api/cep/55650000").statusCode());
        assertEquals(405, requisitar("DELETE", "/").statusCode());
    }
}
