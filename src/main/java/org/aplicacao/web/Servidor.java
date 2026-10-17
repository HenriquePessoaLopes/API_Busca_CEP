package org.aplicacao.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.aplicacao.servico.BuscaCepException;
import org.aplicacao.servico.BuscaCepException.CepInvalido;
import org.aplicacao.servico.BuscaCepException.CepNaoEncontrado;
import org.aplicacao.servico.BuscaCepException.ServicoIndisponivel;
import org.aplicacao.servico.BuscaCepServico;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Servidor web local (só aceita conexões da própria máquina): entrega a página e a rota
 * {@code GET /api/cep/{cep}}. Usa apenas o servidor HTTP que já vem no JDK.
 */
public class Servidor {

    private static final String PREFIXO_API = "/api/cep/";
    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "ico", "image/x-icon");

    private final BuscaCepServico servico;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpServer servidor;

    public Servidor(BuscaCepServico servico, int porta) throws IOException {
        this.servico = servico;
        this.servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), porta), 0);
        this.servidor.setExecutor(Executors.newFixedThreadPool(4));
        this.servidor.createContext("/", this::tratar);
    }

    public void iniciar() {
        servidor.start();
    }

    public void parar() {
        servidor.stop(0);
        ((java.util.concurrent.ExecutorService) servidor.getExecutor()).shutdownNow();
    }

    /** Porta realmente usada (útil quando se pede a porta 0). */
    public int porta() {
        return servidor.getAddress().getPort();
    }

    private void tratar(HttpExchange troca) throws IOException {
        try (troca) {
            if (!"GET".equals(troca.getRequestMethod())) {
                json(troca, 405, Map.of("erro", "Método não permitido."));
                return;
            }
            String caminho = troca.getRequestURI().getPath();
            if (caminho.startsWith(PREFIXO_API)) {
                consultarCep(troca, caminho.substring(PREFIXO_API.length()));
            } else if (caminho.startsWith("/api/")) {
                json(troca, 404, Map.of("erro", "Rota não encontrada."));
            } else {
                arquivoEstatico(troca, caminho);
            }
        } catch (RuntimeException e) {
            // Qualquer falha inesperada vira 500 sem vazar detalhes.
            json(troca, 500, Map.of("erro", "Erro interno."));
        }
    }

    private void consultarCep(HttpExchange troca, String cep) throws IOException {
        try {
            json(troca, 200, servico.buscar(cep.endsWith("/") ? cep.substring(0, cep.length() - 1) : cep));
        } catch (CepInvalido e) {
            json(troca, 400, erro("CEP_INVALIDO", e));
        } catch (CepNaoEncontrado e) {
            json(troca, 404, erro("CEP_NAO_ENCONTRADO", e));
        } catch (ServicoIndisponivel e) {
            json(troca, 502, erro("SERVICO_INDISPONIVEL", e));
        }
    }

    private static Map<String, String> erro(String tipo, BuscaCepException e) {
        Map<String, String> corpo = new LinkedHashMap<>();
        corpo.put("tipo", tipo);
        corpo.put("erro", e.getMessage());
        return corpo;
    }

    private void arquivoEstatico(HttpExchange troca, String caminho) throws IOException {
        String nome = caminho.equals("/") ? "index.html" : caminho.substring(1);
        // Só arquivos simples dentro de /static: nada de subpastas, "..", barras invertidas ou nomes estranhos.
        if (!nome.matches("[A-Za-z0-9_-]+\\.[a-z]+") || !TIPOS.containsKey(nome.substring(nome.lastIndexOf('.') + 1))) {
            json(troca, 404, Map.of("erro", "Não encontrado."));
            return;
        }
        try (InputStream in = Servidor.class.getResourceAsStream("/static/" + nome)) {
            if (in == null) {
                json(troca, 404, Map.of("erro", "Não encontrado."));
                return;
            }
            byte[] bytes = in.readAllBytes();
            troca.getResponseHeaders().set("Content-Type", TIPOS.get(nome.substring(nome.lastIndexOf('.') + 1)));
            cabecalhosDeSeguranca(troca);
            troca.sendResponseHeaders(200, bytes.length);
            troca.getResponseBody().write(bytes);
        }
    }

    private void json(HttpExchange troca, int status, Object corpo) throws IOException {
        byte[] bytes = mapper.writeValueAsString(corpo).getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        cabecalhosDeSeguranca(troca);
        troca.sendResponseHeaders(status, bytes.length);
        troca.getResponseBody().write(bytes);
    }

    private static void cabecalhosDeSeguranca(HttpExchange troca) {
        troca.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        troca.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; style-src 'self'; script-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'");
    }
}
