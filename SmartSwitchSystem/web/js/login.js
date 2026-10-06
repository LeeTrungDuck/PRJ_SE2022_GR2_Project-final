/* Progressive enhancement: the login form remains usable without JavaScript. */
(function () {
    'use strict';
    const password = document.getElementById('passWord');
    const toggle = document.getElementById('login-password-toggle');
    if (!password || !toggle) { return; }
    toggle.hidden = false;
    toggle.addEventListener('click', () => {
        const visible = password.type === 'password';
        password.type = visible ? 'text' : 'password';
        toggle.setAttribute('aria-pressed', String(visible));
        toggle.setAttribute('aria-label', visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
        toggle.querySelector('i').className = visible ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    });
}());
