(() => {
  const form = document.getElementById("form-checkout");
  const itensEl = document.getElementById("checkout-itens");
  const totalEl = document.getElementById("checkout-total");
  const campoEndereco = document.getElementById("campo-endereco");
  const enderecoInput = campoEndereco?.querySelector("[name=enderecoEntrega]");
  const erroEl = document.getElementById("checkout-erro");
  const btnConfirmar = document.getElementById("btn-confirmar");
  const conteudoEl = document.getElementById("checkout-conteudo");
  const vazioEl = document.getElementById("checkout-vazio");
  const resultadoEl = document.getElementById("checkout-resultado");

  document.addEventListener("DOMContentLoaded", () => {
    const cart = window.MiniMercadinhoCart.getCart();
    if (!cart.length) {
      conteudoEl.hidden = true;
      vazioEl.hidden = false;
      return;
    }
    renderResumo(cart);
    wireModoEntrega();
    form?.addEventListener("submit", onSubmit);
  });

  function renderResumo(cart) {
    itensEl.innerHTML = cart
      .map((i) => {
        const subtotal = (i.preco * i.quantidade).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
        const variacao = [i.cor, i.peso].filter((v) => v && v.trim()).join(" • ");
        return `<div class="checkout-item"><span>${i.quantidade}x ${escapeHtml(i.nome)}${variacao ? " (" + escapeHtml(variacao) + ")" : ""}</span><strong>${subtotal}</strong></div>`;
      })
      .join("");
    const total = cart.reduce((sum, i) => sum + i.quantidade * i.preco, 0);
    totalEl.textContent = total.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
  }

  function wireModoEntrega() {
    form.querySelectorAll('input[name="modoEntrega"]').forEach((radio) => {
      radio.addEventListener("change", () => {
        const isEntrega = form.querySelector('input[name="modoEntrega"]:checked')?.value === "ENTREGA";
        campoEndereco.hidden = !isEntrega;
        if (enderecoInput) enderecoInput.required = isEntrega;
      });
    });
  }

  async function onSubmit(e) {
    e.preventDefault();
    esconderErro();
    btnConfirmar.disabled = true;

    const cart = window.MiniMercadinhoCart.getCart();
    const formData = new FormData(form);
    const body = {
      customerNome: formData.get("customerNome"),
      customerEmail: formData.get("customerEmail"),
      customerTelefone: formData.get("customerTelefone"),
      itens: cart.map((i) => ({ nome: i.nome, cor: i.cor, peso: i.peso, quantidade: i.quantidade })),
      modoEntrega: formData.get("modoEntrega"),
      enderecoEntrega: formData.get("enderecoEntrega") || null,
      tipoPagamento: formData.get("tipoPagamento")
    };

    try {
      const resp = await fetch("/api/public/pedidos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      const data = await resp.json().catch(() => null);
      if (!resp.ok) {
        mostrarErro((data && data.message) || `Não foi possível concluir o pedido (HTTP ${resp.status}).`);
        btnConfirmar.disabled = false;
        return;
      }
      window.MiniMercadinhoCart.clearCart();
      mostrarResultado(data);
    } catch {
      mostrarErro("Falha de rede ao enviar o pedido. Tente novamente.");
      btnConfirmar.disabled = false;
    }
  }

  function mostrarResultado(pedido) {
    conteudoEl.hidden = true;
    resultadoEl.hidden = false;

    const totalFmt = Number(pedido.total || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
    document.getElementById("resultado-mensagem").textContent =
      `Pedido #${pedido.pedidoId} confirmado — total ${totalFmt}.`;

    if (pedido.pixQrCodeBase64 || pedido.pixQrCode) {
      const painel = document.getElementById("resultado-pix");
      painel.hidden = false;
      if (pedido.pixQrCodeBase64) {
        document.getElementById("resultado-pix-qrcode").src = "data:image/png;base64," + pedido.pixQrCodeBase64;
      } else {
        document.getElementById("resultado-pix-qrcode").hidden = true;
      }
      document.getElementById("resultado-pix-copia-cola").value = pedido.pixQrCode || "";
    }
  }

  function mostrarErro(msg) {
    erroEl.textContent = msg;
    erroEl.hidden = false;
  }
  function esconderErro() {
    erroEl.hidden = true;
  }

  function escapeHtml(s) {
    return String(s ?? "").replace(/[&<>"']/g, (m) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[m]));
  }
})();
