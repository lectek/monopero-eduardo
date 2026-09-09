/* ===========================================
   Sidebar - ParaisoPet / HotelPet (LekTeC)
   - Off-canvas acessível no mobile
   - Trap de foco, ESC, clique fora, scroll-lock
   - Compacto no desktop (persistente)
   - Auto-mount do botão no header (.sidebar-toggle)
   - Toggle com animação hamburger → chevron (via .is-open)
   =========================================== */

const initSidebarApp = () => {
  if (window.__RMF_SIDEBAR_BOOTSTRAPPED__) return true;
  try {
    const initialized = initSidebar({ autoMountToggle: true });
    window.__RMF_SIDEBAR_BOOTSTRAPPED__ = initialized;
    return initialized;
  } catch (error) {
    window.__RMF_SIDEBAR_BOOTSTRAPPED__ = false;
    console.error("Falha ao inicializar sidebar.", error);
    return false;
  }
};

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", () => {
    if (!initSidebarApp()) {
      window.addEventListener("load", initSidebarApp, { once: true });
    }
  });
} else {
  if (!initSidebarApp()) {
    window.addEventListener("load", initSidebarApp, { once: true });
  }
}

function initSidebar(opts = {}) {
  const { autoMountToggle = false } = opts;

  const sidebar = document.querySelector(".sidebar");
  if (!sidebar) return false;
  const headerEl = document.querySelector(".site-header");

  const syncHeaderHeight = () => {
    if (!headerEl) return;
    const height = Math.round(headerEl.getBoundingClientRect().height);
    if (Number.isFinite(height) && height > 0) {
      document.documentElement.style.setProperty("--site-header-height", `${height}px`);
    }
  };
  syncHeaderHeight();
  if (headerEl) {
    window.addEventListener("resize", syncHeaderHeight, { passive: true });
    if (typeof ResizeObserver === "function") {
      new ResizeObserver(syncHeaderHeight).observe(headerEl);
    }
  }

  // Auto-injetar botão no header (mobile), se não existir
  if (autoMountToggle) autoMountSidebarToggle();

  // Seletores de botões
  const toggleBtns = Array.from(document.querySelectorAll(
    "[data-sidebar-toggle], .sidebar-toggle, .js-sidebar-toggle"
  ));
  if (!toggleBtns.length) return false;
  const toggleBtnSet = new Set(toggleBtns);
  const compactBtns = Array.from(document.querySelectorAll(
    "[data-sidebar-compact-toggle], .sidebar-compact-toggle"
  )).filter((btn) => !toggleBtnSet.has(btn));

  // ARIA base
  const SIDEBAR_ID = sidebar.id || "app-sidebar";
  sidebar.id = SIDEBAR_ID;
  const mqDesktop = typeof window.matchMedia === "function"
    ? window.matchMedia("(min-width: 1024px)")
    : { matches: window.innerWidth >= 1024 };
  sidebar.setAttribute("aria-hidden", mqDesktop.matches ? "false" : "true");
  sidebar.setAttribute("tabindex", "-1");
  toggleBtns.forEach((btn) => {
    btn.setAttribute("aria-controls", SIDEBAR_ID);
    btn.setAttribute("aria-expanded", "false");
    btn.setAttribute("aria-label", btn.getAttribute("aria-label") || "Abrir menu");
  });

  // Overlay
  let overlay = null;
  function ensureOverlay() {
    if (overlay) return overlay;
    overlay = document.createElement("button");
    overlay.type = "button";
    overlay.className = "sidebar-overlay";
    overlay.setAttribute("aria-label", "Fechar menu lateral");
    document.body.appendChild(overlay);
    overlay.addEventListener("click", close);
    return overlay;
  }

  // Trap de foco
  const focusableSel =
    'a[href],button:not([disabled]),input,select,textarea,[tabindex]:not([tabindex="-1"])';
  let lastFocused = null;
  let lockedScrollY = 0;
  let scrollLocked = false;
  let bodyLockStyleSnapshot = null;

  function lockBodyScroll() {
    if (scrollLocked) return;
    lockedScrollY = window.scrollY || window.pageYOffset || 0;
    bodyLockStyleSnapshot = {
      position: document.body.style.position,
      top: document.body.style.top,
      left: document.body.style.left,
      right: document.body.style.right,
      width: document.body.style.width,
      overflow: document.body.style.overflow
    };
    document.documentElement.classList.add("sidebar-scroll-locked");
    document.body.classList.add("no-scroll");
    document.body.style.position = "fixed";
    document.body.style.top = `-${lockedScrollY}px`;
    document.body.style.left = "0";
    document.body.style.right = "0";
    document.body.style.width = "100%";
    document.body.style.overflow = "hidden";
    scrollLocked = true;
  }

  function unlockBodyScroll() {
    if (!scrollLocked) return;
    document.documentElement.classList.remove("sidebar-scroll-locked");
    document.body.classList.remove("no-scroll");
    if (bodyLockStyleSnapshot) {
      document.body.style.position = bodyLockStyleSnapshot.position;
      document.body.style.top = bodyLockStyleSnapshot.top;
      document.body.style.left = bodyLockStyleSnapshot.left;
      document.body.style.right = bodyLockStyleSnapshot.right;
      document.body.style.width = bodyLockStyleSnapshot.width;
      document.body.style.overflow = bodyLockStyleSnapshot.overflow;
    } else {
      document.body.style.position = "";
      document.body.style.top = "";
      document.body.style.left = "";
      document.body.style.right = "";
      document.body.style.width = "";
      document.body.style.overflow = "";
    }
    window.scrollTo(0, lockedScrollY);
    scrollLocked = false;
  }

  function trapKeydown(e) {
    if (!isOpen()) return;
    if (e.key === "Escape") {
      e.preventDefault();
      close();
      return;
    }
    if (e.key !== "Tab") return;
    const items = Array.from(sidebar.querySelectorAll(focusableSel)).filter(
      (el) => el.offsetParent !== null || getComputedStyle(el).position === "fixed"
    );
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault(); last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault(); first.focus();
    }
  }

  // Em mobile, manter a sidebar em modo drawer fixo para bloquear rolagem do conteúdo.
  const shouldReflowMobileSidebar = false;

  function isOpen() { return sidebar.classList.contains("is-open"); }

