/* UI-only data. Replace this boundary with a controller/API when backend work begins. */
(function () {
    'use strict';

    const roles = {
        ADMIN: { label: 'Admin', badge: 'ADMIN', className: 'admin', icon: 'fa-shield-halved' },
        OPERATION: { label: 'Operator', badge: 'OPERATOR', className: 'operator', icon: 'fa-sliders' },
        VIEWER: { label: 'Viewer', badge: 'VIEWER', className: 'viewer', icon: 'fa-eye' }
    };
    let users = [
        { id: 1, fullName: 'Nguyễn Văn An', userName: 'an.nguyen', email: 'an.nguyen@esp32hub.io', role: 'ADMIN', active: true, createdAt: '2023-11-01', superAdmin: true },
        { id: 2, fullName: 'Trần Đình Dũng', userName: 'dung.tran', email: 'dung.tran@esp32hub.io', role: 'ADMIN', active: true, createdAt: '2024-01-10' },
        { id: 3, fullName: 'Lê Quang Huy', userName: 'huy.le', email: 'huy.le@iot-plant.vn', role: 'OPERATION', active: true, createdAt: '2024-02-14' },
        { id: 4, fullName: 'Phạm Thị Mai', userName: 'mai.pham', email: 'mai.pham@factory-net.org', role: 'OPERATION', active: true, createdAt: '2024-03-01' },
        { id: 5, fullName: 'Hoàng Văn Bách', userName: 'bach.hoang', email: 'bach.hoang@monitor.local', role: 'VIEWER', active: true, createdAt: '2024-03-12' },
        { id: 6, fullName: 'Vũ Đức Trọng', userName: 'trong.vu', email: 'trong.vu@thirdparty.com', role: 'VIEWER', active: false, createdAt: '2023-12-19' }
    ];
    const pageSize = 6;
    let page = 1;
    let nextId = 7;
    let pendingAction = null;
    let toastTimer;
    let actionTrigger;
    const byId = (id) => document.getElementById(id);
    const tableBody = byId('um-user-list');
    const userDialog = byId('um-user-dialog');
    const actionDialog = byId('um-action-dialog');
    const userForm = byId('um-user-form');
    const password = byId('um-password');

    function resetPasswordVisibility(input, button, label) {
        input.type = 'password';
        button.setAttribute('aria-label', 'Hiện ' + label);
        button.setAttribute('aria-pressed', 'false');
        button.querySelector('i').className = 'fa-regular fa-eye';
    }

    function togglePasswordVisibility(input, button, label) {
        const visible = input.type === 'password';
        input.type = visible ? 'text' : 'password';
        button.setAttribute('aria-label', (visible ? 'Ẩn ' : 'Hiện ') + label);
        button.setAttribute('aria-pressed', String(visible));
        button.querySelector('i').className = visible ? 'fa-regular fa-eye-slash' : 'fa-regular fa-eye';
    }

    // User content is escaped before insertion; toast/dialog content uses textContent.
    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
    }

    function initials(name) {
        return name.trim().split(/\s+/).slice(-2).map((word) => word.charAt(0)).join('').toUpperCase();
    }

    function iconButton(action, icon, label, user, disabled) {
        return '<button type="button" class="um-icon-button" data-action="' + action + '" data-id="' + user.id + '" title="' + escapeHtml(label) + '" aria-label="' + escapeHtml(label + ': ' + user.fullName) + '"' + (disabled ? ' disabled' : '') + '><i class="fa-solid ' + icon + '" aria-hidden="true"></i></button>';
    }

    function rowMarkup(user) {
        const role = roles[user.role];
        const name = escapeHtml(user.fullName);
        const options = Object.keys(roles).map((key) => '<option value="' + key + '"' + (key === user.role ? ' selected' : '') + '>' + roles[key].label + '</option>').join('');
        const marker = user.superAdmin ? '<span class="um-mini-badge">SUPER</span>' : (!user.active ? '<span class="um-mini-badge um-mini-badge-locked">LOCKED</span>' : '');
        return '<tr' + (!user.active ? ' class="um-user-locked"' : '') + '>' +
            '<td><div class="um-user-identity"><span class="um-avatar um-avatar-' + role.className + '" aria-hidden="true">' + escapeHtml(initials(user.fullName)) + '</span><div><div class="um-user-name"><strong>' + name + '</strong>' + marker + '</div><span class="um-username">@' + escapeHtml(user.userName) + '</span><span class="um-email">' + escapeHtml(user.email) + '</span></div></div></td>' +
            '<td><span class="um-role-badge um-role-' + role.className + '"><i class="fa-solid ' + role.icon + '" aria-hidden="true"></i>' + role.badge + '</span></td>' +
            '<td><span class="um-status-badge' + (!user.active ? ' um-status-locked' : '') + '">' + (user.active ? 'HOẠT ĐỘNG' : 'ĐÃ KHÓA') + '</span></td>' +
            '<td><time class="um-created-date" datetime="' + user.createdAt + '">' + user.createdAt + '</time></td>' +
            '<td><div class="um-row-actions"><select class="um-role-select" data-id="' + user.id + '" aria-label="Vai trò của ' + name + '"' + (user.superAdmin ? ' disabled title="Giữ vai trò của quản trị viên hệ thống"' : '') + '>' + options + '</select>' +
            iconButton('password', 'fa-key', 'Đổi mật khẩu', user, false) +
            iconButton('lock', user.active ? 'fa-lock-open' : 'fa-lock', user.active ? 'Khóa tài khoản' : 'Mở khóa tài khoản', user, user.superAdmin) +
            iconButton('delete', 'fa-trash-can', 'Xóa tài khoản', user, user.superAdmin) + '</div></td></tr>';
    }

    function render() {
        const totalPages = Math.max(1, Math.ceil(users.length / pageSize));
        page = Math.min(page, totalPages);
        const start = (page - 1) * pageSize;
        tableBody.innerHTML = users.slice(start, start + pageSize).map(rowMarkup).join('');
        byId('um-empty').hidden = users.length !== 0;
        byId('um-previous').disabled = page === 1;
        byId('um-next').disabled = page === totalPages;
        byId('um-page-number').textContent = page + ' / ' + totalPages;
        byId('um-result-count').textContent = users.length ? 'Hiển thị ' + (start + 1) + '–' + Math.min(start + pageSize, users.length) + ' trong ' + users.length + ' tài khoản' : '0 tài khoản';
    }

    function showToast(message) {
        clearTimeout(toastTimer);
        byId('um-toast-message').textContent = message;
        byId('um-toast').hidden = false;
        toastTimer = setTimeout(() => { byId('um-toast').hidden = true; }, 5000);
    }

    function localDate() {
        const date = new Date();
        return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
    }

    function openAction(user, action, trigger, newRole) {
        pendingAction = { id: user.id, action: action, newRole: newRole };
        actionTrigger = trigger;
        byId('um-action-form').reset();
        resetPasswordVisibility(password, byId('um-password-toggle'), 'mật khẩu');
        password.required = action === 'password';
        password.disabled = action !== 'password';
        byId('um-password-field').hidden = action !== 'password';
        const title = byId('um-action-title');
        const description = byId('um-action-description');
        const confirm = byId('um-action-confirm');
        confirm.className = 'um-button ' + (action === 'delete' ? 'um-button-danger' : 'um-button-primary');
        if (action === 'password') {
            title.textContent = 'Đổi mật khẩu';
            description.textContent = 'Thiết lập mật khẩu mới cho @' + user.userName + ' trong bản xem UI.';
            confirm.textContent = 'Đổi mật khẩu';
        } else if (action === 'delete') {
            title.textContent = 'Xóa tài khoản?';
            description.textContent = 'Tài khoản ' + user.fullName + ' (@' + user.userName + ') sẽ được xóa khỏi danh sách minh họa. Tải lại trang để khôi phục dữ liệu ban đầu.';
            confirm.textContent = 'Xóa tài khoản';
        } else if (action === 'role') {
            title.textContent = 'Thay đổi vai trò?';
            description.textContent = 'Chuyển vai trò của ' + user.fullName + ' từ ' + roles[user.role].label + ' sang ' + roles[newRole].label + ' trong bản xem UI.';
            confirm.textContent = 'Lưu vai trò';
        } else {
            title.textContent = user.active ? 'Khóa tài khoản?' : 'Mở khóa tài khoản?';
            description.textContent = 'Chuyển trạng thái của ' + user.fullName + ' sang ' + (user.active ? 'Đã khóa' : 'Hoạt động') + ' trong bản xem UI.';
            confirm.textContent = user.active ? 'Khóa tài khoản' : 'Mở khóa tài khoản';
        }
        actionDialog.showModal();
        if (action === 'password') { password.focus(); }
        else { actionDialog.querySelector('[data-close-dialog]').focus(); }
    }

    byId('um-add-user').addEventListener('click', () => {
        userForm.reset();
        resetPasswordVisibility(byId('um-initial-password'), byId('um-initial-password-toggle'), 'mật khẩu ban đầu');
        byId('um-form-error').hidden = true;
        byId('um-username').setCustomValidity('');
        userDialog.showModal();
        byId('um-full-name').focus();
    });

    document.querySelectorAll('[data-close-dialog]').forEach((button) => {
        button.addEventListener('click', () => button.closest('dialog').close());
    });
    userDialog.addEventListener('close', () => {
        userForm.reset();
        byId('um-add-user').focus();
    });
    actionDialog.addEventListener('close', () => {
        password.value = '';
        pendingAction = null;
        if (actionTrigger && actionTrigger.isConnected) { actionTrigger.focus(); }
    });

    byId('um-username').addEventListener('input', () => {
        byId('um-username').setCustomValidity('');
        byId('um-form-error').hidden = true;
    });
    userForm.addEventListener('submit', (event) => {
        event.preventDefault();
        const fullName = byId('um-full-name').value.trim();
        const userName = byId('um-username').value.trim();
        const email = byId('um-email').value.trim();
        if (!fullName || !userName || !email) {
            byId('um-form-error').textContent = 'Vui lòng nhập đầy đủ họ tên, tên đăng nhập và email.';
            byId('um-form-error').hidden = false;
            return;
        }
        if (users.some((user) => user.userName.toLowerCase() === userName.toLowerCase())) {
            byId('um-username').setCustomValidity('Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác.');
            byId('um-form-error').textContent = 'Tên đăng nhập đã tồn tại. Vui lòng chọn tên khác.';
            byId('um-form-error').hidden = false;
            byId('um-username').reportValidity();
            return;
        }
        users.push({ id: nextId++, fullName: fullName, userName: userName, email: email, role: userForm.querySelector('input[name="role"]:checked').value, active: true, createdAt: localDate() });
        page = Math.ceil(users.length / pageSize);
        render();
        userDialog.close();
        showToast('Đã thêm tài khoản @' + userName + ' vào danh sách minh họa.');
    });

    tableBody.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-action]');
        if (!button || button.disabled) { return; }
        const user = users.find((entry) => entry.id === Number(button.dataset.id));
        if (user) { openAction(user, button.dataset.action, button); }
    });
    tableBody.addEventListener('change', (event) => {
        const select = event.target.closest('.um-role-select');
        if (!select || select.disabled) { return; }
        const user = users.find((entry) => entry.id === Number(select.dataset.id));
        const nextRole = select.value;
        select.value = user.role;
        if (nextRole !== user.role && roles[nextRole]) { openAction(user, 'role', select, nextRole); }
    });
    byId('um-action-form').addEventListener('submit', (event) => {
        event.preventDefault();
        if (!pendingAction) { return; }
        const user = users.find((entry) => entry.id === pendingAction.id);
        if (!user || (user.superAdmin && pendingAction.action !== 'password')) { return; }
        let message;
        if (pendingAction.action === 'delete') {
            users = users.filter((entry) => entry.id !== user.id);
            message = 'Đã xóa @' + user.userName + ' khỏi danh sách minh họa.';
        } else if (pendingAction.action === 'lock') {
            user.active = !user.active;
            message = 'Đã ' + (user.active ? 'mở khóa' : 'khóa') + ' tài khoản @' + user.userName + '.';
        } else if (pendingAction.action === 'role') {
            user.role = pendingAction.newRole;
            message = 'Đã đổi vai trò của @' + user.userName + ' thành ' + roles[user.role].label + '.';
        } else {
            message = 'Đã mô phỏng đổi mật khẩu cho @' + user.userName + '. Mật khẩu không được lưu.';
        }
        const targetId = user.id;
        const targetAction = pendingAction.action;
        actionDialog.close();
        render();
        const focusTarget = targetAction === 'role' ? tableBody.querySelector('select[data-id="' + targetId + '"]') : tableBody.querySelector('button[data-id="' + targetId + '"][data-action="' + targetAction + '"]');
        (focusTarget || byId('um-add-user')).focus();
        showToast(message);
    });
    byId('um-password-toggle').addEventListener('click', () => {
        togglePasswordVisibility(password, byId('um-password-toggle'), 'mật khẩu');
    });
    byId('um-initial-password-toggle').addEventListener('click', () => {
        togglePasswordVisibility(byId('um-initial-password'), byId('um-initial-password-toggle'), 'mật khẩu ban đầu');
    });
    byId('um-previous').addEventListener('click', () => { if (page > 1) { page--; render(); } });
    byId('um-next').addEventListener('click', () => { if (page < Math.ceil(users.length / pageSize)) { page++; render(); } });
    byId('um-toast-close').addEventListener('click', () => { byId('um-toast').hidden = true; clearTimeout(toastTimer); });

    // Icon navigation retains accessible labels when its text is collapsed on mobile.
    document.querySelectorAll('.sidebar .nav-pills a').forEach((link) => { link.setAttribute('aria-label', link.textContent.trim()); link.title = link.textContent.trim(); });
    render();
}());
