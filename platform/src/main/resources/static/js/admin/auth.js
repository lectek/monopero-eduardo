(function () {
  const TOKEN_KEY = "mm_admin_token";

  window.AdminAuth = {
    getToken() {
      return localStorage.getItem(TOKEN_KEY);
    },
    setToken(token) {
      localStorage.setItem(TOKEN_KEY, token);
    },
    clear() {
      localStorage.removeItem(TOKEN_KEY);
    },
    requireAuth() {
      if (!this.getToken()) {
        window.location.href = "/admin/login";
      }
    },
    /** Decodifica o claim "roles" do JWT (sem validar assinatura — só pra decidir navegação/UI). */
    getRoles() {
      const token = this.getToken();
      if (!token) return [];
      try {
        const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
        return payload.roles || [];
      } catch (e) {
        return [];
      }
    },
    hasRole(role) {
      return this.getRoles().includes(role);
    },
    async fetch(url, options) {
      options = options || {};
      const token = this.getToken();
      const headers = Object.assign({}, options.headers, token ? { Authorization: "Bearer " + token } : {});
      const resp = await fetch(url, Object.assign({}, options, { headers }));
      if (resp.status === 401) {
        this.clear();
        window.location.href = "/admin/login";
        throw new Error("Sessão expirada.");
      }
      return resp;
    },
  };

  document.addEventListener("DOMContentLoaded", () => {
    const logoutBtn = document.getElementById("admin-logout");
    if (logoutBtn) {
      logoutBtn.addEventListener("click", () => {
        AdminAuth.clear();
        window.location.href = "/admin/login";
      });
    }
  });
})();
