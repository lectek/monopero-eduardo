document.addEventListener("DOMContentLoaded", () => {
  AdminAuth.requireAuth();
  carregarElegiveis();
  carregarRotas();

  document.getElementById("btn-preview").addEventListener("click", preVisualizarRota);
  document.getElementById("btn-criar-rota").addEventListener("click", criarRota);
});

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : value;
  return div.innerHTML;
}

function formatarMoeda(valor) {
  return Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function formatarData(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

function pedidosSelecionados() {
  return Array.from(document.querySelectorAll("[data-pedido-elegivel]:checked")).map((el) => Number(el.value));
}

async function carregarElegiveis() {
  const resp = await AdminAuth.fetch("/api/admin/entregas/pedidos-elegiveis");
  const pedidos = await resp.json();

  const tabela = document.getElementById("tabela-elegiveis");
  const vazio = document.getElementById("elegiveis-vazio");
  const corpo = document.getElementById("elegiveis-corpo");

  if (!pedidos.length) {
    tabela.hidden = true;
    vazio.hidden = false;
    return;
  }
  vazio.hidden = true;
  tabela.hidden = false;

  corpo.innerHTML = pedidos.map((p) => `<tr>
    <td><input type="checkbox" data-pedido-elegivel value="${p.id}" /></td>
    <td>${escapeHtml(p.numero || p.id)}</td>
    <td>${escapeHtml(p.clienteNome)}</td>
    <td>${escapeHtml(p.enderecoEntrega)}</td>
    <td>${formatarMoeda(p.total)}</td>
    <td><span class="badge badge--neutral">${escapeHtml(p.statusLabel || p.status)}</span></td>
  </tr>`).join("");
}

async function carregarRotas() {
  const resp = await AdminAuth.fetch("/api/admin/entregas/rotas");
  const rotas = await resp.json();

  const tabela = document.getElementById("tabela-rotas");
  const vazio = document.getElementById("rotas-vazio");
  const corpo = document.getElementById("rotas-corpo");

  if (!rotas.length) {
    tabela.hidden = true;
    vazio.hidden = false;
    return;
  }
  vazio.hidden = true;
  tabela.hidden = false;

  corpo.innerHTML = rotas.map((r) => `<tr>
    <td>${r.id}</td>
    <td>${escapeHtml(r.dataOperacao)}</td>
    <td>${escapeHtml(r.origem)}</td>
    <td>${r.distanciaTotalKm != null ? r.distanciaTotalKm + " km" : "-"}</td>
    <td>${r.totalParadas}</td>
    <td><span class="badge badge--neutral">${escapeHtml(r.statusLabel || r.status)}</span></td>
    <td><a class="btn" href="/admin/entregas/rotas/${r.id}">Ver</a></td>
  </tr>`).join("");
}

async function preVisualizarRota() {
  const pedidoIds = pedidosSelecionados();
  const origem = document.getElementById("origem-input").value.trim();
  const resultado = document.getElementById("preview-resultado");
  const btnCriar = document.getElementById("btn-criar-rota");

  if (!pedidoIds.length) {
    alert("Selecione pelo menos um pedido.");
    return;
  }

  const resp = await AdminAuth.fetch("/api/admin/entregas/roteirizar", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pedidoIds, origem: origem || null }),
  });

  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    alert(erro.detail || erro.message || "Não foi possível pré-visualizar a rota.");
    return;
  }

  const preview = await resp.json();
  document.getElementById("preview-resumo").textContent =
    `Origem: ${preview.origem || "-"} — Distância total: ${preview.distanciaTotalKm != null ? preview.distanciaTotalKm + " km" : "-"}`;
  document.getElementById("preview-paradas").innerHTML = (preview.paradas || []).map((p) =>
    `<li>${escapeHtml(p.clienteNome)} — ${escapeHtml(p.enderecoEntrega)} (${p.distanciaAcumuladaKm != null ? p.distanciaAcumuladaKm + " km" : "-"})</li>`
  ).join("");
  resultado.hidden = false;
  btnCriar.disabled = false;
}

async function criarRota() {
  const pedidoIds = pedidosSelecionados();
  const origem = document.getElementById("origem-input").value.trim();
  const btnCriar = document.getElementById("btn-criar-rota");

  btnCriar.disabled = true;
  btnCriar.textContent = "Criando...";

  const resp = await AdminAuth.fetch("/api/admin/entregas/rotas", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ pedidoIds, origem: origem || null }),
  });

  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    alert(erro.detail || erro.message || "Não foi possível criar a rota.");
    btnCriar.disabled = false;
    btnCriar.textContent = "Criar rota";
    return;
  }

  const rota = await resp.json();
  window.location.href = `/admin/entregas/rotas/${rota.id}`;
}
