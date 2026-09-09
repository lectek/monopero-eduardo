(() => {
  if (window.__rmfThemeToggleInit) {
    return;
  }
  window.__rmfThemeToggleInit = true;

  const STORAGE_KEY = "rmf-theme";
  const root = document.documentElement;
  const mediaQuery = window.matchMedia
    ? window.matchMedia("(prefers-color-scheme: dark)")
    : null;

  function readStoredTheme() {
    try {
      const value = window.localStorage.getItem(STORAGE_KEY);
      return value === "dark" || value === "light" ? value : null;
    } catch {
      return null;
    }
  }

  function writeStoredTheme(theme) {
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // no-op
    }
  }

  function systemTheme() {
    return mediaQuery && mediaQuery.matches ? "dark" : "light";
  }

  function toggleButtons() {
    return Array.from(document.querySelectorAll("[data-theme-toggle]"));
  }

  function syncToggleButtons(theme) {
    const isDark = theme === "dark";
    const nextModeLabel = isDark ? "modo claro" : "modo escuro";
    const icon = isDark ? "\u2600" : "\u263E";

    toggleButtons().forEach((button) => {
      button.setAttribute("aria-pressed", String(isDark));
      button.setAttribute("aria-label", `Ativar ${nextModeLabel}`);
      button.setAttribute("title", `Alternar para ${nextModeLabel}`);
      button.dataset.themeCurrent = theme;

      const iconNode = button.querySelector(".theme-toggle__icon");
      if (iconNode) {
        iconNode.textContent = icon;
      }
    });
  }

  function applyTheme(theme) {
    root.setAttribute("data-theme", theme);
    root.style.colorScheme = theme;
    syncToggleButtons(theme);
  }

  function currentTheme() {
    const explicit = root.getAttribute("data-theme");
    return explicit === "dark" || explicit === "light" ? explicit : systemTheme();
  }

  function initThemeFromPreference() {
    const stored = readStoredTheme();
    applyTheme(stored || systemTheme());
  }

  function bindToggleButtons() {
    toggleButtons().forEach((button) => {
      if (button.dataset.themeBound === "true") {
        return;
      }
      button.dataset.themeBound = "true";
      button.addEventListener("click", (event) => {
        event.preventDefault();
        const next = currentTheme() === "dark" ? "light" : "dark";
        writeStoredTheme(next);
        applyTheme(next);
      });
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    initThemeFromPreference();
    bindToggleButtons();
  });

  if (mediaQuery) {
    const onSchemeChange = (event) => {
      if (readStoredTheme()) {
        return;
      }
      applyTheme(event.matches ? "dark" : "light");
    };
    if (typeof mediaQuery.addEventListener === "function") {
      mediaQuery.addEventListener("change", onSchemeChange);
    } else if (typeof mediaQuery.addListener === "function") {
      mediaQuery.addListener(onSchemeChange);
    }
  }
})();

