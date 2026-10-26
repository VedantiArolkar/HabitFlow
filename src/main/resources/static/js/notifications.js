/* Notifications - persisted in MySQL, read/delete through /api/notifications */
document.addEventListener('DOMContentLoaded', function () {
  let unreadOnly = false;
  let items = [];

  document.getElementById('markAllRead').addEventListener('click', function () {
    HF.post('/api/notifications/read-all', {}).then(function () {
      HF.toast('All notifications marked as read.', 'success');
      load();
      HF.loadUnreadCount();
    }).catch(function (error) { HF.toast(error.message, 'error'); });
  });

  document.querySelectorAll('[data-notif-filter]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      unreadOnly = btn.dataset.notifFilter === 'unread';
      document.querySelectorAll('[data-notif-filter]').forEach(function (b) {
        b.classList.remove('btn-hf-soft'); b.classList.add('btn-hf-ghost');
      });
      btn.classList.add('btn-hf-soft'); btn.classList.remove('btn-hf-ghost');
      load();
    });
  });

  load();

  function load() {
    document.getElementById('notifList').innerHTML =
      '<div class="hf-skeleton mb-2" style="height:64px"></div><div class="hf-skeleton" style="height:64px"></div>';
    HF.get('/api/notifications?limit=50' + (unreadOnly ? '&unreadOnly=true' : ''))
      .then(function (data) { items = data || []; render(); })
      .catch(function (error) {
        HF.toast(error.message, 'error');
        document.getElementById('notifList').innerHTML =
          HF.emptyState('bi-exclamation-triangle', 'Could not load notifications', error.message, '');
      });
  }

  function render() {
    const unread = items.filter(function (n) { return !n.read; }).length;
    document.getElementById('notifCount').textContent =
      unread ? unread + ' unread' : items.length + ' total';
    const box = document.getElementById('notifList');
    if (!items.length) {
      box.innerHTML = HF.emptyState('bi-bell-slash',
        unreadOnly ? 'Nothing unread' : 'No notifications yet',
        'Completion confirmations, goal milestone messages and weekly summaries appear here.',
        '');
      return;
    }
    box.innerHTML = items.map(function (n) {
      const meta = NOTIF_META[n.type] || NOTIF_META.INFO;
      return '<div class="hf-habit' + (n.read ? '' : ' unread') + '" data-id="' + n.id + '" style="align-items:flex-start">' +
        '<div class="hf-habit-icon" style="background:' + meta.bg + ';color:var(--hf-purple-dark)">' +
        '<i class="bi ' + meta.icon + '"></i></div>' +
        '<div class="hf-habit-main"><p class="hf-habit-name">' + HF.esc(n.title) +
          (!n.read ? ' <span class="hf-chip purple" style="font-size:.62rem">New</span>' : '') + '</p>' +
        '<div style="font-size:.8rem;color:var(--hf-text-soft);line-height:1.55">' + HF.esc(n.message) + '</div>' +
        '<div class="hf-habit-meta mt-1"><span><i class="bi bi-clock me-1"></i>' + HF.relative(n.createdAt) + '</span>' +
        (n.link ? '<a href="' + HF.esc(n.link) + '" style="font-weight:650">Open</a>' : '') + '</div></div>' +
        '<div class="d-flex gap-1">' +
          (!n.read ? '<button class="btn btn-hf-ghost btn-sm" data-read="' + n.id + '" title="Mark as read">' +
            '<i class="bi bi-check2"></i></button>' : '') +
          '<button class="btn btn-hf-ghost btn-sm" data-del="' + n.id + '" title="Delete"><i class="bi bi-trash"></i></button>' +
        '</div></div>';
    }).join('');

    box.querySelectorAll('[data-read]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        HF.patch('/api/notifications/' + btn.dataset.read + '/read', {})
          .then(function () { load(); HF.loadUnreadCount(); })
          .catch(function (error) { HF.toast(error.message, 'error'); });
      });
    });
    box.querySelectorAll('[data-del]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        HF.del('/api/notifications/' + btn.dataset.del)
          .then(function () { load(); HF.loadUnreadCount(); })
          .catch(function (error) { HF.toast(error.message, 'error'); });
      });
    });
  }

  const NOTIF_META = {
    COMPLETION: { icon: 'bi-check2-circle', bg: '#ECFDF5' },
    MILESTONE: { icon: 'bi-flag', bg: '#FEF6E7' },
    REMINDER: { icon: 'bi-alarm', bg: '#E8F0FE' },
    WEEKLY_SUMMARY: { icon: 'bi-calendar-week', bg: '#EFECFF' },
    ACHIEVEMENT: { icon: 'bi-trophy', bg: '#FEF6E7' },
    INFO: { icon: 'bi-info-circle', bg: '#F1F3FA' }
  };
});
