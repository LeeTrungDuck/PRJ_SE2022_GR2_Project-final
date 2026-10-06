/* UI preview only. Profile data lives in memory; passwords are never saved or sent. */
(function () {
    'use strict';

    const byId = (id) => document.getElementById(id);
    const profileForm = byId('ac-profile-form');
    const passwordForm = byId('ac-password-form');
    const otpForm = byId('ac-otp-form');
    const otpDialog = byId('ac-otp-dialog');
    const fullName = byId('ac-fullname');
    const email = byId('ac-email');
    const phone = byId('ac-phone');
    const currentPassword = byId('ac-current-password');
    const newPassword = byId('ac-new-password');
    const confirmPassword = byId('ac-confirm-password');
    const otpCode = byId('ac-otp-code');
    const profileFields = [fullName, email, phone];
    const passwordFields = [currentPassword, newPassword, confirmPassword];
    const visibilityButtons = document.querySelectorAll('[data-password-target]');
    let profile = { fullName: fullName.value, email: email.value, phone: phone.value };
    let pendingProfile = null;
    let toastTimer;

    function showToast(message) {
        clearTimeout(toastTimer);
        byId('ac-toast-message').textContent = message;
        byId('ac-toast').hidden = false;
        toastTimer = setTimeout(() => { byId('ac-toast').hidden = true; }, 6500);
    }

    function clearError(errorId, fields) {
        const error = byId(errorId);
        error.hidden = true;
        error.textContent = '';
        fields.forEach((field) => {
            field.setCustomValidity('');
            field.removeAttribute('aria-invalid');
        });
    }

    function fail(errorId, field, message) {
        const error = byId(errorId);
        error.textContent = message;
        error.hidden = false;
        field.setCustomValidity(message);
        field.setAttribute('aria-invalid', 'true');
        field.focus();
    }

    function saveProfile(nextProfile) {
        profile = { ...nextProfile };
        byId('ac-profile-name').textContent = profile.fullName;
        const sidebarName = document.querySelector('.sidebar .user-name');
        if (sidebarName) { sidebarName.textContent = profile.fullName; }
        fullName.value = profile.fullName;
        email.value = profile.email;
        phone.value = profile.phone;
        showToast('Đã lưu thông tin minh họa trong phiên xem UI.');
    }

    profileFields.forEach((field) => {
        field.addEventListener('input', () => { clearError('ac-profile-error', profileFields); });
    });

    profileForm.addEventListener('submit', (event) => {
        event.preventDefault();
        clearError('ac-profile-error', profileFields);
        const nextProfile = { fullName: fullName.value.trim(), email: email.value.trim(), phone: phone.value.trim() };
        fullName.value = nextProfile.fullName;
        email.value = nextProfile.email;
        phone.value = nextProfile.phone;
        if (!nextProfile.fullName) {
            fail('ac-profile-error', fullName, 'Vui lòng nhập họ và tên.');
            return;
        }
        if (!nextProfile.email || !email.checkValidity()) {
            fail('ac-profile-error', email, 'Vui lòng nhập địa chỉ email hợp lệ.');
            return;
        }
        const digits = nextProfile.phone.replace(/\D/g, '');
        if (!/^[+\d\s().-]+$/.test(nextProfile.phone) || digits.length < 9 || digits.length > 15) {
            fail('ac-profile-error', phone, 'Số điện thoại cần có từ 9 đến 15 chữ số.');
            return;
        }
        if (!profileForm.reportValidity()) { return; }
        if (Object.keys(profile).every((key) => profile[key] === nextProfile[key])) {
            showToast('Thông tin chưa có thay đổi.');
            return;
        }
        if (nextProfile.email !== profile.email) {
            pendingProfile = nextProfile;
            byId('ac-pending-email').textContent = nextProfile.email;
            otpForm.reset();
            clearError('ac-otp-error', [otpCode]);
            otpDialog.showModal();
            otpCode.focus();
            return;
        }
        saveProfile(nextProfile);
    });

    otpCode.addEventListener('input', () => { clearError('ac-otp-error', [otpCode]); });
    otpForm.addEventListener('submit', (event) => {
        event.preventDefault();
        clearError('ac-otp-error', [otpCode]);
        if (!otpForm.reportValidity()) { return; }
        if (otpCode.value !== '246810') {
            fail('ac-otp-error', otpCode, 'Mã chưa đúng. Sử dụng mã minh họa 246810.');
            return;
        }
        if (pendingProfile) { saveProfile(pendingProfile); }
        otpDialog.close();
    });
    ['ac-close-otp', 'ac-cancel-otp'].forEach((id) => { byId(id).addEventListener('click', () => { otpDialog.close(); }); });
    otpDialog.addEventListener('close', () => {
        pendingProfile = null;
        otpForm.reset();
        clearError('ac-otp-error', [otpCode]);
        byId('ac-save-profile').focus();
    });

    function passwordCriteria(value) {
        return [value.length >= 10, /[A-Z]/.test(value), /[0-9]/.test(value), /[^A-Za-z0-9\s]/.test(value)];
    }

    function updateStrength() {
        const value = newPassword.value;
        const score = passwordCriteria(value).filter(Boolean).length;
        byId('ac-strength').dataset.score = String(score);
        document.querySelectorAll('.ac-strength-bars > span').forEach((bar, index) => { bar.classList.toggle('is-filled', index < score); });
        byId('ac-strength-text').textContent = !value ? 'Chưa nhập' : score === 4 ? 'Đạt yêu cầu' : score <= 1 ? 'Yếu' : 'Chưa đủ yêu cầu';
    }

    passwordFields.forEach((field) => {
        field.addEventListener('input', () => { clearError('ac-password-error', passwordFields); });
    });
    newPassword.addEventListener('input', updateStrength);

    visibilityButtons.forEach((button) => {
        const field = byId(button.dataset.passwordTarget);
        const subject = field === currentPassword ? 'mật khẩu hiện tại' : field === newPassword ? 'mật khẩu mới' : 'xác nhận mật khẩu';
        button.addEventListener('click', () => {
            const visible = field.type === 'password';
            field.type = visible ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(visible));
            button.setAttribute('aria-label', (visible ? 'Ẩn ' : 'Hiện ') + subject);
            button.querySelector('i').className = visible ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
        });
    });

    passwordForm.addEventListener('submit', (event) => {
        event.preventDefault();
        clearError('ac-password-error', passwordFields);
        if (!currentPassword.value) {
            fail('ac-password-error', currentPassword, 'Vui lòng nhập mật khẩu hiện tại để thử biểu mẫu.');
            return;
        }
        if (!passwordCriteria(newPassword.value).every(Boolean)) {
            fail('ac-password-error', newPassword, 'Mật khẩu mới cần ít nhất 10 ký tự, có chữ hoa, chữ số và ký tự đặc biệt.');
            return;
        }
        if (newPassword.value === currentPassword.value) {
            fail('ac-password-error', newPassword, 'Mật khẩu mới cần khác mật khẩu hiện tại.');
            return;
        }
        if (confirmPassword.value !== newPassword.value) {
            fail('ac-password-error', confirmPassword, 'Mật khẩu xác nhận chưa khớp.');
            return;
        }
        if (!passwordForm.reportValidity()) { return; }
        passwordForm.reset();
        visibilityButtons.forEach((button) => {
            const field = byId(button.dataset.passwordTarget);
            field.type = 'password';
            button.setAttribute('aria-pressed', 'false');
            button.setAttribute('aria-label', button.getAttribute('aria-label').replace(/^Ẩn /, 'Hiện '));
            button.querySelector('i').className = 'fa-regular fa-eye';
        });
        updateStrength();
        showToast('Đã mô phỏng đổi mật khẩu. Mật khẩu không được lưu.');
    });

    byId('ac-dismiss-toast').addEventListener('click', () => {
        clearTimeout(toastTimer);
        byId('ac-toast').hidden = true;
    });
    updateStrength();
    byId('ac-save-profile').disabled = false;
    byId('ac-update-password').disabled = false;
}());
