package org.aplicacao;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Servidor HTTP local que imita o ViaCEP, para os testes não dependerem da internet. */
public class ViaCepFalso implements AutoCloseable {

    public static final String JSON_PASSIRA = """
            {
              "cep": "55650-000",
              "logradouro": "",
              "complemento": "",
              "unidade": "",
              "bairro": "",
              "localidade": "Passira",
              "uf": "PE",
              "estado": "Pernambuco",
              "regiao": "Nordeste",
              "ibge": "2610509",
              "gia": "",
              "ddd": "81",
              "siafi": "2323"
            }
            """;

    private final HttpServer servidor;

    public ViaCepFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        responder("/ws/55650000/json/", 200, JSON_PASSIRA);
        responder("/ws/99999999/json/", 200, "{\n  \"erro\": \"true\"\n}");
        responder("/ws/50000000/json/", 500, "erro interno");
        responder("/ws/11111111/json/", 400, "<html>Bad Request</html>");
        responder("/ws/22222222/json/", 200, "<html>isto nao e json</html>");
        servidor.start();
    }

    private void responder(String caminho, int status, String corpo) {
        servidor.createContext(caminho, troca -> {
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
    }

    public String url() {
        return "http://" + servidor.getAddress().getHostString() + ":" + servidor.getAddress().getPort() + "/ws";
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
