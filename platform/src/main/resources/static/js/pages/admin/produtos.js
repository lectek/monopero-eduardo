document.addEventListener("DOMContentLoaded", () => {
  AdminAuth.requireAuth();
  carregarProdutos();
});

function formatarMoeda(valor) {
  return Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : value;
  return div.innerHTML;
}

async function carregarProdutos() {
  const resp = await AdminAuth.fetch("/api/admin/produtos");
  const produtos = await resp.json();

  const tabela = document.getElementById("tabela-produtos");
  const vazio = document.getElementById("produtos-vazio");
  const corpo = document.getElementById("produtos-corpo");

  if (!produtos.length) {
    tabela.hidden = true;
    vazio.hidden = false;
    return;
  }
  vazio.hidden = true;
  tabela.hidden = false;

  corpo.innerHTML = produtos.map((p) => {
    const naVitrine = p.preco > 0 && p.quantidade > 0;
    return `<tr>
      <td>${escapeHtml(p.nome)}</td>
      <td>${escapeHtml(p.cor)}</td>
      <td>${escapeHtml(p.peso)}</td>
      <td>${p.quantidade}</td>
      <td>${p.preco > 0 ? formatarMoeda(p.preco) : "-"}</td>
      <td>${escapeHtml(p.codigoBarras)}</td>
      <td><span class="badge ${naVitrine ? "badge--success" : "badge--neutral"}">${naVitrine ? "Sim" : "Não"}</span></td>
    </tr>`;
  }).join("");
}
