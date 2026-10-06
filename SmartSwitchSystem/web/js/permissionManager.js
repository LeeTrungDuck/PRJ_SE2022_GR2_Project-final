/* UI-only permission matrix. No requests or changes are made to the database. */
(function () {
    'use strict';

    const devices = [
        { id: 'esp32-01', name: 'Nhóm ESP32-01', ip: '192.168.1.101', location: 'Khu vực Phòng Khách', mac: '24:6F:28:AB:10:01', switches: [
            { id: 'sw-01-16', name: 'Đèn Chiếu Sáng Chính', gpio: 16, description: 'Tải công suất: 65W • Relay Ch.1', icon: 'fa-lightbulb' },
            { id: 'sw-01-17', name: 'Quạt Thông Gió', gpio: 17, description: 'Tải động cơ: 45W • Relay Ch.2', icon: 'fa-fan' },
            { id: 'sw-01-23', name: 'Ổ Cắm Phụ Tải', gpio: 23, description: 'Phụ tải thiết bị • Relay Ch.3', icon: 'fa-plug' }
        ] },
        { id: 'esp32-02', name: 'Nhóm ESP32-02', ip: '192.168.1.102', location: 'Phòng Thí Nghiệm', mac: '24:6F:28:AB:10:02', switches: [
            { id: 'sw-02-18', name: 'Máy Bơm Làm Mát', gpio: 18, description: 'Bơm tuần hoàn dung môi • Relay Ch.1', icon: 'fa-droplet' },
            { id: 'sw-02-19', name: 'Còi Báo Động Khẩn Cấp', gpio: 19, description: 'Cảnh báo rò rỉ khí độc • Relay Ch.2', icon: 'fa-bell' }
        ] },
        { id: 'esp32-03', name: 'Nhóm ESP32-03', ip: '192.168.1.103', location: 'Kho Thiết Bị Dự Phòng', mac: '24:6F:28:AB:10:03', switches: [
            { id: 'sw-03-21', name: 'Đèn Khử Trùng UV', gpio: 21, description: 'Hệ khử khuẩn buồng chứa • Relay Ch.1', icon: 'fa-spray-can-sparkles' },
            { id: 'sw-03-22', name: 'Van Cấp Nước Bồn Dự Bị', gpio: 22, description: 'Solenoid 24V DC • Relay Ch.2', icon: 'fa-faucet' }
        ] }
    ];
    const viewers = [
        { id: 'viewer_khu_a', name: 'Nguyễn Văn Bình', description: 'Phụ trách khu nhà A', initials: 'NB', grants: { 'sw-01-16': [true, true], 'sw-01-17': [true, false], 'sw-02-18': [true, false] } },
        { id: 'viewer_khach', name: 'Trần Văn Hùng', description: 'Quan sát viên khách sạn', initials: 'TH', grants: { 'sw-01-16': [true, false] } },
        { id: 'viewer_giamsat_lab', name: 'Phan Minh Tuấn', description: 'Nghiên cứu viên Lab', initials: 'PM', grants: {} },
        { id: 'viewer_kho', name: 'Lê Thị Hoa', description: 'Thủ kho thiết bị', initials: 'LH', grants: { 'sw-03-21': [true, true], 'sw-03-22': [true, false] } }
    ];
    const switches = devices.reduce((all, device) => all.concat(device.switches), []);
    const saved = new Map();
    const drafts = new Map();
    let activeViewerId = viewers[0].id;
    let toastTimer;
    const byId = (id) => document.getElementById(id);
    const viewerList = byId('pm-viewer-list');
    const groups = byId('pm-device-groups');
    const revokeDialog = byId('pm-revoke-dialog');

    function copyPermissions(permissions) {
        const result = {};
        switches.forEach((item) => {
            result[item.id] = { canView: permissions[item.id].canView, canControl: permissions[item.id].canControl };
        });
        return result;
    }

    viewers.forEach((viewer) => {
        const permissions = {};
        switches.forEach((item) => {
            const grant = viewer.grants[item.id] || [false, false];
            permissions[item.id] = { canView: grant[0] || grant[1], canControl: grant[1] };
        });
        saved.set(viewer.id, permissions);
        drafts.set(viewer.id, copyPermissions(permissions));
    });

    function escapeHtml(value) {
        return String(value).replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character]));
    }

    function normalize(value) {
        return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd').toLowerCase();
    }

    function isDirty(viewerId) {
        return switches.some((item) => {
            const current = drafts.get(viewerId)[item.id];
            const previous = saved.get(viewerId)[item.id];
            return current.canView !== previous.canView || current.canControl !== previous.canControl;
        });
    }

    function countPermissions(viewerId) {
        const permissions = drafts.get(viewerId);
        return switches.reduce((counts, item) => {
            counts.view += Number(permissions[item.id].canView);
            counts.control += Number(permissions[item.id].canControl);
            return counts;
        }, { view: 0, control: 0 });
    }

    function renderViewers() {
        const search = normalize(byId('pm-search').value.trim());
        const visible = viewers.filter((viewer) => normalize(viewer.name + ' ' + viewer.id).includes(search));
        viewerList.innerHTML = visible.map((viewer) => {
            const counts = countPermissions(viewer.id);
            const selected = viewer.id === activeViewerId;
            return '<button type="button" class="pm-viewer-card" data-viewer-id="' + viewer.id + '" aria-pressed="' + selected + '" aria-label="Cấu hình quyền cho ' + escapeHtml(viewer.name) + '">' +
                '<span class="pm-viewer-identity"><span class="pm-viewer-avatar" aria-hidden="true">' + viewer.initials + '</span><span class="pm-viewer-info"><span class="pm-viewer-name">' + escapeHtml(viewer.name) + '</span><span class="pm-viewer-handle">' + viewer.id + '</span></span><i class="fa-solid ' + (selected ? 'fa-shield-halved' : 'fa-eye') + ' pm-viewer-indicator" aria-hidden="true"></i></span>' +
                '<span class="pm-viewer-scope"><span>Phạm vi quyền:</span><strong>Đã cấp: ' + counts.view + '/' + switches.length + ' switch</strong></span>' +
                '<progress class="pm-progress" value="' + counts.view + '" max="' + switches.length + '" aria-label="Số switch được xem của ' + escapeHtml(viewer.name) + '"></progress>' +
                (isDirty(viewer.id) ? '<span class="pm-viewer-dirty">● Có thay đổi chưa lưu</span>' : '') + '</button>';
        }).join('');
        byId('pm-account-count').textContent = viewers.length + ' TÀI KHOẢN';
        byId('pm-search-empty').hidden = visible.length > 0;
        byId('pm-search-result').textContent = 'Tìm thấy ' + visible.length + ' trong ' + viewers.length + ' tài khoản Viewer.';
    }

    function switchMarkup(item) {
        const permission = drafts.get(activeViewerId)[item.id];
        const state = permission.canControl ? 'control' : (permission.canView ? 'view' : 'hidden');
        return '<div class="pm-switch-row" data-switch-id="' + item.id + '" data-state="' + state + '">' +
            '<div class="pm-switch-identity"><span class="pm-switch-icon"><i class="fa-solid ' + item.icon + '" aria-hidden="true"></i></span><div><div class="pm-switch-title"><h4>' + escapeHtml(item.name) + '</h4><span class="pm-switch-gpio">GPIO ' + item.gpio + '</span></div><p class="pm-switch-description">' + escapeHtml(item.description) + '</p></div></div>' +
            '<div class="pm-switch-permissions"><label><input type="checkbox" data-switch-id="' + item.id + '" data-permission="canView" aria-label="Quyền xem: ' + escapeHtml(item.name) + '"' + (permission.canView ? ' checked' : '') + '><span>canView</span></label>' +
            '<label><input type="checkbox" data-switch-id="' + item.id + '" data-permission="canControl" aria-label="Quyền điều khiển: ' + escapeHtml(item.name) + '"' + (permission.canControl ? ' checked' : '') + '><span>canControl</span></label></div></div>';
    }

    function renderGroups() {
        groups.innerHTML = devices.map((device) => '<section class="pm-device-group" aria-labelledby="pm-' + device.id + '-title">' +
            '<header class="pm-device-heading"><div class="pm-device-identity"><i class="fa-solid fa-microchip" aria-hidden="true"></i><div><div class="pm-device-title"><h3 id="pm-' + device.id + '-title">' + device.name + '</h3><span class="pm-device-ip">IP: ' + device.ip + '</span></div><p class="pm-device-description">' + device.location + ' • MAC: ' + device.mac + '</p></div></div>' +
            '<div class="pm-device-badges"><span class="pm-device-count">' + device.switches.length + ' SWITCH</span><span class="pm-device-online" title="Trạng thái thiết bị minh họa">ONLINE</span></div></header>' +
            '<div class="pm-switch-list">' + device.switches.map(switchMarkup).join('') + '</div></section>').join('');
    }

    function updateSaveState() {
        const dirty = isDirty(activeViewerId);
        const counts = countPermissions(activeViewerId);
        byId('pm-save').disabled = !dirty;
        byId('pm-reset').disabled = !dirty;
        byId('pm-revoke-all').disabled = counts.view === 0 && counts.control === 0;
        byId('pm-grant-view').disabled = counts.view === switches.length;
        byId('pm-save-state').textContent = dirty ? 'Có thay đổi chưa lưu' : 'Không có thay đổi chưa lưu';
        byId('pm-save-state').classList.toggle('is-dirty', dirty);
        byId('pm-permission-summary').textContent = 'Được xem: ' + counts.view + '/' + switches.length + ' switch • Được điều khiển: ' + counts.control + '/' + switches.length + ' switch';
    }

    function renderActiveViewer() {
        const viewer = viewers.find((item) => item.id === activeViewerId);
        byId('pm-target-name').textContent = viewer.name;
        byId('pm-target-handle').textContent = viewer.id;
        byId('pm-target-description').textContent = viewer.description;
        renderViewers();
        renderGroups();
        updateSaveState();
    }

    function showToast(message) {
        clearTimeout(toastTimer);
        byId('pm-toast-message').textContent = message;
        byId('pm-toast').hidden = false;
        toastTimer = setTimeout(() => { byId('pm-toast').hidden = true; }, 5000);
    }

    function focusTargetHeading() {
        byId('pm-target-name').setAttribute('tabindex', '-1');
        byId('pm-target-name').focus();
    }

    viewerList.addEventListener('click', (event) => {
        const button = event.target.closest('button[data-viewer-id]');
        if (!button || !drafts.has(button.dataset.viewerId)) { return; }
        activeViewerId = button.dataset.viewerId;
        renderActiveViewer();
        const selected = viewerList.querySelector('[data-viewer-id="' + activeViewerId + '"]');
        if (selected) { selected.focus(); }
    });

    groups.addEventListener('change', (event) => {
        const input = event.target;
        if (!input.matches('input[data-permission]')) { return; }
        const permission = drafts.get(activeViewerId)[input.dataset.switchId];
        if (!permission) { return; }
        if (input.dataset.permission === 'canControl') {
            permission.canControl = input.checked;
            if (input.checked) { permission.canView = true; }
        } else if (input.dataset.permission === 'canView') {
            permission.canView = input.checked;
            if (!input.checked) { permission.canControl = false; }
        } else { return; }
        // Keep the existing row and keyboard focus when toggling either checkbox.
        const row = input.closest('.pm-switch-row');
        row.querySelector('[data-permission="canView"]').checked = permission.canView;
        row.querySelector('[data-permission="canControl"]').checked = permission.canControl;
        row.dataset.state = permission.canControl ? 'control' : (permission.canView ? 'view' : 'hidden');
        renderViewers();
        updateSaveState();
    });

    byId('pm-search').addEventListener('input', renderViewers);
    byId('pm-clear-search').addEventListener('click', () => {
        byId('pm-search').value = '';
        renderViewers();
        byId('pm-search').focus();
    });
    byId('pm-grant-view').addEventListener('click', () => {
        switches.forEach((item) => { drafts.get(activeViewerId)[item.id].canView = true; });
        renderActiveViewer();
        showToast('Đã chọn quyền xem cho toàn bộ switch. Nhấn Lưu Phân Quyền để lưu trong phiên xem.');
        focusTargetHeading();
    });
    byId('pm-reset').addEventListener('click', () => {
        drafts.set(activeViewerId, copyPermissions(saved.get(activeViewerId)));
        renderActiveViewer();
        showToast('Đã khôi phục phân quyền đã lưu của ' + activeViewerId + '.');
        focusTargetHeading();
    });
    byId('pm-save').addEventListener('click', () => {
        if (!isDirty(activeViewerId)) { return; }
        saved.set(activeViewerId, copyPermissions(drafts.get(activeViewerId)));
        renderViewers();
        updateSaveState();
        showToast('Đã lưu phân quyền của ' + activeViewerId + ' trong phiên xem UI.');
        focusTargetHeading();
    });
    byId('pm-revoke-all').addEventListener('click', () => {
        byId('pm-revoke-description').textContent = 'Bỏ quyền xem và điều khiển trên toàn bộ ' + switches.length + ' switch của ' + activeViewerId + '? Thay đổi có thể hoàn tác trước khi lưu.';
        revokeDialog.showModal();
        byId('pm-cancel-revoke').focus();
    });
    ['pm-close-revoke', 'pm-cancel-revoke'].forEach((id) => { byId(id).addEventListener('click', () => revokeDialog.close()); });
    revokeDialog.addEventListener('close', () => { byId('pm-revoke-all').focus(); });
    byId('pm-revoke-form').addEventListener('submit', (event) => {
        event.preventDefault();
        switches.forEach((item) => {
            drafts.get(activeViewerId)[item.id].canView = false;
            drafts.get(activeViewerId)[item.id].canControl = false;
        });
        revokeDialog.close();
        renderActiveViewer();
        byId('pm-grant-view').focus();
        showToast('Đã bỏ toàn bộ quyền trong bản chỉnh sửa. Nhấn Lưu Phân Quyền để lưu trong phiên xem.');
    });
    byId('pm-toast-close').addEventListener('click', () => { byId('pm-toast').hidden = true; clearTimeout(toastTimer); });
    document.querySelectorAll('.sidebar .nav-pills a').forEach((link) => {
        link.setAttribute('aria-label', link.textContent.trim());
        link.title = link.textContent.trim();
    });

    renderActiveViewer();
}());
