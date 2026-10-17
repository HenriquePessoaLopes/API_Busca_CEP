# API Busca CEP

Aplicativo em Java que descobre o endereço de um CEP brasileiro consultando a API pública [ViaCEP](https://viacep.com.br). Você digita `55650-000` e ele responde com cidade, estado, região, DDD e, quando o CEP é de uma rua, logradouro e bairro. Funciona no terminal e também numa página web, cujo visual lembra uma rodovia: concreto, asfalto e sinalização branca.

## A história

Todo cadastro, entrega ou formulário brasileiro acaba pedindo o CEP e depois pedindo o resto do endereço, que a pessoa precisa digitar de novo. Este projeto nasceu como um exercício de **consumir uma API REST em Java puro**: fazer a requisição HTTP, ler o JSON e transformar em objeto, sem framework nenhum, só o `HttpClient` que já vem no JDK e o Jackson para o JSON.

A primeira versão consultava um CEP fixo no código e, se algo desse errado (CEP inexistente, sem internet), simplesmente imprimia um endereço vazio. Esta versão foi refeita para se comportar como um programa de verdade: pergunta o CEP, aceita os formatos que as pessoas realmente digitam e explica cada erro em português.

Depois ganhou uma interface web. O CEP é o "endereço" de um lugar, então a página é uma estrada: o título fica num pórtico sobre a pista com a faixa tracejada (que corre mais rápido enquanto a consulta acontece), o resultado aparece como a placa de entrada da cidade ("Você chegou a SÃO PAULO") e os erros viram placas de advertência, como "Estrada sem saída" para um CEP que não existe. O próprio Java serve a página, sem framework: só o servidor HTTP que já vem no JDK.

## O que ele faz

- Aceita o CEP como `55650000`, `55650-000`, `55.650-000` ou com espaços.
- Recusa o que não é CEP (menos ou mais de 8 números, letras) sem nem consultar a internet.
- Distingue os três tipos de problema e diz qual foi: **CEP inválido**, **CEP não encontrado** (formato certo, mas não existe) e **serviço indisponível** (sem internet, ViaCEP fora do ar, resposta estranha).
- Funciona de três jeitos: **interface web**, perguntando no terminal em um laço ("digite um CEP ou sair") ou consultando os CEPs passados na linha de comando.
- Na página, guarda as **últimas paradas** (os 5 CEPs mais recentes) para consultar de novo com um clique, e tem o botão **Copiar endereço**.
- Quando o CEP é da cidade inteira, sem rua específica, a página avisa em vez de deixar campos em branco.
- Mostra só os campos que vieram preenchidos (CEPs de cidades pequenas não têm logradouro nem bairro).

## Exemplo

```
=== Busca de CEP (ViaCEP) ===

Digite um CEP (ou "sair"): 01001-000

CEP:         01001-000
Logradouro:  Praça da Sé
Complemento: lado ímpar
Bairro:      Sé
Cidade:      São Paulo
Estado:      São Paulo (SP)
Região:      Sudeste
DDD:         11
Código IBGE: 3550308

Digite um CEP (ou "sair"): 99999999
CEP 99999-999 não encontrado.

Digite um CEP (ou "sair"): abc
CEP inválido: "abc". Use 8 números, por exemplo 55650-000.
```

## Como rodar

Pré-requisito: **JDK 21** e internet (para consultar o ViaCEP). Não precisa instalar o Maven: o projeto traz o Maven Wrapper.

No Windows (PowerShell), dentro da pasta do projeto.

### Interface web

```powershell
.\mvnw.cmd -q compile exec:java "-Dexec.args=--web"
```

Quando aparecer `Busca CEP rodando em http://localhost:8085`, abra esse endereço no navegador. Para parar, `Ctrl+C`. Para usar outra porta: `"-Dexec.args=--web 9000"`. O servidor só aceita conexões da própria máquina.

### Terminal

```powershell
.\mvnw.cmd -q compile exec:java
```

Isso abre o modo interativo. Para consultar CEPs direto, sem perguntas:

```powershell
.\mvnw.cmd -q compile exec:java "-Dexec.args=55650000 01001-000"
```

No Linux ou macOS use `./mvnw` no lugar de `.\mvnw.cmd`. Na primeira vez o Maven baixa as dependências, então demora um pouco.

Se as letras acentuadas aparecerem trocadas por símbolos no terminal do Windows, rode `chcp 65001` antes e tente de novo.

### Testes

```powershell
.\mvnw.cmd test
```

São 39 testes (JUnit 5). Eles **não usam a internet**: sobem um servidor HTTP local que imita o ViaCEP, com respostas de sucesso, CEP inexistente, erro 400, erro 500, resposta que não é JSON e servidor fora do ar. Cobrem também o servidor web (rotas, códigos de status e a garantia de que ele só entrega os arquivos da página, nunca outros arquivos do computador).

## Como o código está organizado

```
src/main/java/org/aplicacao/
  Main.java                          # terminal (interativo ou por argumentos) e --web
  servico/BuscaCepServico.java       # valida o CEP, chama o ViaCEP e interpreta a resposta
  servico/BuscaCepException.java     # os 3 tipos de erro (inválido, não encontrado, indisponível)
  dto/Endereco.java                  # record com os campos do endereço
  web/Servidor.java                  # servidor HTTP do JDK: página + rota GET /api/cep/{cep}
src/main/resources/static/           # a página: index.html, style.css, app.js
src/test/java/org/aplicacao/         # testes e o ViaCEP falso
```

Decisões:

- O serviço recebe o `HttpClient` e a URL base pelo construtor. Em produção usa o ViaCEP; nos testes aponta para o servidor local.
- Um CEP inexistente é um caso **normal** do ViaCEP (resposta 200 com `{"erro": ...}`), então vira `CepNaoEncontrado`, e uma resposta que nem é JSON vira `ServicoIndisponivel`, nunca "CEP não encontrado".
- Os erros são exceções próprias, com mensagens prontas para o usuário: o terminal nunca mostra stack trace.
- A rota `GET /api/cep/{cep}` responde `200` com o endereço, `400` (CEP inválido), `404` (CEP não encontrado) ou `502` (ViaCEP indisponível), sempre em JSON com o tipo do erro.
- A página monta tudo com `textContent` (nunca `innerHTML`), o servidor envia Content-Security-Policy e só entrega arquivos simples da pasta `static`.
