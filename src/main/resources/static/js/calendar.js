/* Calendar - GET /api/calendar?from=&to=&habitId= renders a month grid + day detail */
document.addEventListener('DOMContentLoaded', function () {
  const DOW = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
  const today = HF.parseDate(HF.todayIso());
  let cursor = new Date(today.getFullYear(), today.getMonth(), 1);
  let days = {};
  let selected = HF.todayIso();
  let habitId = '';

  document.getElementById('prevMonth').addEventListener('click', function () { shift(-1); });
  document.getElementById('nextMonth').addEventListener('click', function () { shift(1); });
  document.getElementById('calToday').addEventListener('click', function () {
    cursor = new Date(today.getFullYear(), today.getMonth(), 1);
    selected = HF.todayIso();
    load();
  });
  document.getElementById('calHabitFilter').addEventListener('change', function (e) {
    habitId = e.target.value;
    load();
  });

  HF.get('/api/habits?status=ALL').then(function (habits) {
    const select = document.getElementById('calHabitFilter');
    (habits || []).forEach(function (h) {
      const option = document.createElement('option');
      option.value = h.id;
      option.textContent = h.name;
      select.appendChild(option);
    });
  }).catch(function () { /* filter stays "All habits" */ });

  load();

  function shift(delta) {
    cursor = new Date(cursor.getFullYear(), cursor.getMonth() + delta, 1);
    load();
  }

  function load() {
    const from = HF.isoDate(cursor);
    const last = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0);
    const to = HF.isoDate(last);
    document.getElementById('calLabel').textContent =
      cursor.toLocaleString('en-US', { month: 'long', year: 'numeric' });

    document.getElementById('calGrid').innerHTML = '<div class="hf-skeleton" style="height:260px;grid-column:1/-1"></div>';

    HF.get('/api/calendar?from=' + from + '&to=' + to + (habitId ? '&habitId=' + habitId : ''))
      .then(function (data) {
        days = {};
        (data || []).forEach(function (d) { days[d.date] = d; });
        draw();
      })
      .catch(function (error) {
        HF.toast(error.message, 'error');
        document.getElementById('calGrid').innerHTML =
          '<div style="grid-column:1/-1;padding:2rem;text-align:center;color:var(--hf-text-muted);font-size:.85rem">' +
          'Could not load this month.</div>';
      });
  }

  function draw() {
    const grid = document.getElementById('calGrid');
    const firstDay = (cursor.getDay() + 6) % 7; // Monday-first offset
    const total = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0).getDate();
    let html = '';

    for (let i = 0; i < firstDay; i++) html += '<div class="hf-cal-cell muted"></div>';

    let monthScheduled = 0;
    let monthCompleted = 0;

    for (let dayNum = 1; dayNum <= total; dayNum++) {
      const date = HF.isoDate(new Date(cursor.getFullYear(), cursor.getMonth(), dayNum));
      const info = days[date];
      const future = HF.parseDate(date) > today;
      let cls = 'hf-cal-cell empty';
      let badge = '';

      if (info && info.scheduled > 0) {
        monthScheduled += info.scheduled;
        monthCompleted += info.completed;
        if (info.completed >= info.scheduled) { cls = 'hf-cal-cell done'; badge = '<i class="bi bi-check-lg"></i>'; }
        else if (info.completed > 0) { cls = 'hf-cal-cell partial'; badge = info.completed + '/' + info.scheduled; }
        else { cls = future ? 'hf-cal-cell future' : 'hf-cal-cell missed'; badge = future ? '' : '0/' + info.scheduled; }
      }

      const isToday = date === HF.todayIso();
      html += '<button type="button" class="' + cls + (isToday ? ' today' : '') + (date === selected ? ' selected' : '') +
        '" data-date="' + date + '">' +
        '<span class="hf-cal-num">' + dayNum + '</span>' +
        '<span class="hf-cal-badge">' + badge + '</span></button>';
    }

    const remainder = (firstDay + total) % 7;
    for (let i = 0; i < (remainder === 0 ? 0 : 7 - remainder); i++) html += '<div class="hf-cal-cell muted"></div>';
    grid.innerHTML = html;

    const rate = monthScheduled ? Math.round((monthCompleted / monthScheduled) * 1000) / 10 : 0;
    document.getElementById('calMonthRate').textContent = rate + '%';
    document.getElementById('calMonthDetail').textContent = monthCompleted + ' of ' + monthScheduled + ' sessions';
    document.getElementById('calRange').textContent =
      HF.fmtDate(HF.isoDate(cursor), 'short') + ' — ' +
      HF.fmtDate(HF.isoDate(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0)), 'short');

    grid.querySelectorAll('[data-date]').forEach(function (btn) {
      btn.addEventListener('click', function () {
        selected = btn.dataset.date;
        grid.querySelectorAll('.selected').forEach(function (el) { el.classList.remove('selected'); });
        btn.classList.add('selected');
        detail(selected);
      });
    });

    if (selected) detail(selected);
  }

  function detail(date) {
    const info = days[date];
    const title = document.getElementById('calDetailTitle');
    const sub = document.getElementById('calDetailSub');
    const box = document.getElementById('calDetail');
    title.textContent = HF.fmtDate(date, 'long');

    if (!info || !info.habits || !info.habits.length) {
      sub.textContent = 'Nothing was scheduled';
      box.innerHTML = HF.emptyState('bi-calendar-x', 'Nothing scheduled',
        'Unscheduled days are never counted as missed habits.', '');
      return;
    }

    sub.textContent = info.completed + ' of ' + info.scheduled + ' completed';
    box.innerHTML = info.habits.map(function (h) {
      return '<div class="hf-habit' + (h.completed ? ' done' : '') + '" style="padding:.6rem .7rem">' +
        '<span class="hf-check' + (h.completed ? ' checked' : '') + '" style="pointer-events:none">' +
        '<i class="bi ' + (h.completed ? 'bi-check-lg' : 'bi-dash') + '"></i></span>' +
        '<div class="hf-habit-icon" style="' + HF.iconTint(h.color) + '"><i class="bi ' + HF.esc(h.icon) + '"></i></div>' +
        '<div class="hf-habit-main"><p class="hf-habit-name">' + HF.esc(h.name) + '</p>' +
        '<div class="hf-habit-meta"><span><i class="bi bi-tag me-1"></i>' + HF.esc(h.categoryName || 'Other') + '</span>' +
        (h.note ? '<span class="fst-italic">“' + HF.esc(h.note) + '”</span>' : '') + '</div></div></div>';
    }).join('') +
      '<a class="btn btn-hf-soft btn-sm w-100 mt-2" href="/tracking?date=' + date + '">' +
      '<i class="bi bi-box-arrow-up-right me-1"></i>Open this day in tracking</a>';
  }
});
