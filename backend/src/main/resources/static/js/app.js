"use strict";

/*
 * Dashboard do Sistema de Irrigação Inteligente – Grupo 4
 *
 * Fala com a API REST do Spring Boot (mesmo endereço da página):
 *   GET  /api/status            estado geral
 *   GET  /api/readings/latest   valores actuais
 *   GET  /api/readings          histórico das medições
 *   GET  /api/irrigations       registo das irrigações
 *   GET  /api/config            limites e modo
 *   PUT  /api/config            alterar limites e modo
 *   POST /api/pump              ligar/desligar a bomba (modo manual)
 *
 * Todo o texto vindo da API é inserido com textContent (nunca innerHTML),
 * porque o motivo das irrigações chega por um broker MQTT público.
 */

const API = "/api";
const INTERVALO_MS = 5000;

const estado = {
  configuracao: null,        // última configuração recebida do servidor
  formularioAlterado: false, // o utilizador está a editar os limites
  aActualizar: false         // evita pedidos sobrepostos
};

let temporizadorNotificacao = null;

function el(id) {
  return document.getElementById(id);
}

// ---------------------------------------------------------------
// Pedidos à API
// ---------------------------------------------------------------

async function pedir(url, opcoes) {
  const resposta = await fetch(url, opcoes);

  if (resposta.status === 204) {
    return null;
  }

  const texto = await resposta.text();
  let corpo = null;
  if (texto) {
    try {
      corpo = JSON.parse(texto);
    } catch (e) {
      corpo = null;
    }
  }

  if (!resposta.ok) {
    const mensagem = corpo && corpo.erro ? corpo.erro : "Erro " + resposta.status;
    throw new Error(mensagem);
  }
  return corpo;
}

function enviarJson(metodo, url, objecto) {
  return pedir(url, {
    method: metodo,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(objecto)
  });
}

// ---------------------------------------------------------------
// Formatação
// ---------------------------------------------------------------

function numero(valor, casas) {
  return typeof valor === "number" ? valor.toFixed(casas === undefined ? 1 : casas) : "—";
}

function limitar(valor) {
  const n = Number(valor);
  if (isNaN(n)) return 0;
  return Math.min(100, Math.max(0, n));
}

// A API envia LocalDateTime como texto ISO (ou, noutras configurações, como lista)
function converterData(valor) {
  if (valor === null || valor === undefined) return null;

  if (Array.isArray(valor)) {
    return new Date(valor[0], valor[1] - 1, valor[2], valor[3] || 0, valor[4] || 0, valor[5] || 0);
  }

  const texto = typeof valor === "string" ? valor.replace(/(\.\d{3})\d+/, "$1") : valor;
  const data = new Date(texto);
  return isNaN(data.getTime()) ? null : data;
}

function formatarDataHora(valor) {
  const data = converterData(valor);
  if (!data) return "—";
  return data.toLocaleString("pt-PT", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit", second: "2-digit"
  });
}

function formatarDuracao(inicio, fim) {
  const a = converterData(inicio);
  const b = converterData(fim);
  if (!a || !b) return "—";
  let segundos = Math.max(0, Math.round((b.getTime() - a.getTime()) / 1000));
  const minutos = Math.floor(segundos / 60);
  segundos = segundos % 60;
  return minutos > 0 ? minutos + " min " + segundos + " s" : segundos + " s";
}

function rotuloModo(modo) {
  if (modo === "AUTO") return "Automático";
  if (modo === "MANUAL") return "Manual";
  return "—";
}

function rotuloEsp32(valor) {
  if (valor === "online") return "Online";
  if (valor === "offline") return "Offline";
  return "Desconhecido";
}

function classeEsp32(valor) {
  return valor === "online" || valor === "offline" ? valor : "desconhecido";
}

// ---------------------------------------------------------------
// Notificações e avisos
// ---------------------------------------------------------------

