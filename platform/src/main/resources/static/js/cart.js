/* Carrinho client-side (localStorage) — o backend não tem sessão de carrinho
 * (é uma API JSON stateless), então o carrinho vive só no navegador do
 * cliente até o checkout, quando tudo é enviado de uma vez pra
 * POST /api/public/pedidos. Chave do item = nome|cor|peso (mesma chave
 * natural da tabela products do rbp.db — não tem ID numérico). */
(() => {
  const STORAGE_KEY = "mm_carrinho_v1";

  function keyFor(item) {
    return [item.nome, item.cor || "", item.peso || ""].join("|");
  }

  function getCart() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  }

  function saveCart(cart) {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(cart));
    } catch {
      // Armazenamento indisponível (ex.: aba anônima bloqueando) — carrinho só não persiste entre recarregamentos.
    }
    updateCartBadge();
  }

  function addToCart(item, quantidade) {
    const qty = Math.max(1, Number(quantidade) || 1);
    const cart = getCart();
    const key = keyFor(item);
    const existing = cart.find((i) => keyFor(i) === key);
    if (existing) {
      existing.quantidade += qty;
    } else {
      cart.push({
        nome: item.nome,
        cor: item.cor || "",
        peso: item.peso || "",
        preco: Number(item.preco) || 0,
        descricao: item.descricao || "",
        quantidade: qty
      });
    }
    saveCart(cart);
  }

  function updateQuantidade(key, quantidade) {
    const qty = Math.max(1, Number(quantidade) || 1);
    const cart = getCart().map((i) => (keyFor(i) === key ? { ...i, quantidade: qty } : i));
    saveCart(cart);
  }

  function removeFromCart(key) {
    saveCart(getCart().filter((i) => keyFor(i) !== key));
  }

  function clearCart() {
    saveCart([]);
  }

  function cartCount() {
    return getCart().reduce((sum, i) => sum + i.quantidade, 0);
  }

  function cartTotal() {
    return getCart().reduce((sum, i) => sum + i.quantidade * i.preco, 0);
  }

  function updateCartBadge() {
    const badge = document.querySelector("[data-cart-count]");
    if (!badge) return;
    const count = cartCount();
    badge.textContent = String(count);
    badge.hidden = count === 0;
  }

  document.addEventListener("DOMContentLoaded", updateCartBadge);

  window.MiniMercadinhoCart = {
    keyFor,
    getCart,
    addToCart,
    updateQuantidade,
    removeFromCart,
    clearCart,
    cartCount,
    cartTotal,
    updateCartBadge
  };
})();
