(() => {
  const lista = document.getElementById("lista-itens");
  const subtotalEl = document.getElementById("resumo-subtotal");
  const totalEl = document.getElementById("resumo-total");
  const btnCheckout = document.getElementById("btn-checkout");
  const PLACEHOLDER_IMG = "/img/produtos/placeholder-generico.png";

  document.addEventListener("DOMContentLoaded", render);

  function render() {
    const cart = window.MiniMercadinhoCart.getCart();
    if (!cart.length) {
      lista.innerHTML = `
        <div class="card carrinho-vazio">
          <strong>Seu carrinho está vazio</strong>
          <p>Volte aos produtos para adicionar itens.</p>
          <a class="btn mt-2" href="/produtos">Ver produtos</a>
        </div>`;
      if (btnCheckout) btnCheckout.setAttribute("aria-disabled", "true");
    } else {
      lista.innerHTML = cart.map(renderItem).join("");
      bindItemActions();
    }
    atualizarResumo(cart);
  }

  function renderItem(item) {
    const key = window.MiniMercadinhoCart.keyFor(item);
    const variacao = [item.cor, item.peso].filter((v) => v && v.trim()).join(" • ");
    const precoFmt = Number(item.preco).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
    return `
      <div class="card item-carrinho" data-key="${escapeAttr(key)}">
        <img class="item-carrinho__img" src="${PLACEHOLDER_IMG}" alt="" />
        <div class="item-carrinho__info">
          <span class="item-carrinho__nome">${escapeHtml(item.nome)}</span>
          <small class="text-muted" ${variacao ? "" : "hidden"}>${escapeHtml(variacao)}</small>
          <span class="item-carrinho__preco">${precoFmt}</span>
        </div>
        <div class="item-carrinho__qtd">
          <button type="button" class="btn btn-ghost" data-dec>-</button>
          <input type="number" min="1" value="${item.quantidade}" data-qtd />
          <button type="button" class="btn btn-ghost" data-inc>+</button>
        </div>
        <button type="button" class="btn btn-ghost" data-remover title="Remover">Remover</button>
      </div>`;
  }

  function bindItemActions() {
    lista.querySelectorAll("[data-key]").forEach((row) => {
      const key = row.dataset.key;
      const input = row.querySelector("[data-qtd]");

      row.querySelector("[data-inc]")?.addEventListener("click", () => {
        window.MiniMercadinhoCart.updateQuantidade(key, Number(input.value) + 1);
        render();
      });
      row.querySelector("[data-dec]")?.addEventListener("click", () => {
        window.MiniMercadinhoCart.updateQuantidade(key, Math.max(1, Number(input.value) - 1));
        render();
      });
      input?.addEventListener("change", () => {
        window.MiniMercadinhoCart.updateQuantidade(key, input.value);
        render();
      });
      row.querySelector("[data-remover]")?.addEventListener("click", () => {
        window.MiniMercadinhoCart.removeFromCart(key);
        render();
      });
    });
  }

  function atualizarResumo(cart) {
    const total = cart.reduce((sum, i) => sum + i.quantidade * i.preco, 0);
    const fmt = total.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
    if (subtotalEl) subtotalEl.textContent = fmt;
    if (totalEl) totalEl.textContent = fmt;
  }

  function escapeHtml(s) {
    return String(s ?? "").replace(/[&<>"']/g, (m) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[m]));
  }
  function escapeAttr(s) {
    return escapeHtml(s);
  }
})();
