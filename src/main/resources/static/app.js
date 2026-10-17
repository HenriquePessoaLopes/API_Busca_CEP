(() => {
  const el = {
    form: document.getElementById('form'),
    cep: document.getElementById('cep'),
    buscar: document.getElementById('buscar'),
    pista: document.getElementById('pista'),
    resultado: document.getElementById('resultado'),
    paradas: document.getElementById('paradas'),
    listaParadas: document.getElementById('lista-paradas')
  };

  const CHAVE_HISTORICO = 'busca-cep-paradas';
  const MAX_PARADAS = 5;

  /** Cria um elemento com texto. Dados vindos da API entram sempre por textContent, nunca como HTML. */
  function criar(tag, classe, texto) {
    const e = document.createElement(tag);
    if (classe) e.className = classe;
    if (texto !== undefined) e.textContent = texto;
    return e;
  }

  const soDigitos = (texto) => texto.replace(/\D/g, '').slice(0, 8);
  const comHifen = (digitos) => (digitos.length > 5 ? `${digitos.slice(0, 5)}-${digitos.slice(5)}` : digitos);

  // ---------- histórico (opcional: funciona sem localStorage) ----------
  function lerParadas() {
    try {
      const salvo = JSON.parse(localStorage.getItem(CHAVE_HISTORICO) ?? '[]');
      return Array.isArray(salvo) ? salvo.filter((p) => /^\d{8}$/.test(p?.cep) && typeof p?.cidade === 'string') : [];
    } catch {
      return [];
    }
  }

  function guardarParada(endereco) {
    const cep = soDigitos(endereco.cep ?? '');
    if (cep.length !== 8) return;
    const paradas = [{ cep, cidade: endereco.localidade ?? '' }, ...lerParadas().filter((p) => p.cep !== cep)].slice(0, MAX_PARADAS);
    try {
      localStorage.setItem(CHAVE_HISTORICO, JSON.stringify(paradas));
    } catch {
      /* sem armazenamento: tudo bem */
    }
    mostrarParadas();
  }

  function mostrarParadas() {
    const paradas = lerParadas();
    el.paradas.hidden = paradas.length === 0;
    el.listaParadas.replaceChildren(
      ...paradas.map((p) => {
        const item = criar('li');
        const botao = criar('button', 'parada', `${comHifen(p.cep)} · ${p.cidade}`);
        botao.type = 'button';
        botao.addEventListener('click', () => {
          el.cep.value = comHifen(p.cep);
          consultar(p.cep);
        });
        item.append(botao);
        return item;
      })
    );
  }

  // ---------- resultado ----------
  function linha(rotulo, valor) {
    const bloco = criar('div', 'trecho__item');
    bloco.append(criar('dt', '', rotulo), criar('dd', '', valor));
    return bloco;
  }

  function textoParaCopiar(e) {
    return [
      [e.logradouro, e.complemento].filter(Boolean).join(' - '),
      e.bairro,
      `${e.localidade}/${e.uf}`,
      `CEP ${e.cep}`
    ]
      .filter(Boolean)
      .join(', ');
  }

  function mostrarEndereco(e) {
    const placa = criar('div', 'placa');
    placa.append(criar('p', 'placa__pre', 'Você chegou a'), criar('h2', 'placa__cidade', e.localidade || 'Cidade não informada'));
    const regiao = criar('p', 'placa__regiao');
    regiao.append(criar('span', 'escudo', e.uf || '--'), criar('span', '', [e.estado, e.regiao].filter(Boolean).join(' · ')));
    placa.append(regiao);

    const trecho = criar('dl', 'trecho');
    trecho.append(linha('CEP', e.cep));
    if (e.logradouro) trecho.append(linha('Logradouro', e.logradouro));
    if (e.complemento) trecho.append(linha('Complemento', e.complemento));
    if (e.bairro) trecho.append(linha('Bairro', e.bairro));
    trecho.append(linha('Cidade', `${e.localidade} (${e.uf})`));
    if (e.ddd) trecho.append(linha('DDD', e.ddd));
    if (e.ibge) trecho.append(linha('Código IBGE', e.ibge));
    if (!e.logradouro) {
      trecho.append(criar('p', 'aviso-cidade', 'Este CEP é da cidade inteira: não há uma rua específica.'));
    }

    const copiar = criar('button', 'botao botao--claro', 'Copiar endereço');
    copiar.type = 'button';
    copiar.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(textoParaCopiar(e));
        copiar.textContent = 'Copiado!';
      } catch {
        copiar.textContent = 'Não consegui copiar';
      }
      setTimeout(() => (copiar.textContent = 'Copiar endereço'), 1800);
    });
    const acoes = criar('div', 'acoes');
    acoes.append(copiar);

    el.resultado.replaceChildren(placa, trecho, acoes);
  }

  const TITULOS = {
    CEP_INVALIDO: ['!', 'CEP inválido'],
    CEP_NAO_ENCONTRADO: ['?', 'Estrada sem saída'],
    SERVICO_INDISPONIVEL: ['×', 'Pista interditada']
  };

  function mostrarErro(tipo, mensagem) {
    const [simbolo, titulo] = TITULOS[tipo] ?? ['!', 'Algo deu errado'];
    const losango = criar('div', 'erro__losango');
    losango.append(criar('span', '', simbolo));
    const texto = criar('div');
    texto.append(criar('h3', '', titulo), criar('p', '', mensagem));
    const caixa = criar('div', 'erro');
    caixa.setAttribute('role', 'alert');
    caixa.append(losango, texto);
    el.resultado.replaceChildren(caixa);
  }

  // ---------- consulta ----------
  async function consultar(digitos) {
    el.cep.removeAttribute('aria-invalid');
    el.buscar.disabled = true;
    el.pista.classList.add('carregando');
    try {
      const resp = await fetch(`/api/cep/${digitos}`);
      const dados = await resp.json();
      if (resp.ok) {
        mostrarEndereco(dados);
        guardarParada(dados);
      } else {
        mostrarErro(dados.tipo, dados.erro ?? 'Não foi possível consultar o CEP.');
      }
    } catch {
      mostrarErro('SERVICO_INDISPONIVEL', 'Não consegui falar com o servidor. Ele está rodando?');
    } finally {
      el.buscar.disabled = false;
      el.pista.classList.remove('carregando');
    }
  }

  el.cep.addEventListener('input', () => {
    el.cep.value = comHifen(soDigitos(el.cep.value));
    el.cep.removeAttribute('aria-invalid');
  });

  el.form.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const digitos = soDigitos(el.cep.value);
    if (digitos.length !== 8) {
      el.cep.setAttribute('aria-invalid', 'true');
      mostrarErro('CEP_INVALIDO', 'Um CEP tem 8 números, por exemplo 55650-000.');
      el.cep.focus();
      return;
    }
    consultar(digitos);
  });

  mostrarParadas();
})();
