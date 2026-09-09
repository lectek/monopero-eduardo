document.addEventListener("DOMContentLoaded", () => {
  AdminAuth.requireAuth();
  carregarRota();
  document.getElementById("btn-iniciar-rota").addEventListener("click", iniciarRota);
});

function rotaId() {
  const partes = window.location.pathname.split("/").filter(Boolean);
  return partes[partes.length - 1];
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : value;
  return div.innerHTML;
}

function formatarMoeda(valor) {
  return Number(valor || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

const STATUS_ROTA_JA_INICIADA = ["EM_EXECUCAO", "CONCLUIDA", "CANCELADA"];

async function carregarRota() {
  const resp = await AdminAuth.fetch(`/api/motoboy/entregas/rotas/${rotaId()}`);
  if (!resp.ok) {
    document.getElementById("rota-resumo").innerHTML = "<p>Rota não encontrada.</p>";
    return;
  }
  const rota = await resp.json();
  renderResumo(rota);
  renderProximaParada(rota.proximaParada);

  if (rota.status === "EM_EXECUCAO" || rota.status === "CONCLUIDA") {
    carregarGanho();
  }
}

function renderResumo(rota) {
  document.getElementById("rota-titulo").textContent = `Rota #${rota.id}`;
  document.getElementById("rota-resumo").innerHTML = `
    <p><span class="badge badge--neutral">${escapeHtml(rota.statusLabel || rota.status)}</span></p>
    <p>${escapeHtml(rota.origem)}</p>
    <p>${rota.entregues || 0} de ${rota.totalParadas} parada(s) entregue(s)</p>
  `;
  const btnIniciar = document.getElementById("btn-iniciar-rota");
  btnIniciar.hidden = STATUS_ROTA_JA_INICIADA.includes(rota.status);
}

async function carregarGanho() {
  const resp = await AdminAuth.fetch(`/api/motoboy/entregas/rotas/${rotaId()}/ganho`);
  if (!resp.ok) return;
  const g = await resp.json();

  const card = document.getElementById("ganho-card");
  const conteudo = document.getElementById("ganho-conteudo");
  card.hidden = false;

  let html = `<p><strong>Sua comissão (${g.percentualComissao}% do frete):</strong> ${formatarMoeda(g.comissaoMotoboy)}</p>`;
  if (Number(g.dinheiroColetado) > 0) {
    html += `<p><strong>Dinheiro coletado nas entregas:</strong> ${formatarMoeda(g.dinheiroColetado)}</p>`;
    html += `<p><strong>Você já fica com sua comissão do dinheiro que coletou.</strong></p>`;
    if (Number(g.valorDevolverLoja) > 0) {
      html += `<p style="font-size:1.1em;"><strong>Devolver pro mercadinho:</strong> ${formatarMoeda(g.valorDevolverLoja)}</p>`;
    }
  }
  if (Number(g.comissaoNaoCobertaPorDinheiro) > 0) {
    html += `<p><strong>Parte da comissão que não veio do dinheiro (acertar com a loja):</strong> ${formatarMoeda(g.comissaoNaoCobertaPorDinheiro)}</p>`;
  }
  conteudo.innerHTML = html;
}

function renderProximaParada(parada) {
  const container = document.getElementById("paradas-lista");

  if (!parada) {
    container.innerHTML = "<p>Nenhuma parada pendente — rota concluída ou ainda não iniciada.</p>";
    return;
  }

  const chegou = parada.status === "CHEGOU";
  const aCaminho = parada.status === "A_CAMINHO";

  container.innerHTML = `<div class="card p-4" data-parada="${parada.id}" data-pedido="${parada.pedidoId}">
    <p><strong>Próxima parada — ${escapeHtml(parada.clienteNome)}</strong> &nbsp; <span class="badge badge--neutral">${escapeHtml(parada.statusLabel || parada.status)}</span></p>
    <p>${escapeHtml(parada.enderecoEntrega)}</p>
    <p><a href="${parada.googleMapsUrl}" target="_blank" rel="noopener" class="btn">Abrir no Maps</a></p>
    <p>Código de entrega: <strong>${escapeHtml(parada.codigoEntrega) || "-"}</strong></p>
    ${parada.pagamentoSolicitado ? `<p>Pagamento esperado: <strong>${escapeHtml(parada.pagamentoSolicitado)}</strong></p>` : ""}
    ${aCaminho ? `<button class="btn btn--primary" data-registrar-chegada="${parada.id}" type="button">Cheguei</button>` : ""}
    ${chegou ? `
    <form class="stack gap-8 mt-4" data-form-confirmar="${parada.id}">
      <select class="input" name="formaPagamentoRecebida">
        <option value="">Como recebeu o pagamento?</option>
        <option value="PIX">Pix</option>
        <option value="DINHEIRO">Dinheiro</option>
        <option value="CARTAO">Cartão</option>
      </select>
      <input class="input" name="observacao" placeholder="Observação (opcional)" />
      <button class="btn btn--primary" type="submit">Confirmar entrega</button>
    </form>` : ""}
  </div>`;

  const btnChegada = container.querySelector("[data-registrar-chegada]");
  if (btnChegada) {
    btnChegada.addEventListener("click", () => registrarChegada(btnChegada.getAttribute("data-registrar-chegada"), btnChegada));
  }
  const formConfirmar = container.querySelector("[data-form-confirmar]");
  if (formConfirmar) {
    formConfirmar.addEventListener("submit", (ev) => {
      ev.preventDefault();
      confirmarParada(formConfirmar.getAttribute("data-form-confirmar"), formConfirmar);
    });
  }
}

async function iniciarRota() {
  const btn = document.getElementById("btn-iniciar-rota");
  btn.disabled = true;
  const resp = await AdminAuth.fetch(`/api/motoboy/entregas/rotas/${rotaId()}/iniciar`, { method: "POST" });
  btn.disabled = false;
  if (!resp.ok) {
    alert("Não foi possível iniciar a rota.");
    return;
  }
  await carregarRota();
}

async function registrarChegada(paradaId, btn) {
  btn.disabled = true;
  const resp = await AdminAuth.fetch(`/api/motoboy/entregas/rotas/${rotaId()}/paradas/${paradaId}/chegada`, { method: "POST" });
  if (!resp.ok) {
    alert("Não foi possível registrar a chegada.");
    btn.disabled = false;
    return;
  }
  await carregarRota();
}

async function confirmarParada(paradaId, form) {
  const dados = new FormData(form);
  const body = {
    formaPagamentoRecebida: dados.get("formaPagamentoRecebida") || null,
    ocorrencias: [],
    observacao: dados.get("observacao") || null,
  };
  const resp = await AdminAuth.fetch(`/api/motoboy/entregas/rotas/${rotaId()}/paradas/${paradaId}/confirmar`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    alert("Não foi possível confirmar essa parada.");
    return;
  }
  await carregarRota();
}