function open() {
  if (isOpen()) return;
  lastFocused = document.activeElement;
  sidebar.classList.add("is-open");
  sidebar.setAttribute("aria-hidden", "false");
  document.documentElement.classList.add("sidebar-open");
  const isMobile = !mqDesktop.matches;
  const useReflowMode = isMobile && shouldReflowMobileSidebar;
  toggleBtns.forEach((b) => {
      b.classList.add("is-open");                 // <-- ativa animação chevron
      b.setAttribute("aria-expanded", "true");
      b.setAttribute("aria-label", "Fechar menu");
    });
    document.documentElement.classList.toggle("sidebar-reflow-open", useReflowMode);
    if (isMobile && !useReflowMode) {
      lockBodyScroll();
      ensureOverlay().classList.add("is-open");
    } else if (overlay) {
      unlockBodyScroll();
      overlay.classList.remove("is-open");
    }
    const first = sidebar.querySelector(focusableSel);
    (first || sidebar).focus();
    document.addEventListener("keydown", trapKeydown);
  }

function close() {
  if (!isOpen()) return;
  sidebar.classList.remove("is-open");
  sidebar.setAttribute("aria-hidden", "true");
    document.documentElement.classList.remove("sidebar-open");
    document.documentElement.classList.remove("sidebar-reflow-open");
    toggleBtns.forEach((b) => {
      b.classList.remove("is-open");              // <-- volta ao hamburger
      b.setAttribute("aria-expanded", "false");
      b.setAttribute("aria-label", "Abrir menu");
    });
    unlockBodyScroll();
    if (overlay) overlay.classList.remove("is-open");
    document.removeEventListener("keydown", trapKeydown);
    if (lastFocused) try { lastFocused.focus(); } catch {}
  }

  function toggle() { isOpen() ? close() : open(); }

  // Eventos
  toggleBtns.forEach((btn) => btn.addEventListener("click", toggle));
  document.addEventListener("click", (e) => {
    const a = e.target.closest("a[href]");
    if (a && sidebar.contains(a) && !mqDesktop.matches) close();
  });
  const onViewportChange = () => {
    syncCompactState();
    close();
  };
  if (typeof mqDesktop.addEventListener === "function") {
    mqDesktop.addEventListener("change", onViewportChange);
  } else if (typeof mqDesktop.addListener === "function") {
    mqDesktop.addListener(onViewportChange);
  }

  // Fallback de link ativo
  highlightActiveLink(sidebar);

  // Modo compacto persistente (desktop)
  const COMPACT_KEY = "sidebar_collapsed_v1";
  const layout = sidebar.closest(".layout--with-sidebar");
  const setCollapsed = (collapsed) => {
    sidebar.classList.toggle("sidebar--collapsed", collapsed);
    if (layout) layout.classList.toggle("layout--sidebar-collapsed", collapsed);
    compactBtns.forEach((btn) => {
      btn.setAttribute("aria-label", collapsed ? "Expandir menu lateral" : "Recolher menu lateral");
      const label = btn.querySelector("span");
      if (label) label.textContent = collapsed ? "Abrir" : "Recolher";
    });
  };

  const getSavedCollapsed = () => {
    try {
      return localStorage.getItem(COMPACT_KEY) === "1";
    } catch {
      return false;
    }
  };
  const saveCollapsed = (collapsed) => {
    try {
      localStorage.setItem(COMPACT_KEY, collapsed ? "1" : "0");
    } catch {}
  };
  const syncCompactState = () => {
    if (!mqDesktop.matches) {
      setCollapsed(false);
      return;
    }
    setCollapsed(getSavedCollapsed());
  };

  syncCompactState();
  function toggleCompact() {
    if (!mqDesktop.matches) return;
    const collapsed = !sidebar.classList.contains("sidebar--collapsed");
    setCollapsed(collapsed);
    saveCollapsed(collapsed);
  }
  compactBtns.forEach((btn) => btn.addEventListener("click", toggleCompact));

  ensureOverlay(); // cria cedo
  return true;
}

