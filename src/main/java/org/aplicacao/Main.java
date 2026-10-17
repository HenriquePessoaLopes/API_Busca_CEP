package org.aplicacao;

import org.aplicacao.dto.Endereco;
import org.aplicacao.servico.BuscaCepException;
import org.aplicacao.servico.BuscaCepServico;
import org.aplicacao.web.Servidor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Scanner;

/**
 * Busca de CEP no terminal.
 * <ul>
 *   <li>Com argumentos: consulta cada CEP informado e termina.</li>
 *   <li>Sem argumentos: pergunta os CEPs, um por vez, até digitar "sair".</li>
 *   <li>Com {@code --web [porta]}: sobe a interface web.</li>
 * </ul>
 */
public class Main {

    private static final int PORTA_PADRAO = 8085;

    public static void main(String[] args) throws IOException {
        if (args.length > 0 && args[0].equals("--web")) {
            iniciarWeb(args);
            return; // o servidor mantém o programa vivo até Ctrl+C
        }
        int codigo = executar(args, System.in, System.out, new BuscaCepServico());
        System.exit(codigo);
    }

    /** Sobe a interface web. Uso: --web [porta] (padrão 8085). */
    private static void iniciarWeb(String[] args) throws IOException {
        int porta = PORTA_PADRAO;
        if (args.length > 1) {
            try {
                porta = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.out.println("Porta inválida: " + args[1]);
                System.exit(1);
            }
        }
        Servidor servidor = new Servidor(new BuscaCepServico(), porta);
        servidor.iniciar();
        System.out.println("Busca CEP rodando em http://localhost:" + servidor.porta() + "  (Ctrl+C para parar)");
    }

    /** Separado do main para poder ser testado. Devolve 0 se tudo deu certo e 1 se alguma consulta falhou. */
    static int executar(String[] args, InputStream entrada, PrintStream saida, BuscaCepServico servico) {
        if (args.length > 0) {
            int falhas = 0;
            for (String cep : args) {
                if (!consultar(cep, saida, servico)) {
                    falhas++;
                }
            }
            return falhas == 0 ? 0 : 1;
        }

        saida.println("=== Busca de CEP (ViaCEP) ===");
        Scanner scanner = new Scanner(entrada);
        while (true) {
            saida.print("\nDigite um CEP (ou \"sair\"): ");
            saida.flush();
            if (!scanner.hasNextLine()) {
                break;
            }
            String linha = scanner.nextLine().strip();
            if (linha.equalsIgnoreCase("sair")) {
                break;
            }
            if (!linha.isEmpty()) {
                consultar(linha, saida, servico);
            }
        }
        saida.println("\nAté logo!");
        return 0;
    }

    private static boolean consultar(String cep, PrintStream saida, BuscaCepServico servico) {
        try {
            Endereco endereco = servico.buscar(cep);
            saida.println();
            saida.println(endereco.formatado());
            return true;
        } catch (BuscaCepException e) {
            saida.println(e.getMessage());
            return false;
        }
    }
}
