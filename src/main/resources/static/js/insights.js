/* Smart Insights - rule based, computed in Java from real completion history */
document.addEventListener('DOMContentLoaded', function () {
  const TONE = {
    success: { icon: 'green', bg: '#ECFDF5' },
    warning: { icon: 'amber', bg: '#FEF6E7' },
    info: { icon: 'blue', bg: '#E8F0FE' },
    tip: { icon: 'purple', bg: '#EFECFF' }
  };

  document.getElementById('refreshInsights').addEventListener('click', function () { load(true); });
  load();

  function load(showToast) {
    document.getElementById('insightList').innerHTML =
      '<div class="col-md-6"><div class="hf-card hf-card-pad"><div class="hf-skeleton mb-2" style="height:18px;width:45%"></div>' +
      '<div class="hf-skeleton" style="height:38px"></div></div></div>';
    HF.get('/api/insights').then(function (list) {
      render(list || []);
      if (showToast) HF.toast('Insights refreshed from your latest history.', 'info');
    }).catch(function (error) {
      HF.toast(error.message, 'error');
      document.getElementById('insightList').innerHTML =
        '<div class="col-12"><div class="hf-card">' +
        HF.emptyState('bi-exclamation-triangle', 'Could not load insights', error.message, '') + '</div></div>';
    });
  }

  function render(list) {
    const box = document.getElementById('insightList');
    if (!list.length) {
      box.innerHTML = '<div class="col-12"><div class="hf-card">' +
        HF.emptyState('bi-lightbulb', 'No insights yet',
          'Track a few days of habits and HabitFlow will start spotting patterns for you.', '') + '</div></div>';
      return;
    }
    box.innerHTML = list.map(function (item) {
      const tone = TONE[item.tone] || TONE.info;
      return '<div class="col-md-6"><div class="hf-card hf-card-pad h-100">' +
        '<div class="d-flex gap-3">' +
          '<div class="hf-habit-icon" style="background:' + tone.bg + ';color:var(--hf-purple-dark)">' +
            '<i class="bi ' + HF.esc(item.icon || 'bi-lightbulb') + '"></i></div>' +
          '<div><div style="font-weight:750;font-size:.92rem;margin-bottom:.25rem">' + HF.esc(item.title) + '</div>' +
          '<p style="font-size:.82rem;color:var(--hf-text-soft);line-height:1.6;margin:0">' + HF.esc(item.message) + '</p></div>' +
        '</div>' +
        '<div class="mt-3"><span class="hf-chip ' + tone.icon + '">' + HF.esc(item.type) + '</span></div>' +
        '</div></div>';
    }).join('');
  }
});