/* ---> Auto-montagem do botão no header (se não existir) */
function autoMountSidebarToggle() {
  if (document.querySelector("[data-sidebar-toggle], .sidebar-toggle, .js-sidebar-toggle")) return;

  const host =
    document.querySelector(".header-inner") ||
    document.querySelector(".site-header .container") ||
    document.querySelector(".navbar .container") ||
    document.querySelector(".site-header, header, .navbar");

  if (!host) return;

  const btn = document.createElement("button");
  btn.className = "sidebar-toggle";
  btn.type = "button";
  btn.setAttribute("data-sidebar-toggle", "");
  btn.setAttribute("aria-label", "Abrir menu");
  btn.setAttribute("aria-expanded", "false");
  btn.innerHTML = `<span class="menu-icon" aria-hidden="true"></span>`;
  host.firstChild ? host.insertBefore(btn, host.firstChild) : host.appendChild(btn);
}

/* ---------- utilidades ---------- */
function highlightActiveLink(sidebar) {
  try {
    const path = (location.pathname || "").replace(/\/+$/, "") || "/";
    const links = sidebar.querySelectorAll(".sidebar__link[href]");
    links.forEach((a) => {
      const href = a.getAttribute("href") || a.getAttribute("th:href") || "";
      if (!href) return;
      const clean = href.replace(/^https?:\/\/[^/]+/i, "").replace(/\/+$/, "") || "/";
      if (clean === path || (clean !== "/" && path.startsWith(clean))) {
        a.classList.add("is-active");
        a.setAttribute("aria-current", "page");
      } else {
        a.classList.remove("is-active");
        a.removeAttribute("aria-current");
      }
    });
  } catch {}
}
