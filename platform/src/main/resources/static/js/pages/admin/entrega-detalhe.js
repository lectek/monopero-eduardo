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

function formatarData(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

const STATUS_PARADA_CONCLUIDA = ["ENTREGUE", "CANCELADA"];
const STATUS_ROTA_JA_INICIADA = ["EM_EXECUCAO", "CONCLUIDA", "CANCELADA"];

async function carregarRota() {
  const resp = await AdminAuth.fetch(`/api/admin/entregas/rotas/${rotaId()}`);
  if (!resp.ok) {
    document.getElementById("rota-resumo").innerHTML = "<p>Rota não encontrada.</p>";
    return;
  }
  const rota = await resp.json();
  renderResumo(rota);
  renderParadas(rota.paradas || []);
}

function renderResumo(rota) {
  document.getElementById("rota-titulo").textContent = `Rota #${rota.id}`;
  document.getElementById("rota-resumo").innerHTML = `
    <p><strong>Data:</strong> ${escapeHtml(rota.dataOperacao)} &nbsp; <span class="badge badge--neutral">${escapeHtml(rota.statusLabel || rota.status)}</span></p>
    <p><strong>Origem:</strong> ${escapeHtml(rota.origem)}</p>
    <p><strong>Distância total:</strong> ${rota.distanciaTotalKm != null ? rota.distanciaTotalKm + " km" : "-"}</p>
    <p><strong>Entregador:</strong> ${escapeHtml(rota.entregadorNome) || "-"}</p>
    <p><strong>Iniciada em:</strong> ${formatarData(rota.iniciadaEm) || "-"}</p>
    <p><strong>Finalizada em:</strong> ${formatarData(rota.finalizadaEm) || "-"}</p>
  `;

  const btnIniciar = document.getElementById("btn-iniciar-rota");
  btnIniciar.hidden = STATUS_ROTA_JA_INICIADA.includes(rota.status);
}

function renderParadas(paradas) {
  const container = document.getElementById("paradas-lista");
  if (!paradas.length) {
    container.innerHTML = "<p>Nenhuma parada nesta rota.</p>";
    return;
  }

  container.innerHTML = paradas.map((p) => {
    const concluida = STATUS_PARADA_CONCLUIDA.includes(p.status);
    const chegou = p.status === "CHEGOU";
    const aCaminho = p.status === "A_CAMINHO";
    return `<div class="card p-4" data-parada="${p.id}" data-pedido="${p.pedidoId}">
      <p><strong>${p.ordem}. ${escapeHtml(p.clienteNome)}</strong> &nbsp; <span class="badge badge--neutral">${escapeHtml(p.statusLabel || p.status)}</span></p>
      <p>${escapeHtml(p.enderecoEntrega)}</p>
      <p>Código de entrega: <strong>${escapeHtml(p.codigoEntrega) || "-"}</strong>
        <button class="btn" data-regenerar-codigo="${p.pedidoId}" type="button" style="margin-left:0.5rem;">Regenerar código</button>
      </p>
      ${p.observacao ? `<p>Obs.: ${escapeHtml(p.observacao)}</p>` : ""}
      ${aCaminho ? `<button class="btn btn--primary" data-registrar-chegada="${p.id}" type="button">Registrar chegada</button>` : ""}
      ${chegou ? `
      <details>
        <summary class="btn" style="display:inline-block;cursor:pointer;">Confirmar entrega desta parada</summary>
        <form class="stack gap-8 mt-4" data-form-confirmar="${p.id}" style="max-width:360px;">
          <select class="input" name="formaPagamentoRecebida">
            <option value="">Forma de pagamento recebida (se houver)</option>
            <option value="PIX">Pix</option>
            <option value="DINHEIRO">Dinheiro</option>
            <option value="CARTAO">Cartão</option>
          </select>
          <input class="input" type="number" name="avaliacaoEntrega" min="1" max="5" placeholder="Avaliação (1-5, opcional)" />
          <input class="input" name="observacao" placeholder="Observação (opcional)" />
          <button class="btn btn--primary" type="submit">Confirmar</button>
        </form>
      </details>` : ""}
      ${!concluida && !chegou && !aCaminho ? `<p><em>Aguardando a parada anterior ser concluída.</em></p>` : ""}
    </div>`;
  }).join("");

  container.querySelectorAll("[data-regenerar-codigo]").forEach((btn) => {
    btn.addEventListener("click", () => regenerarCodigo(btn.getAttribute("data-regenerar-codigo")));
  });
  container.querySelectorAll("[data-registrar-chegada]").forEach((btn) => {
    btn.addEventListener("click", () => registrarChegada(btn.getAttribute("data-registrar-chegada"), btn));
  });
  container.querySelectorAll("[data-form-confirmar]").forEach((form) => {
    form.addEventListener("submit", (ev) => {
      ev.preventDefault();
      confirmarParada(form.getAttribute("data-form-confirmar"), form);
    });
  });
}

async function registrarChegada(paradaId, btn) {
  btn.disabled = true;
  const resp = await AdminAuth.fetch(`/api/admin/entregas/rotas/${rotaId()}/paradas/${paradaId}/chegada`, { method: "POST" });
  if (!resp.ok) {
    alert("Não foi possível registrar a chegada.");
    btn.disabled = false;
    return;
  }
  await carregarRota();
}

async function iniciarRota() {
  const btn = document.getElementById("btn-iniciar-rota");
  btn.disabled = true;
  const resp = await AdminAuth.fetch(`/api/admin/entregas/rotas/${rotaId()}/iniciar`, { method: "POST" });
  btn.disabled = false;
  if (!resp.ok) {
    alert("Não foi possível iniciar a rota.");
    return;
  }
  await carregarRota();
}

async function confirmarParada(paradaId, form) {
  const dados = new FormData(form);
  const body = {
    formaPagamentoRecebida: dados.get("formaPagamentoRecebida") || null,
    avaliacaoEntrega: dados.get("avaliacaoEntrega") ? Number(dados.get("avaliacaoEntrega")) : null,
    ocorrencias: [],
    observacao: dados.get("observacao") || null,
  };
  const resp = await AdminAuth.fetch(`/api/admin/entregas/rotas/${rotaId()}/paradas/${paradaId}/confirmar`, {
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

async function regenerarCodigo(pedidoId) {
  const resp = await AdminAuth.fetch(`/api/admin/entregas/${pedidoId}/codigo/regenerar`, { method: "POST" });
  if (!resp.ok) {
    alert("Não foi possível regenerar o código.");
    return;
  }
  await carregarRota();
}
