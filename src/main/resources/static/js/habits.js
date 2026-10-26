/* Habit management - full CRUD against /api/habits */
document.addEventListener('DOMContentLoaded', function () {
  const COLORS = ['#7C6BFF', '#3B82F6', '#14B8A6', '#10B981', '#F59E0B', '#F97316', '#EC4899', '#6366F1', '#0EA5E9', '#64748B'];
  const ICONS = ['bi-check2-circle', 'bi-droplet', 'bi-activity', 'bi-book', 'bi-code-slash', 'bi-flower1',
    'bi-journal-check', 'bi-moon-stars', 'bi-cup-hot', 'bi-bicycle', 'bi-pencil', 'bi-music-note-beamed',
    'bi-phone', 'bi-heart-pulse', 'bi-briefcase', 'bi-stars'];
  const DAY_LABELS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  let habits = [];
  let filterStatus = 'ALL';
  let searchTerm = '';
  let selectedColor = COLORS[0];
  let selectedIcon = ICONS[0];
  let selectedDays = new Set([1, 2, 3, 4, 5, 6, 7]);

  const modalEl = document.getElementById('habitModal');
  const modal = new bootstrap.Modal(modalEl);

  document.getElementById('newHabitBtn').addEventListener('click', function () { openForm(null); });
  document.getElementById('habitSaveBtn').addEventListener('click', save);
  document.getElementById('habitSearch').addEventListener('input', function (e) {
    searchTerm = e.target.value.toLowerCase().trim();
    render();
  });
  document.querySelectorAll('.habit-filter').forEach(function (btn) {
    btn.addEventListener('click', function () {
      filterStatus = btn.dataset.status;
      document.querySelectorAll('.habit-filter').forEach(function (b) {
        b.classList.remove('active', 'btn-hf-soft');
        b.classList.add('btn-hf-ghost');
      });
      btn.classList.add('active', 'btn-hf-soft');
      btn.classList.remove('btn-hf-ghost');
      load();
    });
  });
  document.getElementById('habitFrequency').addEventListener('change', function (e) {
    applyFrequencyDefaults(e.target.value, true);
  });

  buildPickers();
  load();

  function buildPickers() {
    const colorPicker = document.getElementById('colorPicker');
    colorPicker.innerHTML = COLORS.map(function (c) {
      return '<button type="button" class="btn p-0" data-color="' + c + '" ' +
        'style="width:30px;height:30px;border-radius:9px;background:' + c + ';border:2px solid transparent"></button>';
    }).join('');
    colorPicker.querySelectorAll('[data-color]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        selectedColor = btn.dataset.color;
        highlight(colorPicker, 'data-color', selectedColor, 'border-color');
      });
    });

    const iconPicker = document.getElementById('iconPicker');
    iconPicker.innerHTML = ICONS.map(function (i) {
      return '<button type="button" class="btn btn-hf-ghost btn-sm" data-icon="' + i + '" ' +
        'style="width:36px;height:36px;padding:0"><i class="bi ' + i + '"></i></button>';
    }).join('');
    iconPicker.querySelectorAll('[data-icon]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        selectedIcon = btn.dataset.icon;
        highlight(iconPicker, 'data-icon', selectedIcon, 'background');
      });
    });

    const dayPicker = document.getElementById('dayPicker');
    dayPicker.innerHTML = DAY_LABELS.map(function (label, index) {
      return '<button type="button" class="btn btn-hf-ghost btn-sm" data-day="' + (index + 1) + '">' + label + '</button>';
    }).join('');
    dayPicker.querySelectorAll('[data-day]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        const day = Number(btn.dataset.day);
        if (selectedDays.has(day)) { selectedDays.delete(day); } else { selectedDays.add(day); }
        paintDays();
      });
    });
  }

  function highlight(container, attr, value, prop) {
    container.querySelectorAll('[' + attr + ']').forEach(function (b) {
      if (prop === 'background') {
        b.style.background = b.getAttribute(attr) === value ? 'var(--hf-purple-soft)' : '';
        b.style.color = b.getAttribute(attr) === value ? 'var(--hf-purple-dark)' : '';
      } else {
        b.style.borderColor = b.getAttribute(attr) === value ? 'var(--hf-purple-dark)' : 'transparent';
      }
    });
  }

  function paintDays() {
    document.querySelectorAll('#dayPicker [data-day]').forEach(function (btn) {
      const day = Number(btn.dataset.day);
      if (selectedDays.has(day)) {
        btn.classList.remove('btn-hf-ghost');
        btn.classList.add('btn-hf-soft');
      } else {
        btn.classList.add('btn-hf-ghost');
        btn.classList.remove('btn-hf-soft');
      }
    });
  }

  function applyFrequencyDefaults(frequency, fromUser) {
    if (!fromUser) return;
    if (frequency === 'DAILY' || frequency === 'WEEKLY') selectedDays = new Set([1, 2, 3, 4, 5, 6, 7]);
    if (frequency === 'WEEKDAYS') selectedDays = new Set([1, 2, 3, 4, 5]);
    if (frequency === 'CUSTOM') selectedDays = new Set([1, 3, 5]);
    paintDays();
    document.getElementById('habitWeeklyTarget').disabled = frequency !== 'WEEKLY';
  }

  function load() {
    HF.get('/api/habits?status=' + filterStatus).then(function (data) {
      habits = data || [];
      render();
    }).catch(function (error) {
      HF.toast(error.message, 'error');
      document.getElementById('habitGrid').innerHTML =
        HF.emptyState('bi-exclamation-triangle', 'Could not load habits', error.message, '');
    });
  }

  function render() {
    const grid = document.getElementById('habitGrid');
    const filtered = habits.filter(function (h) {
      return !searchTerm || h.name.toLowerCase().indexOf(searchTerm) >= 0 ||
        (h.categoryName || '').toLowerCase().indexOf(searchTerm) >= 0;
    });

    if (!filtered.length) {
      grid.innerHTML = '<div class="col-12"><div class="hf-card">' +
        HF.emptyState('bi-list-task', habits.length ? 'No habits match your search' : 'No habits yet',
          habits.length ? 'Try a different search term or switch the filter.'
            : 'Create your first habit — reading for 20 minutes, exercising, or practising Java are good starts.',
          '<button class="btn btn-hf btn-sm" onclick="document.getElementById(\'newHabitBtn\').click()">Create a habit</button>') +
        '</div></div>';
      return;
    }

    grid.innerHTML = filtered.map(card).join('');
    grid.querySelectorAll('[data-edit]').forEach(function (b) {
      b.addEventListener('click', function () { openForm(habits.find(function (h) { return h.id === Number(b.dataset.edit); })); });
    });
    grid.querySelectorAll('[data-delete]').forEach(function (b) {
      b.addEventListener('click', function () { remove(Number(b.dataset.delete)); });
    });
    grid.querySelectorAll('[data-archive]').forEach(function (b) {
      b.addEventListener('click', function () {
        setStatus(Number(b.dataset.archive), b.dataset.next);
      });
    });
  }

  function card(h) {
    const days = (h.scheduleDays || []).length === 7 ? 'Every day'
      : (h.scheduleDays || []).map(function (d) { return DAY_LABELS[d - 1]; }).join(' · ');
    const archived = h.status === 'ARCHIVED';
    return '<div class="col-md-6 col-xl-4"><div class="hf-card hf-card-pad h-100' +
      (archived ? ' opacity-75' : '') + '">' +
      '<div class="d-flex align-items-start gap-3 mb-3">' +
        '<div class="hf-habit-icon" style="' + HF.iconTint(h.color) + '"><i class="bi ' + HF.esc(h.icon) + '"></i></div>' +
        '<div class="flex-grow-1 min-w-0">' +
          '<div style="font-weight:700;font-size:.95rem;line-height:1.3">' + HF.esc(h.name) + '</div>' +
          '<div style="font-size:.75rem;color:var(--hf-text-muted)">' + HF.esc(h.categoryName || 'Other') + '</div>' +
        '</div>' +
        '<div class="dropdown">' +
          '<button class="btn btn-sm btn-hf-ghost px-2" data-bs-toggle="dropdown"><i class="bi bi-three-dots-vertical"></i></button>' +
          '<ul class="dropdown-menu dropdown-menu-end">' +
            '<li><button class="dropdown-item" data-edit="' + h.id + '"><i class="bi bi-pencil me-2"></i>Edit</button></li>' +
            '<li><button class="dropdown-item" data-archive="' + h.id + '" data-next="' + (archived ? 'ACTIVE' : 'ARCHIVED') + '">' +
              '<i class="bi ' + (archived ? 'bi-arrow-up-circle' : 'bi-archive') + ' me-2"></i>' +
              (archived ? 'Reactivate' : 'Archive') + '</button></li>' +
            '<li><hr class="dropdown-divider"></li>' +
            '<li><button class="dropdown-item text-danger" data-delete="' + h.id + '"><i class="bi bi-trash me-2"></i>Delete</button></li>' +
          '</ul>' +
        '</div>' +
      '</div>' +
      (h.description ? '<p style="font-size:.8rem;color:var(--hf-text-soft);line-height:1.5;margin-bottom:.85rem">' +
        HF.esc(h.description) + '</p>' : '') +
      '<div class="d-flex flex-wrap gap-1 mb-3">' +
        '<span class="hf-chip purple"><i class="bi bi-repeat"></i>' + HF.esc(h.frequency) + '</span>' +
        '<span class="hf-chip"><i class="bi bi-calendar-week"></i>' + days + '</span>' +
        (h.reminderTime ? '<span class="hf-chip blue"><i class="bi bi-alarm"></i>' + HF.esc(h.reminderTime.slice(0, 5)) + '</span>' : '') +
        (h.status === 'ARCHIVED' ? '<span class="hf-chip amber"><i class="bi bi-archive"></i>Archived</span>' : '') +
      '</div>' +
      '<div class="d-flex justify-content-between align-items-center" style="font-size:.76rem;color:var(--hf-text-muted)">' +
        '<span>Since ' + HF.fmtDate(h.startDate, 'short') + '</span>' +
        '<a href="/analytics?habitId=' + h.id + '" style="font-weight:700">Analytics →</a>' +
      '</div></div></div>';
  }

  function openForm(habit) {
    document.getElementById('habitFormError').classList.add('d-none');
    document.getElementById('habitId').value = habit ? habit.id : '';
    document.getElementById('habitModalTitle').textContent = habit ? 'Edit habit' : 'New habit';
    document.getElementById('habitName').value = habit ? habit.name : '';
    document.getElementById('habitDescription').value = habit && habit.description ? habit.description : '';
    document.getElementById('habitFrequency').value = habit ? habit.frequency : 'DAILY';
    document.getElementById('habitWeeklyTarget').value = habit && habit.weeklyTarget ? habit.weeklyTarget : '';
    document.getElementById('habitStartDate').value = habit ? habit.startDate : HF.todayIso();
    document.getElementById('habitTarget').value = habit && habit.targetValue ? habit.targetValue : '';
    document.getElementById('habitUnit').value = habit && habit.targetUnit ? habit.targetUnit : '';
    document.getElementById('habitReminder').value = habit && habit.reminderTime ? habit.reminderTime.slice(0, 5) : '';
    selectedColor = habit && habit.color ? habit.color : COLORS[0];
    selectedIcon = habit && habit.icon ? habit.icon : ICONS[0];
    selectedDays = new Set(habit && habit.scheduleDays && habit.scheduleDays.length
      ? habit.scheduleDays : [1, 2, 3, 4, 5, 6, 7]);
    document.getElementById('habitWeeklyTarget').disabled = (habit ? habit.frequency : 'DAILY') !== 'WEEKLY';

    HF.loadCategories().then(function (cats) {
      const select = document.getElementById('habitCategory');
      select.innerHTML = cats.map(function (c) {
        return '<option value="' + c.id + '">' + HF.esc(c.name) + '</option>';
      }).join('');
      if (habit && habit.categoryId) select.value = String(habit.categoryId);
    });

    highlight(document.getElementById('colorPicker'), 'data-color', selectedColor, 'border-color');
    highlight(document.getElementById('iconPicker'), 'data-icon', selectedIcon, 'background');
    paintDays();
    modal.show();
  }

  function save() {
    const errorBox = document.getElementById('habitFormError');
    errorBox.classList.add('d-none');

    const name = document.getElementById('habitName').value.trim();
    if (!name) return fail('Please give your habit a name.');
    if (selectedDays.size === 0 && document.getElementById('habitFrequency').value !== 'WEEKLY') {
      return fail('Please choose at least one day.');
    }
    const target = document.getElementById('habitTarget').value;
    const payload = {
      name: name,
      description: document.getElementById('habitDescription').value.trim() || null,
      categoryId: Number(document.getElementById('habitCategory').value),
      targetValue: target ? Number(target) : null,
      targetUnit: document.getElementById('habitUnit').value.trim() || null,
      frequency: document.getElementById('habitFrequency').value,
      weeklyTarget: document.getElementById('habitWeeklyTarget').value
        ? Number(document.getElementById('habitWeeklyTarget').value) : null,
      color: selectedColor,
      icon: selectedIcon,
      reminderTime: document.getElementById('habitReminder').value || null,
      startDate: document.getElementById('habitStartDate').value || HF.todayIso(),
      scheduleDays: Array.from(selectedDays).sort(),
      status: 'ACTIVE'
    };

    const id = document.getElementById('habitId').value;
    const button = document.getElementById('habitSaveBtn');
    button.disabled = true;
    const request = id ? HF.put('/api/habits/' + id, payload) : HF.post('/api/habits', payload);
    request.then(function () {
      modal.hide();
      button.disabled = false;
      HF.toast(id ? 'Habit updated.' : 'Habit created.', 'success');
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
    const habit = habits.find(function (h) { return h.id === id; });
    HF.confirm({
      title: 'Delete habit?',
      message: '“' + (habit ? habit.name : 'This habit') + '” and all of its completion history will be permanently deleted.',
      confirmLabel: 'Delete habit'
    }).then(function (ok) {
      if (!ok) return;
      HF.del('/api/habits/' + id).then(function () {
        HF.toast('Habit deleted.', 'info');
        load();
      }).catch(function (error) { HF.toast(error.message, 'error'); });
    });
  }

  function setStatus(id, next) {
    HF.patch('/api/habits/' + id + '/status', { status: next }).then(function () {
      HF.toast(next === 'ARCHIVED' ? 'Habit archived.' : 'Habit reactivated.', 'success');
      load();
    }).catch(function (error) { HF.toast(error.message, 'error'); });
  }
});
