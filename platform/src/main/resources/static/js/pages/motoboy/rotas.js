document.addEventListener("DOMContentLoaded", () => {
  AdminAuth.requireAuth();
  carregarRotas();
});

const STATUS_BADGE_CLASS = {
  EM_EXECUCAO: "badge--warning",
  CONCLUIDA: "badge--success",
  CANCELADA: "badge--danger",
};

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : value;
  return div.innerHTML;
}

async function carregarRotas() {
  const resp = await AdminAuth.fetch("/api/motoboy/entregas/minhas-rotas");
  const rotas = await resp.json();

  const vazio = document.getElementById("rotas-vazio");
  const lista = document.getElementById("rotas-lista");

  if (!rotas.length) {
    vazio.hidden = false;
    lista.innerHTML = "";
    return;
  }
  vazio.hidden = true;

  lista.innerHTML = rotas.map((r) => {
    const badge = STATUS_BADGE_CLASS[r.status] || "badge--neutral";
    return `<a class="card p-4" href="/motoboy/rotas/${r.id}" style="display:block;text-decoration:none;color:inherit;">
      <p><strong>Rota #${r.id}</strong> &nbsp; <span class="badge ${badge}">${escapeHtml(r.statusLabel || r.status)}</span></p>
      <p>${escapeHtml(r.origem)}</p>
      <p>${r.totalParadas} parada(s) — ${r.distanciaTotalKm != null ? r.distanciaTotalKm + " km" : ""}</p>
    </a>`;
  }).join("");
}
