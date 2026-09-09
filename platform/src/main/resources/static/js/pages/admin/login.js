document.addEventListener("DOMContentLoaded", () => {
  const form = document.getElementById("form-login-admin");
  const erroEl = document.getElementById("login-erro");

  function redirecionarPosLogin() {
    window.location.href = AdminAuth.hasRole("MOTOBOY") ? "/motoboy" : "/admin/pedidos";
  }

  if (AdminAuth.getToken()) {
    redirecionarPosLogin();
    return;
  }

  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    erroEl.hidden = true;

    const email = document.getElementById("admin-email").value.trim();
    const senha = document.getElementById("admin-senha").value;

    try {
      const resp = await fetch("/api/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, senha }),
      });
      if (!resp.ok) {
        const erro = await resp.json().catch(() => ({}));
        erroEl.textContent = erro.mensagem || "Não foi possível entrar.";
        erroEl.hidden = false;
        return;
      }
      const dados = await resp.json();
      AdminAuth.setToken(dados.accessToken);
      redirecionarPosLogin();
    } catch (e) {
      erroEl.textContent = "Erro de conexão. Tente novamente.";
      erroEl.hidden = false;
    }
  });
});
