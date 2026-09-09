document.addEventListener("DOMContentLoaded", () => {
  AdminAuth.requireAuth();
  carregarPedidos();
});

const STATUS_LABEL = {
  ABERTO: "Aberto",
  AGUARDANDO_PAGAMENTO: "Aguardando pagamento",
  PAGO: "Pago",
  PRONTO_PARA_ENTREGA: "Pronto p/ entrega",
  PRONTO_PARA_RETIRADA: "Pronto p/ retirada",
  SAIU_PARA_ENTREGA: "Saiu p/ entrega",
  ENVIADO: "Enviado",
  ENTREGUE: "Entregue",
  CANCELADO: "Cancelado",
};

const STATUS_BADGE_CLASS = {
  PAGO: "badge--success",
  ENTREGUE: "badge--success",
  CANCELADO: "badge--danger",
  ABERTO: "badge--warning",
  AGUARDANDO_PAGAMENTO: "badge--warning",
};

const TIPO_PAGAMENTO_LABEL = {
  PIX: "Pix",
  DINHEIRO: "Dinheiro",
  CARTAO_CREDITO: "Cartão de crédito",
  CARTAO_DEBITO: "Cartão de débito",
  BOLETO: "Boleto",
  CUSTOM: "Outro",
};

const MODO_ENTREGA_LABEL = {
  RETIRADA: "Retirada",
  ENTREGA: "Entrega",
};

function formatarMoeda(valor) {
  return Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function formatarData(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

async function carregarPedidos() {
  const resp = await AdminAuth.fetch("/api/admin/pedidos");
  const pedidos = await resp.json();

  const tabela = document.getElementById("tabela-pedidos");
  const vazio = document.getElementById("pedidos-vazio");
  const corpo = document.getElementById("pedidos-corpo");

  if (!pedidos.length) {
    tabela.hidden = true;
    vazio.hidden = false;
    return;
  }
  vazio.hidden = true;
  tabela.hidden = false;

  corpo.innerHTML = pedidos.map((p) => {
    const badgeClasse = STATUS_BADGE_CLASS[p.status] || "badge--neutral";
    const podeConfirmarDinheiro = p.tipoPagamento === "DINHEIRO" && p.status === "ABERTO";
    return `<tr>
      <td>${p.id}</td>
      <td>${formatarData(p.data)}</td>
      <td>${escapeHtml(p.clienteNome)}<br><small>${escapeHtml(p.clienteEmail)}</small></td>
      <td>${formatarMoeda(p.total)}</td>
      <td>${TIPO_PAGAMENTO_LABEL[p.tipoPagamento] || p.tipoPagamento}</td>
      <td>${MODO_ENTREGA_LABEL[p.modoEntrega] || p.modoEntrega || "-"}</td>
      <td><span class="badge ${badgeClasse}">${STATUS_LABEL[p.status] || p.status}</span></td>
      <td>${podeConfirmarDinheiro ? `<button class="btn" data-confirmar="${p.id}">Confirmar recebimento</button>` : ""}</td>
    </tr>`;
  }).join("");

  corpo.querySelectorAll("[data-confirmar]").forEach((btn) => {
    btn.addEventListener("click", () => confirmarRecebimento(btn.getAttribute("data-confirmar"), btn));
  });
}

async function confirmarRecebimento(pedidoId, btn) {
  btn.disabled = true;
  btn.textContent = "Confirmando...";
  try {
    const resp = await AdminAuth.fetch(`/api/admin/pedidos/${pedidoId}/confirmar-recebimento-dinheiro`, { method: "POST" });
    if (!resp.ok) {
      alert("Não foi possível confirmar o recebimento.");
      btn.disabled = false;
      btn.textContent = "Confirmar recebimento";
      return;
    }
    await carregarPedidos();
  } catch (e) {
    // AdminAuth.fetch já redireciona pro login em caso de 401.
  }
}

function escapeHtml(value) {
  const div = document.createElement("div");
  div.textContent = value == null ? "" : value;
  return div.innerHTML;
}
