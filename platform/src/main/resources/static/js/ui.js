// ui.js — helpers de UI e CSRF (compartilhado)

export function getCsrf() {
  const token = document.querySelector('meta[name="_csrf"]')?.content;
  const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
  return { token, header };
}

export function withCsrf(init = {}) {
  const { token, header } = getCsrf();
  init.headers = Object.assign({}, init.headers || {}, token ? { [header]: token } : {});
  return init;
}

export function toast(msg, type = 'ok') {
  let box = document.getElementById('toast');
  if (!box) {
    box = document.createElement('div');
    box.id = 'toast';
    document.body.appendChild(box);
  }
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = msg;
  box.appendChild(el);
  setTimeout(() => el.remove(), 3500);
}
