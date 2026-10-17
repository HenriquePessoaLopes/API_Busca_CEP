package org.aplicacao.servico;

/** Erro previsto ao consultar um CEP. A mensagem já é amigável para mostrar ao usuário. */
public abstract sealed class BuscaCepException extends RuntimeException
        permits BuscaCepException.CepInvalido, BuscaCepException.CepNaoEncontrado, BuscaCepException.ServicoIndisponivel {

    protected BuscaCepException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    /** O texto digitado não é um CEP (precisa ter 8 dígitos). */
    public static final class CepInvalido extends BuscaCepException {
        public CepInvalido(String mensagem) {
            super(mensagem, null);
        }
    }

    /** O CEP tem formato válido, mas não existe na base do ViaCEP. */
    public static final class CepNaoEncontrado extends BuscaCepException {
        public CepNaoEncontrado(String cep) {
            super("CEP " + cep + " não encontrado.", null);
        }
    }

    /** Não foi possível consultar o ViaCEP (sem internet, fora do ar, resposta estranha). */
    public static final class ServicoIndisponivel extends BuscaCepException {
        public ServicoIndisponivel(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