function notificar(texto, tipo) {
  const caixa = el("notificacao");
  caixa.textContent = texto;
  caixa.className = "notificacao " + (tipo === "erro" ? "erro" : "ok");
  clearTimeout(temporizadorNotificacao);
  temporizadorNotificacao = setTimeout(function () {
    caixa.className = "notificacao escondido";
  }, 5000);
}

function mostrarLigacao(ligado, mensagem) {
  const selo = el("estadoServidor");
  const aviso = el("aviso");
  if (ligado) {
    selo.textContent = "Ligado";
    selo.className = "selo online";
    aviso.className = "aviso escondido";
  } else {
    selo.textContent = "Sem ligação";
    selo.className = "selo offline";
    aviso.textContent = "Não foi possível comunicar com o servidor. " + (mensagem || "");
    aviso.className = "aviso";
  }
}

// ---------------------------------------------------------------
// Desenho do painel
// ---------------------------------------------------------------

function desenharValores(ultima, config) {
  const temLeitura = ultima !== null && ultima !== undefined;

  el("valorSolo").textContent = temLeitura ? numero(ultima.soilMoisture) + " %" : "—";
  el("valorTemperatura").textContent = temLeitura ? numero(ultima.temperature) + " °C" : "—";
  el("valorHumidadeAr").textContent = temLeitura ? numero(ultima.airHumidity) + " %" : "—";

  const bomba = el("valorBomba");
  if (temLeitura && typeof ultima.pumpOn === "boolean") {
    bomba.textContent = ultima.pumpOn ? "LIGADA" : "DESLIGADA";
    bomba.className = "valor " + (ultima.pumpOn ? "ligada" : "desligada");
  } else {
    bomba.textContent = "—";
    bomba.className = "valor";
  }

  el("ultimaLeitura").textContent = temLeitura
    ? "Última leitura: " + formatarDataHora(ultima.createdAt)
    : "Ainda não há leituras";

  // Barra da humidade do solo, com as marcas dos dois limites
  const solo = temLeitura ? limitar(ultima.soilMoisture) : 0;
  const barra = el("barraSolo");
  barra.style.width = solo + "%";

  if (config) {
    el("marcaLigar").style.left = limitar(config.moistureLimit) + "%";
    el("marcaParar").style.left = limitar(config.moistureStopLimit) + "%";
    el("textoLimitesSolo").textContent =
      "Liga abaixo de " + numero(config.moistureLimit) + " % · desliga a partir de " +
      numero(config.moistureStopLimit) + " %";

    let classe = "barra-preenchimento medio";
    if (solo < config.moistureLimit) classe = "barra-preenchimento baixo";
    else if (solo >= config.moistureStopLimit) classe = "barra-preenchimento alto";
    barra.className = classe;
  }
}

function desenharEstado(status, config) {
  const esp32 = status ? status.esp32 : "desconhecido";

  const seloEsp32 = el("estadoEsp32");
  seloEsp32.textContent = rotuloEsp32(esp32);
  seloEsp32.className = "selo " + classeEsp32(esp32);

  const seloTopo = el("estadoEsp32Topo");
  seloTopo.textContent = rotuloEsp32(esp32);
  seloTopo.className = "selo " + classeEsp32(esp32);

  el("estadoModo").textContent = rotuloModo(status ? status.operatingMode : null);

  let textoBomba = "—";
  if (status && typeof status.pumpOn === "boolean") {
    textoBomba = status.pumpOn ? "Ligada" : "Desligada";
  }
  el("estadoBomba").textContent = textoBomba;
  el("estadoIrrigacao").textContent = status ? (status.irrigationActive ? "Sim" : "Não") : "—";

  if (config) {
    el("regraAutomatica").textContent =
      "Regra automática: liga a bomba quando a humidade do solo for inferior a " +
      numero(config.moistureLimit) + " % e a temperatura superior a " +
      numero(config.temperatureLimit) + " °C; desliga quando a humidade do solo atingir " +
      numero(config.moistureStopLimit) + " %.";
  }
}

