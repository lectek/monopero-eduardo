if (!window.__RMF_HEADER_BOOTSTRAPPED__) {
  window.__RMF_HEADER_BOOTSTRAPPED__ = true;

  document.addEventListener("DOMContentLoaded", () => {
    const root = document.documentElement;
    const body = document.body;

    const nav =
      document.getElementById("primary-nav") ||
      document.getElementById("primary-navigation") ||
      document.querySelector(".primary-nav");

    const explicitToggleBtn = document.querySelector(
      ".nav-toggle, .menu-button[data-role='nav-toggle'], [data-header-nav-toggle]"
    );
    const sidebarLikeToggleBtn = document.querySelector(".sidebar-toggle, [data-sidebar-toggle]");
    const hasSidebar = Boolean(document.querySelector(".sidebar"));
    const toggleBtn = explicitToggleBtn || (!hasSidebar ? sidebarLikeToggleBtn : null);

    const header = document.querySelector("header.site-header, .navbar, header");
    if (header) {
      const syncHeaderHeight = () => {
        const height = Math.round(header.getBoundingClientRect().height);
        if (Number.isFinite(height) && height > 0) {
          const nextHeight = `${height}px`;
          if (root.style.getPropertyValue("--site-header-height") !== nextHeight) {
            root.style.setProperty("--site-header-height", nextHeight);
          }
        }
      };

      const onScroll = () => header.classList.toggle("scrolled", window.scrollY > 20);
      window.addEventListener("scroll", onScroll, { passive: true });
      window.addEventListener("resize", syncHeaderHeight, { passive: true });
      if (typeof ResizeObserver === "function") {
        new ResizeObserver(syncHeaderHeight).observe(header);
      }
      onScroll();
      syncHeaderHeight();
    }

    const path = (window.location.pathname || "/").replace(/\/+$/, "") || "/";
    document.querySelectorAll(".primary-nav a[href]").forEach((link) => {
      try {
        const href =
          new URL(link.getAttribute("href"), window.location.origin).pathname.replace(/\/+$/, "") || "/";
        if (href && (path === href || (href !== "/" && path.startsWith(href)))) {
          link.classList.add("ativo");
        }
      } catch {
        // ignore malformed href
      }
    });

    if (!toggleBtn || !nav) return;

    const controlledBySidebar = hasSidebar && toggleBtn.matches(".sidebar-toggle, [data-sidebar-toggle]");
    if (controlledBySidebar) return;

    const NAV_OPEN_CLASS = "header-nav-open";
    let lastFocused = null;

    const navId = nav.id || "primary-nav";
    if (!nav.id) nav.id = navId;
    toggleBtn.setAttribute("aria-controls", navId);
    toggleBtn.setAttribute("aria-expanded", "false");
    nav.setAttribute("role", "navigation");
    if (!nav.getAttribute("aria-label")) nav.setAttribute("aria-label", "Menu principal");

    let backdrop = document.querySelector("button.nav-backdrop");
    if (!backdrop) {
      backdrop = document.createElement("button");
      backdrop.type = "button";
      backdrop.className = "nav-backdrop";
      backdrop.setAttribute("aria-label", "Fechar menu");
      document.body.appendChild(backdrop);
    }

    const isOpen = () => root.classList.contains(NAV_OPEN_CLASS) || nav.classList.contains("open");
    const setExpanded = (v) => toggleBtn.setAttribute("aria-expanded", String(v));
    const focusables = () =>
      nav.querySelectorAll(
        'a[href],button:not([disabled]),input,select,textarea,[tabindex]:not([tabindex="-1"])'
      );

    function openNav() {
      if (isOpen()) return;
      lastFocused = document.activeElement;
      root.classList.add(NAV_OPEN_CLASS);
      nav.classList.add("open");
      body.classList.add("no-scroll");
      setExpanded(true);
      toggleBtn.classList.add("is-open");
      const first = focusables()[0];
      if (first) first.focus();
    }

    function closeNav() {
      if (!isOpen()) return;
      root.classList.remove(NAV_OPEN_CLASS);
      nav.classList.remove("open");
      if (!root.classList.contains("nav-open")) body.classList.remove("no-scroll");
      setExpanded(false);
      toggleBtn.classList.remove("is-open");
      if (lastFocused) {
        try {
          lastFocused.focus();
        } catch {
          // ignore focus errors
        }
      }
    }

    function toggleNav() {
      if (isOpen()) closeNav();
      else openNav();
    }

    toggleBtn.addEventListener("click", (event) => {
      event.preventDefault();
      toggleNav();
    });
    backdrop.addEventListener("click", closeNav);

    nav.querySelectorAll("a[href]").forEach((a) => {
      a.addEventListener("click", closeNav);
    });

    document.addEventListener("keydown", (e) => {
      if (!isOpen()) return;

      if (e.key === "Escape") {
        e.preventDefault();
        closeNav();
        return;
      }

      if (e.key === "Tab") {
        const f = Array.from(focusables());
        if (!f.length) return;
        const first = f[0];
        const last = f[f.length - 1];

        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first.focus();
        }
      }
    });

    window.addEventListener("resize", () => {
      if (window.innerWidth >= 1024 && isOpen()) closeNav();
    });
  });
}

