package org.aplicacao.servico;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aplicacao.dto.Endereco;
import org.aplicacao.servico.BuscaCepException.CepInvalido;
import org.aplicacao.servico.BuscaCepException.CepNaoEncontrado;
import org.aplicacao.servico.BuscaCepException.ServicoIndisponivel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Consulta endereços na API pública ViaCEP (https://viacep.com.br). */
public class BuscaCepServico {

    public static final String URL_PADRAO = "https://viacep.com.br/ws";

    private final HttpClient cliente;
    private final String urlBase;
    private final ObjectMapper mapper = new ObjectMapper();

    public BuscaCepServico() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), URL_PADRAO);
    }

    /** Construtor para testes ou para apontar para outro servidor. */
    public BuscaCepServico(HttpClient cliente, String urlBase) {
        this.cliente = cliente;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    /**
     * Aceita "55650000", "55650-000" ou com espaços/pontos e devolve só os 8 dígitos.
     *
     * @throws CepInvalido se o texto não tiver exatamente 8 dígitos
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            throw new CepInvalido("Informe um CEP.");
        }
        String cep = texto.replaceAll("[\\s.\\-]", "");
        if (!cep.matches("\\d{8}")) {
            throw new CepInvalido("CEP inválido: \"" + texto.strip() + "\". Use 8 números, por exemplo 55650-000.");
        }
        return cep;
    }

    /** Formata 55650000 como 55650-000. */
    public static String formatarCep(String cep) {
        return cep.substring(0, 5) + "-" + cep.substring(5);
    }

    /**
     * Busca o endereço de um CEP.
     *
     * @throws CepInvalido         se o CEP não tiver 8 dígitos
     * @throws CepNaoEncontrado    se o CEP não existir
     * @throws ServicoIndisponivel se não for possível falar com o ViaCEP
     */
    public Endereco buscar(String textoCep) {
        String cep = normalizar(textoCep);

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(urlBase + "/" + cep + "/json/"))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> resposta;
        try {
            resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicoIndisponivel("A consulta foi interrompida.", e);
        } catch (IOException e) {
            throw new ServicoIndisponivel("Não foi possível falar com o ViaCEP. Confira sua conexão com a internet.", e);
        }

        if (resposta.statusCode() == 400) {
            throw new CepInvalido("O ViaCEP não aceitou o CEP " + formatarCep(cep) + ".");
        }
        if (resposta.statusCode() != 200) {
            throw new ServicoIndisponivel("O ViaCEP respondeu com erro (status " + resposta.statusCode() + "). Tente de novo mais tarde.", null);
        }

        try {
            JsonNode json = mapper.readTree(resposta.body());
            // Para CEP inexistente o ViaCEP responde 200 com {"erro": true}.
            if (json == null || !json.isObject() || json.has("erro")) {
                throw new CepNaoEncontrado(formatarCep(cep));
            }
            return mapper.treeToValue(json, Endereco.class);
        } catch (JsonProcessingException e) {
            throw new ServicoIndisponivel("A resposta do ViaCEP veio em um formato inesperado.", e);
        }
    }
}