function desenharControlo(config) {
  const modo = config ? config.operatingMode : null;

  el("btnAuto").className = "botao" + (modo === "AUTO" ? " activo" : "");
  el("btnManual").className = "botao" + (modo === "MANUAL" ? " activo" : "");

  const manual = modo === "MANUAL";
  el("btnLigar").disabled = !manual;
  el("btnDesligar").disabled = !manual;
  el("dicaBomba").textContent = manual
    ? "Modo manual activo: a bomba obedece aos botões."
    : "Passe para o modo manual para comandar a bomba.";
}

function desenharFormulario(config) {
  if (!config) return;

  // Não apaga o que o utilizador está a escrever
  if (!estado.formularioAlterado) {
    el("limiteLigar").value = config.moistureLimit;
    el("limitePara").value = config.moistureStopLimit;
    el("limiteTemperatura").value = config.temperatureLimit;
  }
  el("ultimaConfig").textContent = "Última alteração: " + formatarDataHora(config.updatedAt);
}

function criarCelula(texto, classe) {
  const td = document.createElement("td");
  td.textContent = texto;
  if (classe) td.className = classe;
  return td;
}

function preencherTabela(idCorpo, linhas, numeroColunas, mensagemVazia) {
  const corpo = el(idCorpo);
  corpo.replaceChildren();

  if (linhas.length === 0) {
    const tr = document.createElement("tr");
    const td = criarCelula(mensagemVazia, "vazio");
    td.colSpan = numeroColunas;
    tr.appendChild(td);
    corpo.appendChild(tr);
    return;
  }

  linhas.forEach(function (celulas) {
    const tr = document.createElement("tr");
    celulas.forEach(function (celula) {
      tr.appendChild(criarCelula(celula.texto, celula.classe));
    });
    corpo.appendChild(tr);
  });
}

function desenharLeituras(leituras) {
  const linhas = (leituras || []).map(function (l) {
    return [
      { texto: formatarDataHora(l.createdAt) },
      { texto: numero(l.soilMoisture) + " %" },
      { texto: numero(l.temperature) + " °C" },
      { texto: numero(l.airHumidity) + " %" },
      { texto: l.pumpOn ? "Ligada" : "Desligada", classe: l.pumpOn ? "ligada" : "" },
      { texto: rotuloModo(l.operatingMode) }
    ];
  });
  preencherTabela("corpoLeituras", linhas, 6, "Ainda não há leituras.");
}

function desenharIrrigacoes(irrigacoes) {
  const linhas = (irrigacoes || []).map(function (i) {
    const emCurso = !i.endTime;
    return [
      { texto: formatarDataHora(i.startTime) },
      { texto: emCurso ? "Em curso" : formatarDataHora(i.endTime), classe: emCurso ? "em-curso" : "" },
      { texto: emCurso ? "—" : formatarDuracao(i.startTime, i.endTime) },
      { texto: i.reason || "—", classe: "motivo" },
      { texto: rotuloModo(i.mode) }
    ];
  });
  preencherTabela("corpoIrrigacoes", linhas, 5, "Ainda não há irrigações registadas.");
}

// ---------------------------------------------------------------
// Actualização periódica
// ---------------------------------------------------------------

function limiteLeituras() {
  const valor = parseInt(el("limiteLeituras").value, 10);
  return isNaN(valor) ? 20 : valor;
}

async function actualizarTudo() {
  if (estado.aActualizar) return;
  estado.aActualizar = true;

  try {
    const resultados = await Promise.all([
      pedir(API + "/status"),
      pedir(API + "/readings/latest"),
      pedir(API + "/config"),
      pedir(API + "/readings?limit=" + limiteLeituras()),
      pedir(API + "/irrigations?limit=20")
    ]);

    const status = resultados[0];
    const ultima = resultados[1];
    const config = resultados[2];
    const leituras = resultados[3];
    const irrigacoes = resultados[4];

    estado.configuracao = config;

    desenharValores(ultima, config);
    desenharEstado(status, config);
    desenharControlo(config);
    desenharFormulario(config);
    desenharLeituras(leituras);
    desenharIrrigacoes(irrigacoes);

    mostrarLigacao(true);
  } catch (erro) {
    mostrarLigacao(false, erro.message);
  } finally {
    estado.aActualizar = false;
  }
}

