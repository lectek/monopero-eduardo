/* Catálogo público — adaptado do catalogo.js do ParaisoPet: lá o produto
 * tem ID numérico e "adicionar ao carrinho" é um POST pro carrinho de
 * sessão do servidor; aqui o produto é identificado por nome+cor+peso
 * (mesma chave da tabela products do rbp.db) e o carrinho é local
 * (localStorage, ver cart.js) — sem sessão no backend. */
(() => {
  const grid = document.getElementById("catalogo-grid");
  const formBusca = document.getElementById("form-busca");
  const inputQ = document.getElementById("q");
  const btnLimpar = document.getElementById("btn-limpar");
  const PLACEHOLDER_IMG = "/img/produtos/placeholder-generico.png";

  let produtosCarregados = [];

  document.addEventListener("DOMContentLoaded", () => {
    carregar();
    formBusca?.addEventListener("submit", (e) => {
      e.preventDefault();
      renderizar(filtrar(inputQ?.value || ""));
    });
    btnLimpar?.addEventListener("click", () => {
      if (inputQ) inputQ.value = "";
      renderizar(produtosCarregados);
    });
  });

  async function carregar() {
    if (!grid) return;
    grid.innerHTML = `<div class="card p-4">Carregando…</div>`;
    try {
      const resp = await fetch("/api/public/produtos", { headers: { Accept: "application/json" } });
      if (!resp.ok) {
        grid.innerHTML = `<div class="card p-4">Erro ao carregar (${resp.status}).</div>`;
        return;
      }
      produtosCarregados = await resp.json();
      renderizar(produtosCarregados);
    } catch {
      grid.innerHTML = `<div class="card p-4">Falha de rede ao consultar o catálogo.</div>`;
    }
  }

  function filtrar(termo) {
    const q = termo.trim().toLowerCase();
    if (!q) return produtosCarregados;
    return produtosCarregados.filter((p) => (p.nome || "").toLowerCase().includes(q));
  }

  function renderizar(produtos) {
    if (!grid) return;
    if (!produtos.length) {
      grid.innerHTML = `<div class="card p-4">Nenhum produto encontrado.</div>`;
      return;
    }
    grid.innerHTML = produtos.map(renderCard).join("");
    bindCardActions();
  }

  function renderCard(p) {
    const precoFmt = Number(p.preco || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
    const variacao = [p.cor, p.peso].filter((v) => v && v.trim()).join(" • ");
    const disponivel = p.quantidadeDisponivel > 0;

    return `
      <article class="card p-3 product-card" data-card
               data-nome="${escapeAttr(p.nome)}" data-cor="${escapeAttr(p.cor || "")}" data-peso="${escapeAttr(p.peso || "")}"
               data-preco="${Number(p.preco || 0)}" data-descricao="${escapeAttr(p.descricao || "")}">
        <img src="${PLACEHOLDER_IMG}" alt="${escapeHtml(p.nome)}" loading="lazy" />
        <div class="stack mt-2">
          <div class="product-card__title line-clamp-2">${escapeHtml(p.nome)}</div>
          <small class="text-muted" ${variacao ? "" : "hidden"}>${escapeHtml(variacao)}</small>
          <p class="text-muted m-0 line-clamp-2">${escapeHtml(p.descricao || "")}</p>
          <div class="between mt-1">
            <span class="product-card__price">${precoFmt}</span>
            <small class="text-muted">${p.quantidadeDisponivel} em estoque</small>
          </div>
          <button class="btn mt-2" data-add-carrinho ${disponivel ? "" : "disabled"}>
            ${disponivel ? "Adicionar ao carrinho" : "Indisponível"}
          </button>
        </div>
      </article>
    `;
  }

  function bindCardActions() {
    document.querySelectorAll("[data-add-carrinho]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const card = btn.closest("[data-card]");
        if (!card) return;
        window.MiniMercadinhoCart.addToCart(
          {
            nome: card.dataset.nome,
            cor: card.dataset.cor,
            peso: card.dataset.peso,
            preco: card.dataset.preco,
            descricao: card.dataset.descricao
          },
          1
        );
        toast("Adicionado ao carrinho!");
      });
    });
  }

  function toast(msg) {
    const el = document.createElement("div");
    el.textContent = msg;
    Object.assign(el.style, {
      position: "fixed", right: "1rem", bottom: "1rem",
      padding: "0.75rem 1rem", background: "#16a34a",
      color: "#fff", borderRadius: "8px", zIndex: 9999, boxShadow: "0 8px 30px rgba(0,0,0,.2)"
    });
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 2200);
  }

  function escapeHtml(s) {
    return String(s ?? "").replace(/[&<>"']/g, (m) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[m]));
  }
  function escapeAttr(s) {
    return escapeHtml(s);
  }
})();
