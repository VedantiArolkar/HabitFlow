/* Analytics - GET /api/analytics?from=&to=&habitId= */
document.addEventListener('DOMContentLoaded', function () {
  const params = new URLSearchParams(window.location.search);
  const fromInput = document.getElementById('rangeFrom');
  const toInput = document.getElementById('rangeTo');
  const habitSelect = document.getElementById('rangeHabit');

  fromInput.value = HF.todayIso({ days: -29 });
  toInput.value = HF.todayIso();

  document.getElementById('applyRange').addEventListener('click', function () { load(); });
  document.querySelectorAll('[data-range]').forEach(function (btn) {
    btn.addEventListener('click', function () {
      const days = Number(btn.dataset.range);
      fromInput.value = HF.isoDate(HF.addDays(HF.parseDate(HF.todayIso()), -(days - 1)));
      toInput.value = HF.todayIso();
      load();
    });
  });
  habitSelect.addEventListener('change', function () { load(); });

  HF.get('/api/habits?status=ALL').then(function (habits) {
    (habits || []).forEach(function (h) {
      const option = document.createElement('option');
      option.value = h.id;
      option.textContent = h.name;
      habitSelect.appendChild(option);
    });
    if (params.get('habitId')) {
      habitSelect.value = params.get('habitId');
      load();
    }
  }).catch(function () { /* selector stays on all habits */ });

  load();

  function load() {
    const query = '?from=' + fromInput.value + '&to=' + toInput.value +
      (habitSelect.value ? '&habitId=' + habitSelect.value : '');
    HF.get('/api/analytics' + query).then(render).catch(function (error) {
      HF.toast(error.message, 'error');
    });
  }

  function render(a) {
    document.getElementById('anOverall').textContent = Math.round(a.overallRate * 10) / 10 + '%';
    document.getElementById('anCompleted').textContent = a.completedSessions;
    document.getElementById('anScheduled').textContent = a.scheduledSessions + ' scheduled sessions';
    document.getElementById('anOverallNote').textContent =
      a.completedSessions + ' of ' + a.scheduledSessions + ' sessions completed';
    document.getElementById('anDaily').textContent = Math.round(a.dailyRate) + '%';
    document.getElementById('anWeekly').textContent = 'Last 7 days: ' + Math.round(a.weeklyRate) + '%';
    document.getElementById('anMonthly').textContent = 'Last 30 days: ' + Math.round(a.monthlyRate) + '%';

    /* trend line */
    HF.chart('trendChart', {
      type: 'line',
      data: {
        labels: a.trend.map(function (p) { return p.label; }),
        datasets: [{
          data: a.trend.map(function (p) { return p.value; }),
          borderColor: '#3B82F6', borderWidth: 2.5, tension: 0.35,
          fill: true,
          backgroundColor: HF.gradient(document.getElementById('trendChart').getContext('2d'),
            'rgba(59,130,246,.25)', 'rgba(59,130,246,0)'),
          pointRadius: 3, pointBackgroundColor: '#fff', pointBorderColor: '#3B82F6', pointBorderWidth: 2
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        scales: {
          y: { beginAtZero: true, max: 100, grid: { color: '#F1F3FA' }, ticks: { callback: function (v) { return v + '%'; } } },
          x: { grid: { display: false }, ticks: { maxTicksLimit: 10, font: { size: 10 } } }
        },
        plugins: { legend: { display: false } }
      }
    });

    /* habit comparison */
    if (!a.habitPerformance.length) {
      document.getElementById('habitPerfChart').parentElement.innerHTML =
        HF.emptyState('bi-bar-chart', 'No habit data in this range', 'Try a wider date range.', '');
    } else {
      HF.chart('habitPerfChart', {
        type: 'bar',
        data: {
          labels: a.habitPerformance.map(function (p) { return p.label; }),
          datasets: [{
            data: a.habitPerformance.map(function (p) { return p.value; }),
            backgroundColor: '#7C6BFF', borderRadius: 6, maxBarThickness: 26
          }]
        },
        options: {
          indexAxis: 'y', responsive: true, maintainAspectRatio: false,
          scales: {
            x: { beginAtZero: true, max: 100, grid: { color: '#F1F3FA' }, ticks: { callback: function (v) { return v + '%'; } } },
            y: { grid: { display: false }, ticks: { font: { size: 10 } } }
          },
          plugins: { legend: { display: false } }
        }
      });
    }

    /* category performance */
    HF.chart('categoryPerfChart', {
      type: 'bar',
      data: {
        labels: a.categoryPerformance.map(function (p) { return p.label; }),
        datasets: [{
          data: a.categoryPerformance.map(function (p) { return p.value; }),
          backgroundColor: '#14B8A6', borderRadius: 6, maxBarThickness: 30
        }]
      },
      options: {
        responsive: true, maintainAspectRatio: false,
        scales: {
          y: { beginAtZero: true, max: 100, grid: { color: '#F1F3FA' }, ticks: { callback: function (v) { return v + '%'; } } },
          x: { grid: { display: false }, ticks: { font: { size: 10 } } }
        },
        plugins: { legend: { display: false } }
      }
    });

    /* heatmap */
    const heatmap = document.getElementById('heatmap');
    if (!a.heatmap.length) {
      heatmap.innerHTML = '<div style="font-size:.82rem;color:var(--hf-text-muted);padding:1rem 0">No data in this range yet.</div>';
    } else {
      heatmap.innerHTML = a.heatmap.map(function (cell) {
        return '<div class="hf-heat-cell hf-heat-' + cell.level + '" title="' +
          cell.date + ' — ' + cell.completed + '/' + cell.scheduled + ' completed"></div>';
      }).join('');
    }

    /* table */
    const table = document.getElementById('statsTable');
    if (!a.habitStats.length) {
      table.innerHTML = '<tr><td colspan="6" class="text-center py-4" style="color:var(--hf-text-muted)">No habits in this range.</td></tr>';
    } else {
      table.innerHTML = a.habitStats.map(function (s) {
        const tone = s.overallRate >= 85 ? 'green' : s.overallRate >= 65 ? 'teal' : s.overallRate >= 40 ? 'amber' : 'red';
        return '<tr><td style="font-weight:650">' + HF.esc(s.habitName) + '</td>' +
          '<td>' + s.currentStreak + ' days</td>' +
          '<td>' + s.longestStreak + ' days</td>' +
          '<td>' + s.totalCompletions + '</td>' +
          '<td>' + Math.round(s.overallRate) + '% <span style="color:var(--hf-text-muted);font-size:.72rem">(' +
            s.completedInRange + '/' + s.scheduledDaysInRange + ')</span></td>' +
          '<td><span class="hf-chip ' + tone + '">' + HF.esc(s.consistencyLabel) + '</span></td></tr>';
      }).join('');
    }

    document.getElementById('bestList').innerHTML = a.bestHabits.length
      ? a.bestHabits.map(function (name, i) {
          return '<div class="d-flex align-items-center gap-2 py-2" style="font-size:.85rem">' +
            '<span class="hf-chip green" style="min-width:24px;justify-content:center">' + (i + 1) + '</span>' +
            '<span>' + HF.esc(name) + '</span></div>';
        }).join('')
      : '<p style="font-size:.8rem;color:var(--hf-text-muted)">Not enough history yet.</p>';

    document.getElementById('lowList').innerHTML = a.lowHabits.length
      ? a.lowHabits.map(function (name) {
          return '<div class="d-flex align-items-center gap-2 py-2" style="font-size:.85rem">' +
            '<i class="bi bi-exclamation-triangle" style="color:#F59E0B"></i><span>' + HF.esc(name) + '</span></div>';
        }).join('')
      : '<p style="font-size:.8rem;color:var(--hf-text-muted)">No habits below 60% consistency. Nice work.</p>';
  }
});
