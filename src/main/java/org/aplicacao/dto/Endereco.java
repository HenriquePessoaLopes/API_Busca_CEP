package org.aplicacao.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Endereço devolvido pelo ViaCEP. Campos que a API manda e não usamos (como "unidade") são ignorados.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Endereco(
        String cep,
        String logradouro,
        String complemento,
        String bairro,
        String localidade,
        String uf,
        String estado,
        String regiao,
        String ibge,
        String gia,
        String ddd,
        String siafi) {

    /** Texto pronto para mostrar no terminal; campos vazios ficam de fora. */
    public String formatado() {
        StringBuilder sb = new StringBuilder();
        adicionar(sb, "CEP", cep);
        adicionar(sb, "Logradouro", logradouro);
        adicionar(sb, "Complemento", complemento);
        adicionar(sb, "Bairro", bairro);
        adicionar(sb, "Cidade", localidade);
        adicionar(sb, "Estado", estado == null || estado.isBlank() ? uf : estado + " (" + uf + ")");
        adicionar(sb, "Região", regiao);
        adicionar(sb, "DDD", ddd);
        adicionar(sb, "Código IBGE", ibge);
        return sb.toString().stripTrailing();
    }

    private static void adicionar(StringBuilder sb, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            sb.append(String.format("%-12s %s%n", rotulo + ":", valor));
        }
    }
}
