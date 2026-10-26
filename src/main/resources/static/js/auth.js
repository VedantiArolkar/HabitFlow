/* Login / registration pages */
document.addEventListener('DOMContentLoaded', function () {
  const loginForm = document.getElementById('loginForm');
  if (loginForm) {
    const alertBox = document.getElementById('authAlert');
    loginForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      const button = loginForm.querySelector('button[type="submit"]');
      const email = loginForm.email.value.trim();
      const password = loginForm.password.value;
      if (!email || !password) {
        showAlert(alertBox, 'Please enter both your email and password.');
        return;
      }
      button.disabled = true;
      button.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Signing in…';
      try {
        await signIn(email, password);
        window.location.assign('/dashboard');
      } catch (error) {
        showAlert(alertBox, error.message || 'Incorrect email or password.');
        button.disabled = false;
        button.textContent = 'Sign in';
      }
    });
  }

  const registerForm = document.getElementById('registerForm');
  if (registerForm) {
    const alertBox = document.getElementById('authAlert');
    registerForm.addEventListener('submit', async function (event) {
      event.preventDefault();
      const button = registerForm.querySelector('button[type="submit"]');
      const fullName = registerForm.fullName.value.trim();
      const email = registerForm.email.value.trim();
      const password = registerForm.password.value;
      const confirm = registerForm.confirmPassword.value;

      if (fullName.length < 2) return showAlert(alertBox, 'Please enter your full name.');
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return showAlert(alertBox, 'Please enter a valid email address.');
      if (password.length < 8) return showAlert(alertBox, 'Your password must be at least 8 characters.');
      if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) {
        return showAlert(alertBox, 'Your password needs at least one letter and one number.');
      }
      if (password !== confirm) return showAlert(alertBox, 'The two passwords do not match.');

      button.disabled = true;
      button.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Creating account…';
      try {
        await HF.post('/api/auth/register', { fullName: fullName, email: email, password: password });
        await signIn(email, password);
        window.location.assign('/dashboard');
      } catch (error) {
        showAlert(alertBox, error.message || 'We could not create your account.');
        button.disabled = false;
        button.textContent = 'Create account';
      }
    });
  }
});

// Spring Security formLogin reads request parameters, so send URL-encoded data (not JSON).
async function signIn(email, password) {
  // Ensure the CSRF cookie exists before the login POST.
  await fetch('/api/auth/csrf', { method: 'GET', credentials: 'same-origin' });
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  const headers = { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8', 'Accept': 'application/json' };
  if (match) headers['X-XSRF-TOKEN'] = decodeURIComponent(match[1]);
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: headers,
    body: new URLSearchParams({ email: email, password: password }).toString()
  });
  let data = {};
  try { data = await response.json(); } catch (e) { /* response may be empty */ }
  if (!response.ok) {
    throw new Error(data.message || (response.status === 401 ? 'Incorrect email or password.' : 'Sign in failed (' + response.status + ').'));
  }
  return data;
}

function showAlert(box, message) {
  if (!box) { alert(message); return; }
  box.className = 'alert alert-danger py-2 px-3 mb-3';
  box.style.fontSize = '.84rem';
  box.textContent = message;
  box.classList.remove('d-none');
}
