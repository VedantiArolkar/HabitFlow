/* ============================================================
   HabitFlow - shared front-end core
   Thin fetch wrapper (CSRF aware), toasts, confirm modal, helpers.
   ============================================================ */
const HF = (function () {
  const state = { user: null, categories: [], charts: {} };

  function cookie(name) {
    const match = document.cookie.match(new RegExp('(^|;\\s*)' + name + '=([^;]*)'));
    return match ? decodeURIComponent(match[2]) : null;
  }

  async function ensureCsrf() {
    if (!cookie('XSRF-TOKEN')) {
      try { await fetch('/api/auth/csrf', { credentials: 'same-origin' }); } catch (e) { /* ignore */ }
    }
  }

  async function api(method, url, body) {
    await ensureCsrf();
    const headers = { 'Accept': 'application/json' };
    const token = cookie('XSRF-TOKEN');
    if (token) headers['X-XSRF-TOKEN'] = token;
    if (body !== undefined && body !== null) headers['Content-Type'] = 'application/json';

    let response;
    try {
      response = await fetch(url, {
        method: method,
        headers: headers,
        credentials: 'same-origin',
        body: body === undefined || body === null ? undefined : JSON.stringify(body)
      });
    } catch (networkError) {
      throw new Error('Cannot reach the server. Is the application still running?');
    }

    if (response.status === 401 && !url.startsWith('/api/auth')) {
      window.location.href = '/login';
      throw new Error('Your session expired. Please sign in again.');
    }

    const text = await response.text();
    let data = null;
    if (text) {
      try { data = JSON.parse(text); } catch (e) { data = text; }
    }
    if (!response.ok) {
      const message = (data && typeof data === 'object' && data.message)
        ? data.message
        : 'Request failed (' + response.status + ').';
      const error = new Error(message);
      error.status = response.status;
      error.data = data;
      throw error;
    }
    return data;
  }

  const get = (url) => api('GET', url);
  const post = (url, body) => api('POST', url, body);
  const put = (url, body) => api('PUT', url, body);
  const patch = (url, body) => api('PATCH', url, body);
  const del = (url) => api('DELETE', url);

  /* ---------- toasts ---------- */
  function toast(message, type) {
    let wrap = document.querySelector('.hf-toast-wrap');
    if (!wrap) {
      wrap = document.createElement('div');
      wrap.className = 'hf-toast-wrap';
      document.body.appendChild(wrap);
    }
    const icons = { success: 'bi-check-circle-fill', error: 'bi-exclamation-octagon-fill', info: 'bi-info-circle-fill' };
    const kind = type || 'info';
    const el = document.createElement('div');
    el.className = 'hf-toast ' + kind;
    el.innerHTML = '<i class="bi ' + (icons[kind] || icons.info) + '"></i><span></span>';
    el.querySelector('span').textContent = message;
    wrap.appendChild(el);
    setTimeout(function () {
      el.style.transition = 'opacity .3s ease, transform .3s ease';
      el.style.opacity = '0';
      el.style.transform = 'translateX(24px)';
      setTimeout(function () { el.remove(); }, 320);
    }, kind === 'error' ? 5200 : 3200);
  }

  /* ---------- confirm dialog (promise based) ---------- */
  function confirmDialog(options) {
    const opts = Object.assign({ title: 'Are you sure?', message: '', confirmLabel: 'Confirm', danger: true }, options || {});
    return new Promise(function (resolve) {
      const id = 'hfConfirmModal';
      const existing = document.getElementById(id);
      if (existing) existing.remove();
      const wrapper = document.createElement('div');
      wrapper.innerHTML =
        '<div class="modal fade" id="' + id + '" tabindex="-1">' +
        '  <div class="modal-dialog modal-dialog-centered modal-sm">' +
        '    <div class="modal-content">' +
        '      <div class="modal-header"><h5 class="modal-title"></h5>' +
        '        <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>' +
        '      <div class="modal-body"><p class="mb-0" style="font-size:.88rem;color:var(--hf-text-soft)"></p></div>' +
        '      <div class="modal-footer">' +
        '        <button type="button" class="btn btn-hf-ghost btn-sm" data-bs-dismiss="modal">Cancel</button>' +
        '        <button type="button" class="btn btn-sm ' + (opts.danger ? 'btn-danger' : 'btn-hf') + '" data-confirm>confirm</button>' +
        '      </div>' +
        '    </div>' +
        '  </div>' +
        '</div>';
      document.body.appendChild(wrapper);
      const modalEl = document.getElementById(id);
      modalEl.querySelector('.modal-title').textContent = opts.title;
      modalEl.querySelector('.modal-body p').textContent = opts.message;
      modalEl.querySelector('[data-confirm]').textContent = opts.confirmLabel;
      const modal = new bootstrap.Modal(modalEl);
      let decided = false;
      modalEl.querySelector('[data-confirm]').addEventListener('click', function () { decided = true; modal.hide(); });
      modalEl.addEventListener('hidden.bs.modal', function () { modalEl.remove(); resolve(decided); });
      modal.show();
    });
  }

  /* ---------- small helpers ---------- */
  function esc(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/[&<>"']/g, function (c) {
      return ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c];
    });
  }

  function initials(name) {
    if (!name) return 'HF';
    return name.trim().split(/\s+/).slice(0, 2).map(function (p) { return p[0].toUpperCase(); }).join('');
  }

  function parseDate(iso) {
    if (!iso) return new Date();
    const parts = iso.split('-');
    return new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
  }


  function isoDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
  }

  function todayIso() { return isoDate(new Date()); }

  function addDays(date, days) {
    const copy = new Date(date.getTime());
    copy.setDate(copy.getDate() + days);
    return copy;
  }

  const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  const DOW = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  function fmtDate(iso, style) {
    const d = parseDate(iso);
    if (style === 'long') return DOW[d.getDay()] + ', ' + d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear();
    if (style === 'short') return d.getDate() + ' ' + MONTHS[d.getMonth()].slice(0, 3);
    return isoDate(d);
  }

  function relative(isoDateTime) {
    if (!isoDateTime) return '';
    const then = new Date(isoDateTime);
    const diff = Math.floor((Date.now() - then.getTime()) / 1000);
    if (diff < 60) return 'just now';
    if (diff < 3600) return Math.floor(diff / 60) + 'm ago';
    if (diff < 86400) return Math.floor(diff / 3600) + 'h ago';
    if (diff < 604800) return Math.floor(diff / 86400) + 'd ago';
    return then.getDate() + ' ' + MONTHS[then.getMonth()].slice(0, 3);
  }

  function number(value, digits) {
    const n = Number(value || 0);
    return digits ? n.toFixed(digits) : String(Math.round(n));
  }

  function iconTint(color) {
    return 'background:' + (color || '#7C6BFF') + '1F;color:' + (color || '#7C6BFF') + ';';
  }

  function emptyState(icon, title, text, actionHtml) {
    return '<div class="hf-empty">' +
      '<div class="hf-empty-icon"><i class="bi ' + icon + '"></i></div>' +
      '<h4>' + esc(title) + '</h4><p>' + esc(text) + '</p>' +
      (actionHtml || '') + '</div>';
  }

  function skeleton(rows) {
    let html = '';
    for (let i = 0; i < (rows || 3); i++) {
      html += '<div class="hf-skeleton mb-2" style="height:58px"></div>';
    }
    return html;
  }

  function progressBar(percent, tone) {
    const value = Math.max(0, Math.min(100, Number(percent || 0)));
    return '<div class="hf-progress"><div class="hf-progress-bar ' + (tone || '') +
      '" style="width:' + value + '%"></div></div>';
  }

  function ring(percent, color) {
    const value = Math.max(0, Math.min(100, Number(percent || 0)));
    const radius = 52;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (value / 100) * circumference;
    return '<div class="hf-ring">' +
      '<svg width="120" height="120" viewBox="0 0 120 120">' +
      '<circle cx="60" cy="60" r="' + radius + '" fill="none" stroke="#EEF0F8" stroke-width="10"></circle>' +
      '<circle cx="60" cy="60" r="' + radius + '" fill="none" stroke="' + (color || '#7C6BFF') +
      '" stroke-width="10" stroke-linecap="round" stroke-dasharray="' + circumference +
      '" stroke-dashoffset="' + offset + '" style="transition:stroke-dashoffset .8s cubic-bezier(.4,0,.2,1)"></circle>' +
      '</svg><div class="hf-ring-val">' + Math.round(value) + '%</div></div>';
  }

  function chart(id, config) {
    const canvas = document.getElementById(id);
    if (!canvas) return null;
    if (state.charts[id]) state.charts[id].destroy();
    state.charts[id] = new Chart(canvas.getContext('2d'), config);
    return state.charts[id];
  }

  function destroyCharts() {
    Object.keys(state.charts).forEach(function (k) { state.charts[k].destroy(); delete state.charts[k]; });
  }

  function chartDefaults() {
    Chart.defaults.font.family = "'Plus Jakarta Sans','Inter',system-ui,sans-serif";
    Chart.defaults.font.size = 11;
    Chart.defaults.color = '#8A90A6';
    Chart.defaults.plugins.legend.display = false;
    Chart.defaults.plugins.tooltip.backgroundColor = '#1A1D2E';
    Chart.defaults.plugins.tooltip.padding = 10;
    Chart.defaults.plugins.tooltip.cornerRadius = 8;
    Chart.defaults.plugins.tooltip.titleFont = { weight: '700', size: 12 };
  }

  function gradient(context, from, to) {
    const g = context.createLinearGradient(0, 0, 0, 240);
    g.addColorStop(0, from);
    g.addColorStop(1, to);
    return g;
  }

  /* ---------- shell wiring ---------- */
  function initShell() {
    const toggle = document.getElementById('sidebarToggle');
    const sidebar = document.querySelector('.hf-sidebar');
    const overlay = document.querySelector('.hf-overlay');
    if (toggle && sidebar) {
      toggle.addEventListener('click', function () {
        sidebar.classList.toggle('open');
        if (overlay) overlay.classList.toggle('show');
      });
    }
    if (overlay) {
      overlay.addEventListener('click', function () {
        sidebar.classList.remove('open');
        overlay.classList.remove('show');
      });
    }
    const todayEl = document.getElementById('topbarDate');
    if (todayEl) {
      const now = new Date();
      todayEl.textContent = DOW[now.getDay()] + ', ' + now.getDate() + ' ' + MONTHS[now.getMonth()] + ' ' + now.getFullYear();
    }
    const greeting = document.getElementById('greetingText');
    if (greeting) {
      const hour = new Date().getHours();
      const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
      greeting.textContent = part + ', ' + (greeting.dataset.name || 'there').split(' ')[0] + ' 👋';
    }
    loadUnreadCount();
    wireLogout();
    wireNotificationBell();
  }

  function loadUnreadCount() {
    get('/api/notifications/unread-count').then(function (res) {
      const count = res && res.count ? res.count : 0;
      document.querySelectorAll('[data-unread-badge]').forEach(function (el) {
        el.textContent = count > 9 ? '9+' : count;
        el.style.display = count > 0 ? '' : 'none';
      });
      const dot = document.querySelector('[data-bell-dot]');
      if (dot) dot.style.display = count > 0 ? '' : 'none';
    }).catch(function () { /* not signed in yet */ });
  }

  function wireLogout() {
    document.querySelectorAll('[data-logout]').forEach(function (btn) {
      btn.addEventListener('click', async function (event) {
        event.preventDefault();
        try {
          await post('/api/auth/logout');
        } catch (e) { /* fall through to redirect */ }
        window.location.href = '/login';
      });
    });
  }

  function wireNotificationBell() {
    const list = document.getElementById('bellList');
    if (!list) return;
    const dropdown = document.getElementById('bellDropdown');
    if (dropdown && dropdown.parentElement) {
      dropdown.parentElement.addEventListener('show.bs.dropdown', function () {
        list.innerHTML = '<div class="p-3">' + skeleton(2) + '</div>';
        get('/api/notifications?limit=6').then(function (items) {
          if (!items || !items.length) {
            list.innerHTML = '<div class="px-3 py-4 text-center" style="font-size:.82rem;color:var(--hf-text-muted)">No notifications yet.</div>';
            return;
          }
          list.innerHTML = items.map(function (n) {
            return '<a class="dropdown-item d-flex gap-2 align-items-start' + (n.read ? '' : ' fw-bold') +
              '" href="' + esc(n.link || '/notifications') + '" style="white-space:normal">' +
              '<i class="bi ' + notificationIcon(n.type) + ' mt-1" style="color:var(--hf-purple)"></i>' +
              '<span><span class="d-block" style="font-size:.82rem">' + esc(n.title) + '</span>' +
              '<span class="d-block" style="font-size:.72rem;color:var(--hf-text-muted);font-weight:600">' +
              esc(relative(n.createdAt)) + '</span></span></a>';
          }).join('');
        }).catch(function () {
          list.innerHTML = '<div class="p-3 text-danger" style="font-size:.8rem">Could not load notifications.</div>';
        });
      });
    }
  }

  function notificationIcon(type) {
    return ({
      COMPLETION: 'bi-check-circle',
      REMINDER: 'bi-alarm',
      MILESTONE: 'bi-trophy',
      WEEKLY_SUMMARY: 'bi-calendar-week',
      ACHIEVEMENT: 'bi-star'
    })[type] || 'bi-bell';
  }

  async function loadCategories(force) {
    if (state.categories.length && !force) return state.categories;
    state.categories = await get('/api/categories');
    return state.categories;
  }

  async function loadMe() {
    if (state.user) return state.user;
    state.user = await get('/api/auth/me');
    return state.user;
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (typeof Chart !== 'undefined') chartDefaults();
    // Authentication pages have no app shell; don't make protected notification calls there.
    const isAuthPage = document.getElementById('loginForm') || document.getElementById('registerForm');
    if (!isAuthPage) initShell();
  });

  return {
    api: api, get: get, post: post, put: put, patch: patch, del: del,
    toast: toast, confirm: confirmDialog, esc: esc, initials: initials,
    parseDate: parseDate, isoDate: isoDate, todayIso: todayIso, addDays: addDays,
    fmtDate: fmtDate, relative: relative, number: number, MONTHS: MONTHS, DOW: DOW,
    iconTint: iconTint, emptyState: emptyState, skeleton: skeleton,
    progressBar: progressBar, ring: ring, chart: chart, destroyCharts: destroyCharts,
    gradient: gradient, loadCategories: loadCategories, loadMe: loadMe,
    loadUnreadCount: loadUnreadCount, notificationIcon: notificationIcon,
    state: state
  };
})();
