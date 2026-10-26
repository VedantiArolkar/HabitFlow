/* Profile & settings - GET/PUT /api/profile, PUT /api/profile/password */
document.addEventListener('DOMContentLoaded', function () {
  const profileForm = document.getElementById('profileForm');
  const passwordForm = document.getElementById('passwordForm');

  loadProfile();

  profileForm.addEventListener('submit', function (event) {
    event.preventDefault();
    const alertBox = document.getElementById('profileAlert');
    alertBox.classList.add('d-none');
    const name = document.getElementById('profileName').value.trim();
    const email = document.getElementById('profileEmail').value.trim();
    if (name.length < 2) return fail(alertBox, 'Your name must be at least 2 characters.');
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail(alertBox, 'Please enter a valid email address.');

    const button = profileForm.querySelector('button[type="submit"]');
    button.disabled = true;
    HF.put('/api/profile', {
      fullName: name,
      email: email,
      timezone: document.getElementById('profileTimezone').value.trim() || 'UTC'
    }).then(function (user) {
      button.disabled = false;
      paint(user);
      HF.toast('Profile updated.', 'success');
      const sidebarName = document.querySelector('[data-user-name]');
      if (sidebarName) sidebarName.textContent = user.fullName;
    }).catch(function (error) {
      button.disabled = false;
      fail(alertBox, error.message);
    });
  });

  passwordForm.addEventListener('submit', function (event) {
    event.preventDefault();
    const alertBox = document.getElementById('passwordAlert');
    alertBox.classList.add('d-none');
    const current = document.getElementById('currentPassword').value;
    const next = document.getElementById('newPassword').value;
    const confirm = document.getElementById('confirmNewPassword').value;

    if (next.length < 8) return fail(alertBox, 'Your new password must be at least 8 characters.');
    if (!/[A-Za-z]/.test(next) || !/[0-9]/.test(next)) {
      return fail(alertBox, 'Use at least one letter and one number.');
    }
    if (next !== confirm) return fail(alertBox, 'The new passwords do not match.');

    const button = passwordForm.querySelector('button[type="submit"]');
    button.disabled = true;
    HF.put('/api/profile/password', { currentPassword: current, newPassword: next })
      .then(function () {
        HF.toast('Password changed. Please sign in again.', 'success');
        setTimeout(function () { window.location.href = '/login'; }, 1200);
      })
      .catch(function (error) {
        button.disabled = false;
        fail(alertBox, error.message);
      });
  });

  document.getElementById('logoutBtn') && document.getElementById('logoutBtn')
    .addEventListener('click', function (event) {
      event.preventDefault();
      HF.post('/api/auth/logout', {}).then(function () { window.location.href = '/login'; });
    });

  function loadProfile() {
    HF.get('/api/profile').then(paint).catch(function (error) {
      HF.toast(error.message, 'error');
    });
  }

  function paint(user) {
    document.getElementById('settingsName').textContent = user.fullName;
    document.getElementById('settingsEmail').textContent = user.email;
    document.getElementById('settingsAvatar').textContent = HF.initials(user.fullName);
    document.getElementById('settingsRole').textContent =
      user.role === 'ROLE_ADMIN' ? 'Administrator' : 'Member';
    document.getElementById('settingsSince').textContent = HF.fmtDate(user.memberSince, 'long');
    document.getElementById('profileName').value = user.fullName;
    document.getElementById('profileEmail').value = user.email;
    document.getElementById('profileTimezone').value = user.timezone || 'UTC';
  }

  function fail(box, message) {
    box.textContent = message;
    box.classList.remove('d-none');
  }
});
