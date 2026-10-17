package org.aplicacao.servico;

import org.aplicacao.ViaCepFalso;
import org.aplicacao.dto.Endereco;
import org.aplicacao.servico.BuscaCepException.CepInvalido;
import org.aplicacao.servico.BuscaCepException.CepNaoEncontrado;
import org.aplicacao.servico.BuscaCepException.ServicoIndisponivel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;

class BuscaCepServicoTest {

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

    @ParameterizedTest
    @ValueSource(strings = {"55650000", "55650-000", " 55650-000 ", "55.650-000", "55650 000"})
    void normalizaCepsEmFormatosComuns(String texto) {
        assertEquals("55650000", BuscaCepServico.normalizar(texto));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123", "123456789", "abcdefgh", "5565000a", "55650-00", "55650--000x"})
    void recusaCepsQueNaoTemOitoDigitos(String texto) {
        assertThrows(CepInvalido.class, () -> BuscaCepServico.normalizar(texto));
    }

    @Test
    void recusaCepNulo() {
        assertThrows(CepInvalido.class, () -> BuscaCepServico.normalizar(null));
    }

    @Test
    void formataCepComHifen() {
        assertEquals("55650-000", BuscaCepServico.formatarCep("55650000"));
    }

    @Test
    void buscaEnderecoExistente() {
        Endereco e = servico.buscar("55650000");
        assertEquals("55650-000", e.cep());
        assertEquals("Passira", e.localidade());
        assertEquals("PE", e.uf());
        assertEquals("Pernambuco", e.estado());
        assertEquals("Nordeste", e.regiao());
        assertEquals("81", e.ddd());
        assertEquals("2610509", e.ibge());
    }

    @Test
    void aceitaCepComHifen() {
        assertEquals("Passira", servico.buscar("55650-000").localidade());
    }

    @Test
    void cepInexistenteLancaCepNaoEncontrado() {
        CepNaoEncontrado erro = assertThrows(CepNaoEncontrado.class, () -> servico.buscar("99999999"));
        assertEquals("CEP 99999-999 não encontrado.", erro.getMessage());
    }

    @Test
    void cepInvalidoNemChegaAoServidor() {
        assertThrows(CepInvalido.class, () -> servico.buscar("12"));
    }

    @Test
    void status400DoServidorViraCepInvalido() {
        assertThrows(CepInvalido.class, () -> servico.buscar("11111111"));
    }

    @Test
    void status500ViraServicoIndisponivel() {
        ServicoIndisponivel erro = assertThrows(ServicoIndisponivel.class, () -> servico.buscar("50000000"));
        assertTrue(erro.getMessage().contains("500"));
    }

    @Test
    void respostaQueNaoEJsonViraServicoIndisponivel() {
        // Uma resposta HTML nunca deve ser tratada como "CEP não encontrado".
        assertThrows(ServicoIndisponivel.class, () -> servico.buscar("22222222"));
    }

    @Test
    void semConexaoViraServicoIndisponivel() {
        // Porta 1 não tem ninguém escutando.
        BuscaCepServico semServidor = new BuscaCepServico(HttpClient.newHttpClient(), "http://127.0.0.1:1/ws");
        assertThrows(ServicoIndisponivel.class, () -> semServidor.buscar("55650000"));
    }

    @Test
    void enderecoFormatadoOmiteCamposVazios() {
        String texto = servico.buscar("55650000").formatado();
        assertTrue(texto.contains("CEP:"));
        assertTrue(texto.contains("55650-000"));
        assertTrue(texto.contains("Pernambuco (PE)"));
        assertFalse(texto.contains("Logradouro"), "logradouro vazio não deve aparecer");
        assertFalse(texto.contains("Complemento"));
    }
}
