/* Daily tracking page - GET /api/tracking?date=, POST/DELETE completions */
document.addEventListener('DOMContentLoaded', function () {
  const params = new URLSearchParams(window.location.search);
  let currentDate = params.get('date') || HF.todayIso();
  let pendingHabitId = null;
  let pendingButton = null;
  const noteModal = new bootstrap.Modal(document.getElementById('noteModal'));

  const dateInput = document.getElementById('trackingDateInput');
  dateInput.value = currentDate;

  document.getElementById('prevDay').addEventListener('click', function () { shift(-1); });
  document.getElementById('nextDay').addEventListener('click', function () { shift(1); });
  document.getElementById('todayBtn').addEventListener('click', function () { go(HF.todayIso()); });
  dateInput.addEventListener('change', function () { go(dateInput.value); });

  document.getElementById('noteSave').addEventListener('click', function () {
    if (pendingHabitId === null) return;
    const note = document.getElementById('noteText').value.trim();
    noteModal.hide();
    submit(pendingHabitId, pendingButton, note || null);
  });

  load();

  function shift(delta) {
    const next = HF.addDays(HF.parseDate(currentDate), delta);
    if (next > new Date()) {
      HF.toast('You cannot open a future date.', 'info');
      return;
    }
    go(HF.isoDate(next));
  }

  function go(date) {
    if (!date) return;
    currentDate = date;
    dateInput.value = date;
    const url = new URL(window.location);
    url.searchParams.set('date', date);
    window.history.replaceState({}, '', url);
    load();
  }

  function load() {
    document.getElementById('trackingList').innerHTML =
      '<div class="hf-skeleton mb-2" style="height:64px"></div><div class="hf-skeleton" style="height:64px"></div>';
    HF.get('/api/tracking?date=' + currentDate).then(render).catch(function (error) {
      HF.toast(error.message, 'error');
      document.getElementById('trackingList').innerHTML =
        HF.emptyState('bi-exclamation-triangle', 'Could not load this day', error.message, '');
    });
  }

  function render(view) {
    document.getElementById('trackingDateLabel').textContent =
      view.today ? 'Today · ' + HF.fmtDate(view.date, 'short') : HF.fmtDate(view.date, 'long');
    document.getElementById('trackingDateSub').textContent =
      view.future ? 'Future date — not trackable' :
      view.scheduledCount === 0 ? 'Nothing scheduled on this day' :
      view.completedCount + ' of ' + view.scheduledCount + ' scheduled habits completed';

    document.getElementById('futureNotice').classList.toggle('d-none', !view.future);
    document.getElementById('trackingPercent').textContent = Math.round(view.completionPercent) + '%';
    document.getElementById('trackingCounts').textContent =
      view.completedCount + ' of ' + view.scheduledCount + ' completed';
    document.getElementById('trackingRing').innerHTML = HF.ring(view.completionPercent, '#14B8A6');
    document.getElementById('completedChip').textContent = view.completedCount + ' done';
    document.getElementById('pendingChip').textContent =
      Math.max(0, view.scheduledCount - view.completedCount) + ' left';

    const list = document.getElementById('trackingList');
    if (!view.habits.length) {
      list.innerHTML = HF.emptyState('bi-calendar-x', 'Nothing scheduled',
        view.future ? 'Future dates have no completion data yet.'
                   : 'No habits were due on this date. Unscheduled days are never counted as missed.',
        '<a class="btn btn-hf btn-sm" href="/habits">Manage habits</a>');
      return;
    }

    list.innerHTML = view.habits.map(function (h) {
      const disabled = view.future ? 'disabled' : '';
      return '<div class="hf-habit' + (h.completed ? ' done' : '') + '">' +
        '<button class="hf-check' + (h.completed ? ' checked' : '') + '" ' +
          'data-toggle="' + h.habitId + '" data-completed="' + h.completed + '" ' +
          (h.completed || view.future ? 'disabled' : '') + '>' +
          '<i class="bi ' + (h.completed ? 'bi-check-lg' : 'bi-check-lg') + '"></i></button>' +
        '<div class="hf-habit-icon" style="' + HF.iconTint(h.color) + '"><i class="bi ' + HF.esc(h.icon) + '"></i></div>' +
        '<div class="hf-habit-main"><p class="hf-habit-name">' + HF.esc(h.name) + '</p>' +
        '<div class="hf-habit-meta">' +
          '<span><i class="bi bi-tag me-1"></i>' + HF.esc(h.categoryName || 'Other') + '</span>' +
          (h.reminderTime ? '<span><i class="bi bi-alarm me-1"></i>' + HF.esc(h.reminderTime.slice(0, 5)) + '</span>' : '') +
          '<span><i class="bi bi-fire me-1"></i>' + h.currentStreak + ' day streak</span>' +
          (h.note ? '<span class="fst-italic">“' + HF.esc(h.note) + '”</span>' : '') +
        '</div></div>' +
        (h.completed
          ? '<button class="btn btn-hf-ghost btn-sm" data-undo="' + h.habitId + '"><i class="bi bi-arrow-counterclockwise me-1"></i>Undo</button>'
          : '<button class="btn btn-hf-soft btn-sm" data-note="' + h.habitId + '"><i class="bi bi-journal-text me-1"></i>Add note</button>') +
        '</div>';
    }).join('');

    list.querySelectorAll('[data-undo]').forEach(function (btn) {
      btn.addEventListener('click', function () { undo(Number(btn.dataset.undo), btn); });
    });
    list.querySelectorAll('[data-note]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        pendingHabitId = Number(btn.dataset.note);
        pendingButton = btn;
        const habit = view.habits.find(function (h) { return h.habitId === pendingHabitId; });
        document.getElementById('noteHabitName').textContent = habit ? habit.name : 'this habit';
        document.getElementById('noteText').value = '';
        noteModal.show();
      });
    });
    list.querySelectorAll('[data-toggle]').forEach(function (btn) {
      if (btn.dataset.completed === 'true') return;
      btn.addEventListener('click', function () { submit(Number(btn.dataset.toggle), btn, null); });
    });
  }

  function submit(habitId, button, note) {
    if (button) button.disabled = true;
    HF.post('/api/habits/' + habitId + '/complete', { date: currentDate, note: note })
      .then(function (result) {
        HF.toast('Completed' + (result && result.currentStreak ? ' — ' + result.currentStreak + ' day streak!' : '!'), 'success');
        HF.loadUnreadCount();
        load();
      })
      .catch(function (error) {
        HF.toast(error.message, 'error');
        if (button) button.disabled = false;
      });
  }

  function undo(habitId, button) {
    HF.confirm({
      title: 'Undo completion?',
      message: 'The completion record for this date will be deleted from the database.',
      confirmLabel: 'Undo'
    }).then(function (ok) {
      if (!ok) return;
      button.disabled = true;
      HF.del('/api/habits/' + habitId + '/complete?date=' + currentDate)
        .then(function () { HF.toast('Completion removed.', 'info'); load(); })
        .catch(function (error) { HF.toast(error.message, 'error'); button.disabled = false; });
    });
  }
});
