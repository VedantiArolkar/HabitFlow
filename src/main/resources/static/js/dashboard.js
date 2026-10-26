/* Dashboard page - all values come from GET /api/dashboard */
document.addEventListener('DOMContentLoaded', function () {
  load();

  function load() {
    HF.get('/api/dashboard').then(render).catch(function (error) {
      HF.toast(error.message, 'error');
      document.getElementById('todayList').innerHTML =
        HF.emptyState('bi-wifi-off', 'Could not load your dashboard', error.message, '');
    });
  }

  function render(d) {
    const set = function (id, value) { const el = document.getElementById(id); if (el) el.textContent = value; };

    set('statActive', d.activeHabits);
    set('statActiveNote', d.activeHabits === 1 ? 'habit tracked' : 'habits tracked');
    set('statCompleted', d.completedToday + '/' + d.scheduledToday);
    set('statCompletedNote', 'of today\'s schedule');
    set('statPercent', Math.round(d.todayPercent) + '%');
    set('statStreak', d.currentStreak);
    set('statBest', d.bestStreak);
    set('statWeekly', Math.round(d.weeklyConsistency) + '%');

    const greeting = document.getElementById('greetingText');
    if (greeting && d.userName) greeting.dataset.name = d.userName;
    const hour = new Date().getHours();
    const part = hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
    if (greeting) {
      greeting.textContent = part + ', ' + (d.userName || 'there').split(' ')[0] + ' 👋';
    }
    const dateLine = document.getElementById('dashboardDateLine');
    if (dateLine) {
      dateLine.textContent = d.scheduledToday === 0
        ? 'Nothing scheduled for today. Add a habit to get started.'
        : d.scheduledToday - d.completedToday === 0
          ? 'Every habit scheduled today is complete. Outstanding.'
          : (d.scheduledToday - d.completedToday) + ' habit' + (d.scheduledToday - d.completedToday === 1 ? '' : 's') + ' still waiting for you today.';
    }

    /* ---- today's habit list ---- */
    const all = (d.pendingToday || []).concat(d.completedTodayList || []);
    const summary = document.getElementById('todaySummary');
    if (summary) summary.textContent = d.completedToday + ' of ' + d.scheduledToday + ' scheduled habits completed';

    const list = document.getElementById('todayList');
    if (!all.length) {
      list.innerHTML = HF.emptyState('bi-calendar-plus', 'Nothing scheduled today',
        'Either your habits are not due today, or you have not created one yet.',
        '<a class="btn btn-hf btn-sm" href="/habits">Create a habit</a>');
    } else {
      list.innerHTML = all.map(habitRow).join('');
      list.querySelectorAll('[data-complete]').forEach(function (btn) {
        btn.addEventListener('click', function () { completeHabit(Number(btn.dataset.complete), btn); });
      });
    }

    /* ---- ring ---- */
    const ringBox = document.getElementById('todayRing');
    if (ringBox) ringBox.innerHTML = HF.ring(d.todayPercent, '#7C6BFF');
    set('ringDone', d.completedToday);
    set('ringLeft', Math.max(0, d.scheduledToday - d.completedToday));
    set('ringTotal', d.scheduledToday);
    set('monthRateText', Math.round(d.monthlyConsistency) + '%');
    const monthProgress = document.getElementById('monthProgress');
    if (monthProgress) monthProgress.innerHTML = HF.progressBar(d.monthlyConsistency, 'teal');

    /* ---- top habit ---- */
    set('bestHabitName', d.bestHabitName || 'No streak yet');
    set('bestHabitStreak', d.bestHabitStreak || 0);

    /* ---- recent ---- */
    const recent = document.getElementById('recentList');
    if (!d.recentCompletions || !d.recentCompletions.length) {
      recent.innerHTML = HF.emptyState('bi-check2-circle', 'No completions yet today',
        'Tick your first habit and it will appear here.', '');
    } else {
      recent.innerHTML = d.recentCompletions.map(function (h) {
        return '<div class="hf-habit done">' +
          '<div class="hf-habit-icon" style="' + HF.iconTint(h.color) + '"><i class="bi ' + HF.esc(h.icon) + '"></i></div>' +
          '<div class="hf-habit-main"><p class="hf-habit-name">' + HF.esc(h.name) + '</p>' +
          '<div class="hf-habit-meta"><span><i class="bi bi-tag me-1"></i>' + HF.esc(h.categoryName || 'Other') + '</span>' +
          '<span><i class="bi bi-fire me-1"></i>' + h.currentStreak + ' day streak</span></div></div>' +
          '<span class="hf-chip green"><i class="bi bi-check2"></i>Done</span></div>';
      }).join('');
    }

    drawCharts(d);
    HF.loadUnreadCount();
  }

  function habitRow(h) {
    return '<div class="hf-habit' + (h.completed ? ' done' : '') + '">' +
      '<button class="hf-check' + (h.completed ? ' checked' : '') + '" data-complete="' + h.habitId + '" ' +
      (h.completed ? 'disabled' : '') + ' title="' + (h.completed ? 'Completed' : 'Mark complete') + '">' +
      '<i class="bi bi-check-lg"></i></button>' +
      '<div class="hf-habit-icon" style="' + HF.iconTint(h.color) + '"><i class="bi ' + HF.esc(h.icon) + '"></i></div>' +
      '<div class="hf-habit-main"><p class="hf-habit-name">' + HF.esc(h.name) + '</p>' +
      '<div class="hf-habit-meta"><span><i class="bi bi-tag me-1"></i>' + HF.esc(h.categoryName || 'Other') + '</span>' +
      (h.reminderTime ? '<span><i class="bi bi-alarm me-1"></i>' + HF.esc(h.reminderTime) + '</span>' : '') +
      '<span><i class="bi bi-fire me-1"></i>' + h.currentStreak + '</span></div></div>' +
      '<a class="btn btn-hf-ghost btn-sm d-none d-sm-inline-flex" href="/tracking?habit=' + h.habitId + '">Open</a></div>';
  }

  function completeHabit(habitId, button) {
    button.disabled = true;
    HF.post('/api/habits/' + habitId + '/complete', { date: HF.todayIso(), note: null })
      .then(function () {
        HF.toast('Habit completed. Streak protected!', 'success');
        load();
      })
      .catch(function (error) {
        HF.toast(error.message, 'error');
        button.disabled = false;
      });
  }

  function drawCharts(d) {
    const weekly = document.getElementById('weeklyChart');
    if (weekly) {
      HF.chart('weeklyChart', {
        type: 'bar',
        data: {
          labels: d.weeklyChart.map(function (p) { return p.label; }),
          datasets: [{
            data: d.weeklyChart.map(function (p) { return p.value; }),
            backgroundColor: d.weeklyChart.map(function (p) {
              return p.value >= 100 ? '#10B981' : p.value >= 50 ? '#7C6BFF' : '#C7C2F5';
            }),
            borderRadius: 7,
            maxBarThickness: 34
          }]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          scales: {
            y: { beginAtZero: true, max: 100, grid: { color: '#F1F3FA' }, ticks: { callback: function (v) { return v + '%'; } } },
            x: { grid: { display: false } }
          },
          plugins: {
            tooltip: {
              callbacks: {
                label: function (ctx) {
                  const point = d.weeklyChart[ctx.dataIndex];
                  return point.value + '% (' + (point.extra || '') + ')';
                }
              }
            }
          }
        }
      });
    }

    const monthly = document.getElementById('monthlyChart');
    if (monthly) {
      const ctx = monthly.getContext('2d');
      HF.chart('monthlyChart', {
        type: 'line',
        data: {
          labels: d.monthlyChart.map(function (p) { return p.label; }),
          datasets: [{
            data: d.monthlyChart.map(function (p) { return p.value; }),
            borderColor: '#7C6BFF',
            borderWidth: 2.5,
            fill: true,
            backgroundColor: HF.gradient(ctx, 'rgba(124,107,255,.28)', 'rgba(124,107,255,0)'),
            tension: 0.4,
            pointBackgroundColor: '#fff',
            pointBorderColor: '#7C6BFF',
            pointBorderWidth: 2,
            pointRadius: 4
          }]
        },
        options: {
          responsive: true, maintainAspectRatio: false,
          scales: {
            y: { beginAtZero: true, max: 100, grid: { color: '#F1F3FA' }, ticks: { callback: function (v) { return v + '%'; } } },
            x: { grid: { display: false } }
          }
        }
      });
    }

    const category = document.getElementById('categoryChart');
    if (category) {
      const labels = d.categoryDistribution.map(function (p) { return p.label; });
      if (!labels.length) {
        category.parentElement.innerHTML = '<div style="font-size:.82rem;color:var(--hf-text-muted);text-align:center;padding:2rem 0">No habits yet</div>';
      } else {
        HF.chart('categoryChart', {
          type: 'doughnut',
          data: {
            labels: labels,
            datasets: [{
              data: d.categoryDistribution.map(function (p) { return p.value; }),
              backgroundColor: ['#7C6BFF', '#3B82F6', '#14B8A6', '#F59E0B', '#EC4899', '#10B981', '#6366F1', '#94A3B8'],
              borderWidth: 0,
              spacing: 2
            }]
          },
          options: {
            responsive: true, maintainAspectRatio: false, cutout: '62%',
            plugins: {
              legend: { display: true, position: 'bottom', labels: { boxWidth: 8, boxHeight: 8, padding: 10, font: { size: 10 } } }
            }
          }
        });
      }
    }
  }
});
