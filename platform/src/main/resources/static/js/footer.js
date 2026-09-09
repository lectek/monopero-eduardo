// footer.js
document.addEventListener("DOMContentLoaded", () => {
  initNewsletter();
  initLangChips();
  initBackToTop();
});

/* ==============================
   📧 Newsletter (com fallback)
   ============================== */
function initNewsletter() {
  // funciona com id="newsletter-form" ou class="newsletter-form"
  const form =
    document.getElementById("newsletter-form") ||
    document.querySelector("footer form.newsletter-form");

  if (!form) return;

  const emailInput =
    document.getElementById("news-email") ||
    form.querySelector('input[type="email"]');

  // status preferencial: #newsletter-status; senão cria um
  let statusEl = document.getElementById("newsletter-status");
  if (!statusEl) {
    statusEl = document.createElement("output");
    statusEl.id = "newsletter-status";
    statusEl.className = "newsletter-status";
    statusEl.setAttribute("aria-live", "polite");
    statusEl.setAttribute("aria-atomic", "true");
    form.appendChild(statusEl);
  }

  const honeypot = form.querySelector('input[name="website"]');
  let sending = false;

  // Se já inscrito, avisa (opcional)
  const saved = localStorage.getItem("newsletter_subscribed");
  if (saved === "1") {
    showMessage("Inscrição ativa. Obrigado! 🎉", "success");
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (sending) return;

    const email = (emailInput?.value || "").trim();

    if (!isValidEmail(email)) {
      showMessage("Digite um e-mail válido.", "error");
      emailInput?.focus();
      return;
    }

    if (honeypot && honeypot.value) {
      // provável bot; finge sucesso para não dar dica
      showMessage("Inscrição realizada com sucesso! 🎉", "success");
      form.reset();
      return;
    }

    sending = true;
    form.classList.add("is-submitting");
    showMessage("Enviando…", "info");

    // Tenta enviar de verdade se o form tiver action; caso contrário, simula
    const action = form.getAttribute("action") || form.getAttribute("data-action");
    try {
      if (action) {
        // Preferimos JSON; se o backend aceitar somente form-encoded, troque abaixo
        const resp = await fetch(action, {
          method: form.getAttribute("method") || "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ email })
        });

        if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
      } else {
        // fallback: simulação
        await new Promise((r) => setTimeout(r, 800));
      }

      showMessage("Inscrição realizada com sucesso! 🎉", "success");
      localStorage.setItem("newsletter_subscribed", "1");
      form.reset();
    } catch (err) {
      showMessage("Não foi possível enviar agora. Tente novamente.", "error");
      // console.error(err);
    } finally {
      sending = false;
      form.classList.remove("is-submitting");
    }
  });

  function isValidEmail(email) {
    // Regex simples e robusta para a maioria dos casos
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email);
  }

  function showMessage(message, type) {
    statusEl.textContent = message;
    statusEl.setAttribute("aria-live", type === "error" ? "assertive" : "polite");
    statusEl.setAttribute("aria-atomic", "true");
    statusEl.classList.remove("is-error", "is-success", "is-info");
    if (type === "error") statusEl.classList.add("is-error");
    else if (type === "success") statusEl.classList.add("is-success");
    else statusEl.classList.add("is-info");
  }
}

/* ==============================
   🌐 Idiomas (chips)
   ============================== */
function initLangChips() {
  const chips = document.querySelectorAll(
    ".lang-selector button.chip[data-lang], .lang-selector a.chip[data-lang], " +
    ".lang-selector button.btn-outline[data-lang], .lang-selector a.btn-outline[data-lang]"
  );
  if (!chips.length) return;

  const KEY = "site_lang";
  const saved = localStorage.getItem(KEY);

  chips.forEach((chip) => {
    const val = chip.dataset.lang;
    if (saved && saved === val) chip.classList.add("active");

    chip.addEventListener("click", (e) => {
      if (chip.tagName === "A") e.preventDefault();
      chips.forEach((c) => c.classList.remove("active"));
      chip.classList.add("active");
      localStorage.setItem(KEY, val);
      // ponto de integração opcional:
      // fetch(`/i18n?lang=${val}`, { method: "POST" })
    });
  });
}

/* ==============================
   ⬆️ Voltar ao topo
   ============================== */
function initBackToTop() {
  const btn = document.querySelector(".btn-to-top");
  if (!btn) return;

  const toggle = () => {
    if (window.scrollY > 200) btn.style.display = "inline-flex";
    else btn.style.display = "none";
  };

  toggle(); // estado inicial
  window.addEventListener("scroll", throttle(toggle, 100), { passive: true });

  // redundância: se JS desabilitar smooth no HTML, garantimos aqui
  btn.addEventListener("click", (e) => {
    e.preventDefault();
    window.scrollTo({ top: 0, behavior: "smooth" });
  });
}

/* utilitário simples */
function throttle(fn, wait) {
  let t = 0;
  return (...args) => {
    const now = Date.now();
    if (now - t >= wait) {
      t = now;
      fn.apply(null, args);
    }
  };
}
