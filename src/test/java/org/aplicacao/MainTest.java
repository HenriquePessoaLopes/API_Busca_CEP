package org.aplicacao;

import org.aplicacao.servico.BuscaCepServico;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    private static ViaCepFalso viaCep;
    private static BuscaCepServico servico;

    @BeforeAll
    static void subirServidorFalso() throws Exception {
        viaCep = new ViaCepFalso();
        servico = new BuscaCepServico(HttpClient.newHttpClient(), viaCep.url());
    }

    @AfterAll
    static void derrubarServidorFalso() {
        viaCep.close();
    }

    private record Resultado(int codigo, String saida) {
    }

    private static Resultado rodar(String entrada, String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream saida = new PrintStream(bytes, true, StandardCharsets.UTF_8);
        int codigo = Main.executar(args, new ByteArrayInputStream(entrada.getBytes(StandardCharsets.UTF_8)), saida, servico);
        return new Resultado(codigo, bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void comArgumentoConsultaOCepEDevolveZero() {
        Resultado r = rodar("", "55650000");
        assertEquals(0, r.codigo());
        assertTrue(r.saida().contains("Passira"));
        assertTrue(r.saida().contains("Pernambuco (PE)"));
    }

    @Test
    void comArgumentosVariosCepsConsultaTodos() {
        Resultado r = rodar("", "55650-000", "99999999");
        assertEquals(1, r.codigo(), "uma consulta falhou");
        assertTrue(r.saida().contains("Passira"));
        assertTrue(r.saida().contains("CEP 99999-999 não encontrado."));
    }

    @Test
    void modoInterativoConsultaEPergunta() {
        Resultado r = rodar("55650-000\nabc\n\n99999999\nSAIR\n");
        assertEquals(0, r.codigo());
        assertTrue(r.saida().contains("Passira"));
        assertTrue(r.saida().contains("CEP inválido: \"abc\""));
        assertTrue(r.saida().contains("CEP 99999-999 não encontrado."));
        assertTrue(r.saida().contains("Até logo!"));
    }

    @Test
    void modoInterativoTerminaQuandoAEntradaAcaba() {
        Resultado r = rodar("55650000\n");
        assertEquals(0, r.codigo());
        assertTrue(r.saida().contains("Até logo!"));
    }

    @Test
    void errosNuncaMostramStackTrace() {
        Resultado r = rodar("", "12");
        assertEquals(1, r.codigo());
        assertTrue(r.saida().contains("CEP inválido"));
        assertTrue(!r.saida().contains("Exception"));
    }
}
