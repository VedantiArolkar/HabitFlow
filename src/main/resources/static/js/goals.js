/* Goals - CRUD against /api/goals, progress comes from linked habit completions */
document.addEventListener('DOMContentLoaded', function () {
  let goals = [];
  let activeHabits = [];
  const modal = new bootstrap.Modal(document.getElementById('goalModal'));

  document.getElementById('newGoalBtn').addEventListener('click', function () { openForm(null); });
  document.getElementById('goalSaveBtn').addEventListener('click', save);

  load();

  function load() {
    Promise.all([HF.get('/api/goals'), HF.get('/api/habits?status=ACTIVE')])
      .then(function (res) {
        goals = res[0] || [];
        activeHabits = res[1] || [];
        render();
      })
      .catch(function (error) {
        HF.toast(error.message, 'error');
        document.getElementById('goalList').innerHTML =
          '<div class="col-12"><div class="hf-card">' +
          HF.emptyState('bi-exclamation-triangle', 'Could not load goals', error.message, '') + '</div></div>';
      });
  }

  function render() {
    const active = goals.filter(function (g) { return g.status === 'ACTIVE'; });
    const done = goals.filter(function (g) { return g.status === 'COMPLETED'; });
    const avg = goals.length
      ? goals.reduce(function (sum, g) { return sum + g.percent; }, 0) / goals.length : 0;
    const next = active.slice().sort(function (a, b) { return b.percent - a.percent; })[0];

    document.getElementById('goalActive').textContent = active.length;
    document.getElementById('goalDone').textContent = done.length;
    document.getElementById('goalAvg').textContent = Math.round(avg) + '%';
    document.getElementById('goalNext').textContent = next ? Math.round(next.percent) + '%' : '—';

    const list = document.getElementById('goalList');
    if (!goals.length) {
      list.innerHTML = '<div class="col-12"><div class="hf-card">' +
        HF.emptyState('bi-bullseye', 'No goals yet',
          'A goal gives your habits a direction — for example “Practise Java 40 times this quarter”.',
          '<button class="btn btn-hf btn-sm" onclick="document.getElementById(\'newGoalBtn\').click()">Create a goal</button>') +
        '</div></div>';
      return;
    }

    list.innerHTML = goals.map(card).join('');
    list.querySelectorAll('[data-edit]').forEach(function (b) {
      b.addEventListener('click', function () {
        openForm(goals.find(function (g) { return g.id === Number(b.dataset.edit); }));
      });
    });
    list.querySelectorAll('[data-delete]').forEach(function (b) {
      b.addEventListener('click', function () { remove(Number(b.dataset.delete)); });
    });
    list.querySelectorAll('[data-complete]').forEach(function (b) {
      b.addEventListener('click', function () { setStatus(Number(b.dataset.complete), 'COMPLETED'); });
    });
    list.querySelectorAll('[data-reopen]').forEach(function (b) {
      b.addEventListener('click', function () { setStatus(Number(b.dataset.reopen), 'ACTIVE'); });
    });
  }

  function card(g) {
    const percent = Math.min(100, Math.round(g.percent));
    const tone = percent >= 100 ? 'green' : percent >= 50 ? 'teal' : 'purple';
    const bar = percent >= 100 ? 'teal' : '';
    const linked = (g.habitNames || []).length
      ? (g.habitNames || []).map(function (n) { return '<span class="hf-chip"><i class="bi bi-link-45deg"></i>' + HF.esc(n) + '</span>'; }).join('')
      : '<span class="hf-chip"><i class="bi bi-hand-index"></i>Manual progress</span>';
    const days = g.daysRemaining === null ? '' :
      g.daysRemaining > 0 ? g.daysRemaining + ' days left' :
      g.daysRemaining === 0 ? 'Due today' : Math.abs(g.daysRemaining) + ' days overdue';

    return '<div class="col-md-6 col-xl-4"><div class="hf-card hf-card-pad h-100">' +
      '<div class="d-flex justify-content-between align-items-start gap-2 mb-2">' +
        '<div><div style="font-weight:750;font-size:.98rem;line-height:1.3">' + HF.esc(g.title) + '</div>' +
        '<div style="font-size:.74rem;color:var(--hf-text-muted)">' +
          (g.targetDate ? 'Target ' + HF.fmtDate(g.targetDate, 'short') : 'No target date') + '</div></div>' +
        '<div class="dropdown">' +
          '<button class="btn btn-sm btn-hf-ghost px-2" data-bs-toggle="dropdown"><i class="bi bi-three-dots-vertical"></i></button>' +
          '<ul class="dropdown-menu dropdown-menu-end">' +
            '<li><button class="dropdown-item" data-edit="' + g.id + '"><i class="bi bi-pencil me-2"></i>Edit</button></li>' +
            (g.status === 'ACTIVE'
              ? '<li><button class="dropdown-item" data-complete="' + g.id + '"><i class="bi bi-check2-all me-2"></i>Mark complete</button></li>'
              : '<li><button class="dropdown-item" data-reopen="' + g.id + '"><i class="bi bi-arrow-counterclockwise me-2"></i>Reopen</button></li>') +
            '<li><hr class="dropdown-divider"></li>' +
            '<li><button class="dropdown-item text-danger" data-delete="' + g.id + '"><i class="bi bi-trash me-2"></i>Delete</button></li>' +
          '</ul></div>' +
      '</div>' +
      (g.description ? '<p style="font-size:.79rem;color:var(--hf-text-soft);margin-bottom:.7rem">' + HF.esc(g.description) + '</p>' : '') +
      '<div class="d-flex justify-content-between align-items-baseline mb-1">' +
        '<span style="font-size:.76rem;color:var(--hf-text-muted)">' + g.progress + ' / ' + g.targetValue + '</span>' +
        '<span style="font-weight:800;font-size:.9rem;color:var(--hf-purple-dark)">' + percent + '%</span></div>' +
      HF.progressBar(percent, bar) +
      '<div class="d-flex flex-wrap gap-1 mt-3 mb-2">' + linked +
        (g.status === 'COMPLETED' ? '<span class="hf-chip green"><i class="bi bi-trophy"></i>Completed</span>' : '') + '</div>' +
      '<div class="d-flex justify-content-between align-items-center" style="font-size:.75rem;color:var(--hf-text-muted)">' +
        '<span>' + days + '</span>' +
        (g.milestoneReached > 0 && percent < 100
          ? '<span class="hf-chip amber"><i class="bi bi-flag"></i>' + g.milestoneReached + '% milestone</span>' : '') +
      '</div>' +
      (g.milestoneMessage ? '<div class="mt-2" style="font-size:.75rem;color:var(--hf-teal-dark);font-weight:600">' +
        '<i class="bi bi-stars me-1"></i>' + HF.esc(g.milestoneMessage) + '</div>' : '') +
      '</div></div>';
  }

  function openForm(goal) {
    document.getElementById('goalFormError').classList.add('d-none');
    document.getElementById('goalId').value = goal ? goal.id : '';
    document.getElementById('goalModalTitle').textContent = goal ? 'Edit goal' : 'New goal';
    document.getElementById('goalTitle').value = goal ? goal.title : '';
    document.getElementById('goalDescription').value = goal && goal.description ? goal.description : '';
    document.getElementById('goalTarget').value = goal ? goal.targetValue : '';
    document.getElementById('goalDate').value = goal && goal.targetDate ? goal.targetDate : '';
    document.getElementById('goalProgress').value = goal ? goal.progress : 0;

    const linkedIds = goal && goal.habitIds ? goal.habitIds : [];
    const box = document.getElementById('goalHabits');
    box.innerHTML = activeHabits.length
      ? activeHabits.map(function (h) {
          return '<label class="d-flex align-items-center gap-2 py-1" style="font-size:.85rem">' +
            '<input class="form-check-input mt-0" type="checkbox" value="' + h.id + '" ' +
            (linkedIds.indexOf(h.id) >= 0 ? 'checked' : '') + '>' +
            '<span class="hf-habit-icon" style="width:24px;height:24px;font-size:.7rem;' + HF.iconTint(h.color) + '">' +
            '<i class="bi ' + HF.esc(h.icon) + '"></i></span>' +
            HF.esc(h.name) + '</label>';
        }).join('')
      : '<p style="font-size:.8rem;color:var(--hf-text-muted)">No active habits to link yet.</p>';
    modal.show();
  }

  function save() {
    const errorBox = document.getElementById('goalFormError');
    errorBox.classList.add('d-none');
    const title = document.getElementById('goalTitle').value.trim();
    if (!title) return fail('Please give your goal a title.');
    const target = Number(document.getElementById('goalTarget').value || 1);
    if (!target || target < 1) return fail('Target must be at least 1.');

    const habitIds = Array.from(document.querySelectorAll('#goalHabits input:checked'))
      .map(function (i) { return Number(i.value); });
    const payload = {
      title: title,
      description: document.getElementById('goalDescription').value.trim() || null,
      targetValue: target,
      manualProgress: Number(document.getElementById('goalProgress').value || 0),
      targetDate: document.getElementById('goalDate').value || null,
      habitIds: habitIds,
      status: 'ACTIVE'
    };

    const id = document.getElementById('goalId').value;
    const button = document.getElementById('goalSaveBtn');
    button.disabled = true;
    const request = id ? HF.put('/api/goals/' + id, payload) : HF.post('/api/goals', payload);
    request.then(function () {
      modal.hide();
      button.disabled = false;
      HF.toast(id ? 'Goal updated.' : 'Goal created.', 'success');
      load();
    }).catch(function (error) {
      button.disabled = false;
      const fieldErrors = error.data && error.data.fieldErrors;
      fail(fieldErrors ? Object.values(fieldErrors).join(' ') : error.message);
    });

    function fail(message) {
      errorBox.textContent = message;
      errorBox.classList.remove('d-none');
      button.disabled = false;
    }
  }

  function remove(id) {
    const goal = goals.find(function (g) { return g.id === id; });
    HF.confirm({
      title: 'Delete goal?',
      message: '“' + (goal ? goal.title : 'This goal') + '” will be removed. Your habit history is not affected.',
      confirmLabel: 'Delete goal'
    }).then(function (ok) {
      if (!ok) return;
      HF.del('/api/goals/' + id).then(function () {
        HF.toast('Goal deleted.', 'info');
        load();
      }).catch(function (error) { HF.toast(error.message, 'error'); });
    });
  }

  function setStatus(id, status) {
    HF.patch('/api/goals/' + id + '/status', { status: status }).then(function () {
      HF.toast(status === 'COMPLETED' ? 'Goal completed. Well done!' : 'Goal reopened.', 'success');
      load();
    }).catch(function (error) { HF.toast(error.message, 'error'); });
  }
});