// ---------------------------------------------------------------
// Acções do utilizador
// ---------------------------------------------------------------

async function enviarConfiguracao(corpo, mensagemSucesso) {
  try {
    const nova = await enviarJson("PUT", API + "/config", corpo);
    estado.configuracao = nova;
    estado.formularioAlterado = false;
    notificar(mensagemSucesso, "ok");
  } catch (erro) {
    notificar(erro.message, "erro");
  }
  await actualizarTudo();
}

async function mudarModo(modo) {
  const c = estado.configuracao;
  if (!c) {
    notificar("A configuração ainda não foi carregada.", "erro");
    return;
  }
  await enviarConfiguracao({
    moistureLimit: c.moistureLimit,
    moistureStopLimit: c.moistureStopLimit,
    temperatureLimit: c.temperatureLimit,
    operatingMode: modo
  }, "Modo " + rotuloModo(modo).toLowerCase() + " activado.");
}

async function guardarLimites(evento) {
  evento.preventDefault();

  const c = estado.configuracao;
  if (!c) {
    notificar("A configuração ainda não foi carregada.", "erro");
    return;
  }

  const ligar = parseFloat(el("limiteLigar").value);
  const parar = parseFloat(el("limitePara").value);
  const temperatura = parseFloat(el("limiteTemperatura").value);

  if (isNaN(ligar) || isNaN(parar) || isNaN(temperatura)) {
    notificar("Preencha os três limites com números.", "erro");
    return;
  }
  if (ligar < 0 || ligar > 100 || parar < 0 || parar > 100) {
    notificar("Os limites de humidade devem estar entre 0 e 100.", "erro");
    return;
  }
  if (parar <= ligar) {
    notificar("O limite de paragem deve ser superior ao limite de activação.", "erro");
    return;
  }
  if (temperatura < -40 || temperatura > 80) {
    notificar("A temperatura deve estar entre -40 e 80 °C.", "erro");
    return;
  }

  await enviarConfiguracao({
    moistureLimit: ligar,
    moistureStopLimit: parar,
    temperatureLimit: temperatura,
    operatingMode: c.operatingMode
  }, "Limites guardados e enviados ao ESP32.");
}

async function comandarBomba(ligar) {
  const botoes = [el("btnLigar"), el("btnDesligar")];
  botoes.forEach(function (b) { b.disabled = true; });

  try {
    const resposta = await enviarJson("POST", API + "/pump", { pumpOn: ligar });
    notificar(resposta && resposta.mensagem ? resposta.mensagem : "Comando enviado.", "ok");
  } catch (erro) {
    notificar(erro.message, "erro");
  }

  // O ESP32 confirma o novo estado na leitura seguinte
  setTimeout(actualizarTudo, 1500);
  setTimeout(actualizarTudo, 6000);
  await actualizarTudo();
}

// ---------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------

function iniciar() {
  el("btnAuto").addEventListener("click", function () { mudarModo("AUTO"); });
  el("btnManual").addEventListener("click", function () { mudarModo("MANUAL"); });
  el("btnLigar").addEventListener("click", function () { comandarBomba(true); });
  el("btnDesligar").addEventListener("click", function () { comandarBomba(false); });
  el("formConfig").addEventListener("submit", guardarLimites);
  el("limiteLeituras").addEventListener("change", actualizarTudo);

  ["limiteLigar", "limitePara", "limiteTemperatura"].forEach(function (id) {
    el(id).addEventListener("input", function () { estado.formularioAlterado = true; });
  });

  actualizarTudo();
  setInterval(actualizarTudo, INTERVALO_MS);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", iniciar);
} else {
  iniciar();
}