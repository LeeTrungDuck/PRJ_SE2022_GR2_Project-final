/* UI-only state. No database, MQTT, HTTP requests or credential storage. */
(function () {
    'use strict';

    let nodes = [
        { id: 'ESP32-CORE-A101', name: 'Node Tủ Điện 01', ip: '192.168.1.150', hostname: 'esp32-rack-a101.local', location: 'Xưởng A MSB', online: true, latency: 12 },
        { id: 'ESP32-OFFICE-02', name: 'Node Chiếu Sáng Văn Phòng', ip: '192.168.1.155', hostname: 'esp32-lighting.local', location: 'Văn Phòng', online: true, latency: 18 },
        { id: 'ESP32-PUMP-EXT04', name: 'Node Bơm Thủy Canh Nông Trại', ip: '192.168.1.188', hostname: 'esp32-farm-hydro.local', location: 'Trạm Thủy Canh', online: false, latency: null, offlineReason: 'Timeout' }
    ];
    let switches = [
        { key: 'sw-1', name: 'Đèn Chiếu Xưởng Zone A', nodeId: 'ESP32-CORE-A101', gpio: 16, wiring: 'NO', rating: '10A / 250VAC', type: 'light', boot: 'OFF', on: true, schedule: null },
        { key: 'sw-2', name: 'Quạt Thông Gió CN 1', nodeId: 'ESP32-CORE-A101', gpio: 17, wiring: 'NO', rating: '30A High Inrush', type: 'fan', boot: 'ON', on: true, schedule: null },
        { key: 'sw-3', name: 'Bơm Nước Áp Lực 02 HP', nodeId: 'ESP32-CORE-A101', gpio: 21, wiring: 'NC', rating: 'Thường Đóng', type: 'motor', boot: 'OFF', on: false, schedule: null },
        { key: 'sw-4', name: 'Ổ Cắm Máy In 3D & CNC', nodeId: 'ESP32-OFFICE-02', gpio: 22, wiring: 'NO', rating: '16A Opto', type: 'other', boot: 'OFF', on: false, schedule: null }
    ];
    const byId = (id) => document.getElementById(id);
    const nodeForm = byId('dm-node-form');
    const switchForm = byId('dm-switch-form');
    const scheduleForm = byId('dm-schedule-form');
    const pulseJobs = new Map();
    const dialogOpeners = new Map();
    let focusTargets = new Map();
    let nodeStatusFilter = 'all';
    let editingNodeId = null;
    let editingSwitchKey = null;
    let schedulingKey = null;
    let pendingConfirm = null;
    let nextSwitchKey = 5;
    let toastTimer;

    const findNode = (id) => nodes.find((node) => node.id === id);
    const findSwitch = (key) => switches.find((item) => item.key === key);
    const normalized = (value) => String(value).normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase();
    const valueOf = (id) => byId(id).value.trim();

    function element(tag, className, text) {
        const result = document.createElement(tag);
        if (className) { result.className = className; }
        if (text !== undefined) { result.textContent = text; }
        return result;
    }

    function icon(name) {
        const result = element('i', 'fa-solid ' + name);
        result.setAttribute('aria-hidden', 'true');
        return result;
    }

    function notify(message) {
        clearTimeout(toastTimer);
        byId('dm-feedback').textContent = message;
        byId('dm-toast-message').textContent = message;
        byId('dm-toast').hidden = false;
        toastTimer = setTimeout(() => { byId('dm-toast').hidden = true; }, 6500);
    }

    function actionButton(action, label, iconName, focusKey, handler, disabled) {
        const button = element('button', 'dm-icon-button');
        button.type = 'button';
        button.title = label;
        button.dataset.action = action;
        button.dataset.focusKey = focusKey;
        button.setAttribute('aria-label', label);
        button.disabled = Boolean(disabled);
        button.appendChild(icon(iconName));
        button.addEventListener('click', handler);
        focusTargets.set(focusKey, button);
        return button;
    }

    function fillSelect(id, options, leading, preserve) {
        const select = byId(id);
        const previous = select.value;
        const entries = leading ? [leading].concat(options) : options;
        select.replaceChildren(...entries.map((entry) => {
            const option = element('option', '', entry.label);
            option.value = entry.value;
            return option;
        }));
        select.value = preserve && entries.some((entry) => entry.value === previous) ? previous : entries.length ? entries[0].value : '';
    }

    function refreshOptions() {
        const locations = Array.from(new Set(nodes.map((node) => node.location))).sort((a, b) => a.localeCompare(b, 'vi'));
        fillSelect('dm-location', locations.map((location) => ({ value: location, label: location })), { value: 'all', label: 'Tất cả vị trí xưởng' }, true);
        fillSelect('dm-parent-filter', nodes.map((node) => ({ value: node.id, label: node.id })), { value: 'all', label: 'ESP32 cha: Tất cả' }, true);
    }

    function emptyRow(body, columns, message, filtered, clear) {
        const row = element('tr');
        const cell = element('td', 'dm-empty-cell');
        cell.colSpan = columns;
        cell.appendChild(element('p', '', message));
        if (filtered) {
            const button = element('button', 'um-button um-button-secondary', 'Xóa bộ lọc');
            button.type = 'button';
            button.addEventListener('click', clear);
            cell.appendChild(button);
        }
        row.appendChild(cell);
        body.appendChild(row);
    }

    function clearNodeFilters() {
        byId('dm-node-search').value = '';
        byId('dm-location').value = 'all';
        nodeStatusFilter = 'all';
        render();
    }

    function clearSwitchFilters() {
        byId('dm-switch-search').value = '';
        byId('dm-parent-filter').value = 'all';
        byId('dm-type-filter').value = 'all';
        render();
    }

    function renderNodes() {
        const body = byId('dm-node-rows');
        body.replaceChildren();
        const query = normalized(valueOf('dm-node-search'));
        const location = byId('dm-location').value;
        const visible = nodes.filter((node) => (nodeStatusFilter === 'all' || node.online === (nodeStatusFilter === 'online')) && (location === 'all' || node.location === location) && normalized([node.id, node.name, node.ip, node.hostname, node.location].join(' ')).includes(query));
        byId('dm-count-all').textContent = String(nodes.length);
        byId('dm-count-online').textContent = String(nodes.filter((node) => node.online).length);
        byId('dm-count-offline').textContent = String(nodes.filter((node) => !node.online).length);
        document.querySelectorAll('[data-node-status]').forEach((button) => { button.setAttribute('aria-pressed', String(button.dataset.nodeStatus === nodeStatusFilter)); });
        visible.forEach((node) => {
            const row = element('tr');
            row.dataset.online = String(node.online);
            const identityCell = element('td');
            const identity = element('div', 'dm-item');
            const identityIcon = element('span', 'dm-item-icon');
            identityIcon.appendChild(icon(node.online ? 'fa-network-wired' : 'fa-cloud-arrow-down'));
            const info = element('div', 'dm-item-info');
            const name = element('span', 'dm-item-name', node.name);
            name.title = node.name + ' · ' + node.location;
            info.append(name, element('span', 'dm-item-subtitle', node.id));
            identity.append(identityIcon, info);
            identityCell.appendChild(identity);
            const hostCell = element('td');
            const ipRow = element('div', 'dm-ip-row');
            const copy = actionButton('copy', 'Sao chép IP ' + node.ip, 'fa-copy', 'node:' + node.id + ':copy', () => copyIp(node.ip));
            copy.classList.add('dm-copy-button');
            ipRow.append(element('span', 'dm-ip', node.ip), copy);
            hostCell.append(ipRow, element('span', 'dm-hostname', node.hostname || '—'));
            const statusCell = element('td');
            const status = element('div', 'dm-node-status ' + (node.online ? 'is-online' : 'is-offline'));
            status.append(element('span', 'dm-status-dot'), element('span', '', node.online ? 'ONLINE' : 'OFFLINE'), element('span', 'dm-latency', '(' + (node.online ? node.latency + 'ms' : node.offlineReason || 'Chưa kết nối') + ')'));
            statusCell.appendChild(status);
            const actionsCell = element('td');
            const actions = element('div', 'dm-actions');
            const busy = switches.some((item) => item.nodeId === node.id && pulseJobs.has(item.key));
            actions.append(
                actionButton('ping', 'Ping minh họa: ' + node.name, 'fa-diagram-project', 'node:' + node.id + ':ping', () => notify('Ping minh họa ' + node.ip + ': ' + (node.online ? node.latency + 'ms.' : 'Timeout. Không gửi yêu cầu mạng.'))),
                actionButton('edit', 'Cấu hình ' + node.name, 'fa-gear', 'node:' + node.id + ':edit', () => openNode(node.id)),
                actionButton('reboot', 'Khởi động lại minh họa: ' + node.name, 'fa-rotate-right', 'node:' + node.id + ':reboot', () => confirmReboot(node.id), !node.online || busy),
                actionButton('delete', 'Xóa ' + node.name, 'fa-trash-can', 'node:' + node.id + ':delete', () => confirmDeleteNode(node.id))
            );
            actionsCell.appendChild(actions);
            row.append(identityCell, hostCell, statusCell, actionsCell);
            body.appendChild(row);
        });
        if (!visible.length) { emptyRow(body, 4, nodes.length ? 'Không tìm thấy thiết bị phù hợp.' : 'Chưa có ESP32. Thêm thiết bị để bắt đầu cấu hình.', nodes.length > 0, clearNodeFilters); }
        byId('dm-node-result').textContent = 'Hiển thị ' + visible.length + ' / ' + nodes.length + ' thiết bị';
    }

    function renderSwitches() {
        const body = byId('dm-switch-rows');
        body.replaceChildren();
        const query = normalized(valueOf('dm-switch-search'));
        const parent = byId('dm-parent-filter').value;
        const type = byId('dm-type-filter').value;
        const visible = switches.filter((item) => {
            const node = findNode(item.nodeId);
            return node && (parent === 'all' || item.nodeId === parent) && (type === 'all' || item.type === type) && normalized([item.name, 'GPIO ' + item.gpio, item.nodeId, node.name, node.ip].join(' ')).includes(query);
        });
        const icons = { light: 'fa-lightbulb', fan: 'fa-fan', motor: 'fa-water', other: 'fa-plug' };
        visible.forEach((item) => {
            const node = findNode(item.nodeId);
            const busy = pulseJobs.has(item.key);
            const row = element('tr');
            const identityCell = element('td');
            const identity = element('div', 'dm-item');
            const identityIcon = element('span', 'dm-item-icon is-' + item.type);
            identityIcon.appendChild(icon(icons[item.type]));
            const info = element('div', 'dm-item-info');
            const name = element('span', 'dm-item-name', item.name);
            name.title = item.name;
            info.append(name, element('span', 'dm-item-subtitle dm-load-subtitle', 'Relay ' + item.wiring + (item.rating ? ' - ' + item.rating : '')));
            identity.append(identityIcon, info);
            identityCell.appendChild(identity);
            const parentCell = element('td');
            parentCell.append(element('span', 'dm-parent-id', node.id), element('span', 'dm-parent-ip', node.ip));
            const gpioCell = element('td');
            gpioCell.appendChild(element('span', 'dm-gpio', String(item.gpio)));
            const bootCell = element('td');
            bootCell.appendChild(element('span', 'dm-boot ' + (item.boot === 'ON' ? 'is-on' : item.boot === 'LAST' ? 'is-last' : ''), item.boot === 'LAST' ? 'Ghi nhớ (LAST)' : 'Mặc định: ' + item.boot + (item.boot === 'ON' ? ' (HIGH)' : ' (LOW)')));
            const currentCell = element('td');
            const current = element('div', 'dm-current');
            const toggle = actionButton('toggle', 'Bật/tắt ' + item.name, '', 'switch:' + item.key + ':toggle', () => toggleSwitch(item.key), !node.online || busy);
            toggle.className = 'dm-toggle';
            toggle.replaceChildren();
            toggle.setAttribute('role', 'switch');
            toggle.setAttribute('aria-checked', String(item.on));
            const track = element('span', 'dm-toggle-track');
            track.setAttribute('aria-hidden', 'true');
            track.appendChild(element('span'));
            toggle.appendChild(track);
            current.append(toggle, element('span', 'dm-current-label ' + (busy ? 'is-testing' : item.on && node.online ? 'is-on' : ''), !node.online ? 'OFFLINE' : busy ? 'TEST 1s' : item.on ? 'ON' : 'OFF'));
            currentCell.appendChild(current);
            const actionsCell = element('td');
            const actions = element('div', 'dm-actions');
            const schedule = actionButton('schedule', item.schedule ? 'Lịch ' + item.schedule.action + ': ' + new Date(item.schedule.at).toLocaleString('vi-VN') : 'Hẹn giờ ' + item.name, 'fa-clock', 'switch:' + item.key + ':schedule', () => openSchedule(item.key), busy);
            if (item.schedule) { schedule.classList.add('has-schedule'); }
            actions.append(schedule,
                actionButton('pulse', 'Test xung minh họa 1 giây: GPIO ' + item.gpio, 'fa-bolt', 'switch:' + item.key + ':pulse', () => testPulse(item.key), !node.online || busy),
                actionButton('edit', 'Sửa cấu hình ' + item.name, 'fa-pen', 'switch:' + item.key + ':edit', () => openSwitch(item.key), busy),
                actionButton('delete', 'Gỡ ' + item.name, 'fa-trash-can', 'switch:' + item.key + ':delete', () => confirmDeleteSwitch(item.key), busy)
            );
            actionsCell.appendChild(actions);
            row.append(identityCell, parentCell, gpioCell, bootCell, currentCell, actionsCell);
            body.appendChild(row);
        });
        if (!visible.length) { emptyRow(body, 6, switches.length ? 'Không tìm thấy switch phù hợp.' : 'Chưa có switch. Gán relay vào một ESP32 để tạo cấu hình.', switches.length > 0, clearSwitchFilters); }
        byId('dm-switch-result').textContent = 'Hiển thị ' + visible.length + ' / ' + switches.length + ' switch · Cuộn ngang để xem đủ các cột';
    }

    function render() {
        const focusedKey = document.activeElement && document.activeElement.dataset.focusKey;
        focusTargets = new Map();
        renderNodes();
        renderSwitches();
        byId('dm-add-switch').disabled = nodes.length === 0;
        byId('dm-add-switch').title = nodes.length ? '' : 'Thêm một ESP32 trước khi gán switch';
        const focused = focusTargets.get(focusedKey);
        if (focused && !focused.disabled) { focused.focus(); }
    }

    async function copyIp(ip) {
        try {
            if (!navigator.clipboard) { throw new Error('Unavailable'); }
            await navigator.clipboard.writeText(ip);
            notify('Đã sao chép IP: ' + ip);
        } catch (error) { notify('Không thể sao chép tự động. Địa chỉ IP: ' + ip); }
    }

    function clearErrors(form, id) {
        byId(id).textContent = '';
        byId(id).hidden = true;
        form.querySelectorAll('input, select').forEach((field) => { field.setCustomValidity(''); field.removeAttribute('aria-invalid'); });
    }

    function fail(id, fieldId, message) {
        byId(id).textContent = message;
        byId(id).hidden = false;
        byId(fieldId).setCustomValidity(message);
        byId(fieldId).setAttribute('aria-invalid', 'true');
        byId(fieldId).focus();
    }

    function openDialog(id) {
        dialogOpeners.set(id, document.activeElement);
        byId(id).showModal();
    }

    function openNode(id) {
        const node = id ? findNode(id) : null;
        editingNodeId = node ? node.id : null;
        nodeForm.reset();
        clearErrors(nodeForm, 'dm-node-error');
        byId('dm-node-id').readOnly = Boolean(node);
        byId('dm-node-dialog-title').textContent = node ? 'Cấu Hình Thiết Bị ESP32' : 'Thêm Thiết Bị ESP32';
        if (node) {
            ['id', 'name', 'ip', 'location'].forEach((field) => { byId('dm-node-' + field).value = node[field]; });
            byId('dm-node-host').value = node.hostname;
        }
        openDialog('dm-node-dialog');
        byId(node ? 'dm-node-name' : 'dm-node-id').focus();
    }

    function validIp(ip) {
        const parts = ip.split('.');
        return parts.length === 4 && parts.every((part) => /^\d{1,3}$/.test(part) && Number(part) <= 255);
    }

    nodeForm.addEventListener('submit', (event) => {
        event.preventDefault();
        clearErrors(nodeForm, 'dm-node-error');
        if (!nodeForm.reportValidity()) { return; }
        const candidate = { id: editingNodeId || valueOf('dm-node-id'), name: valueOf('dm-node-name'), ip: valueOf('dm-node-ip'), hostname: valueOf('dm-node-host').toLowerCase(), location: valueOf('dm-node-location') };
        if (!/^[A-Za-z0-9_-]+$/.test(candidate.id)) { fail('dm-node-error', 'dm-node-id', 'Device ID chỉ gồm chữ, số, dấu gạch ngang hoặc gạch dưới.'); return; }
        if (nodes.some((node) => node.id.toLowerCase() === candidate.id.toLowerCase() && node.id !== editingNodeId)) { fail('dm-node-error', 'dm-node-id', 'Device ID này đã tồn tại.'); return; }
        if (!candidate.name) { fail('dm-node-error', 'dm-node-name', 'Vui lòng nhập tên gợi nhớ.'); return; }
        if (!candidate.location) { fail('dm-node-error', 'dm-node-location', 'Vui lòng nhập vị trí lắp đặt.'); return; }
        if (!validIp(candidate.ip)) { fail('dm-node-error', 'dm-node-ip', 'Địa chỉ IPv4 chưa hợp lệ, ví dụ 192.168.1.160.'); return; }
        const canonicalIp = candidate.ip.split('.').map(Number).join('.');
        if (nodes.some((node) => node.ip === canonicalIp && node.id !== editingNodeId)) { fail('dm-node-error', 'dm-node-ip', 'Địa chỉ IP này đã thuộc một ESP32 khác.'); return; }
        candidate.ip = canonicalIp;
        if (candidate.hostname && !/^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(candidate.hostname)) { fail('dm-node-error', 'dm-node-host', 'Nhập hostname, ví dụ esp32-room-103.local.'); return; }
        if (editingNodeId) { nodes = nodes.map((node) => node.id === editingNodeId ? { ...node, ...candidate } : node); }
        else { nodes.push({ ...candidate, online: false, latency: null, offlineReason: 'Chưa kết nối' }); }
        byId('dm-node-dialog').close();
        refreshOptions();
        render();
        notify('Đã lưu thiết bị minh họa ' + candidate.id + '.');
    });

    function openSwitch(key) {
        if (!nodes.length) { return; }
        const item = key ? findSwitch(key) : null;
        editingSwitchKey = item ? item.key : null;
        switchForm.reset();
        clearErrors(switchForm, 'dm-switch-error');
        fillSelect('dm-switch-parent', nodes.map((node) => ({ value: node.id, label: node.id + ' (' + node.ip + ')' })), null, false);
        byId('dm-switch-dialog-title').textContent = item ? 'Sửa Cấu Hình Switch' : 'Gán Switch / Relay Vào ESP32';
        if (item) {
            byId('dm-switch-parent').value = item.nodeId;
            ['gpio', 'wiring', 'name', 'type', 'boot', 'rating'].forEach((field) => { byId('dm-switch-' + field).value = String(item[field]); });
        }
        openDialog('dm-switch-dialog');
        byId('dm-switch-parent').focus();
    }

    function parseGpio(value) {
        const match = /^(?:GPIO\s*)?(\d+)$/i.exec(value.trim());
        if (!match) { return null; }
        const gpio = Number(match[1]);
        return Number.isSafeInteger(gpio) ? gpio : null;
    }

    switchForm.addEventListener('submit', (event) => {
        event.preventDefault();
        clearErrors(switchForm, 'dm-switch-error');
        if (!switchForm.reportValidity()) { return; }
        const candidate = { nodeId: byId('dm-switch-parent').value, name: valueOf('dm-switch-name'), gpio: parseGpio(valueOf('dm-switch-gpio')), wiring: byId('dm-switch-wiring').value, type: byId('dm-switch-type').value, boot: byId('dm-switch-boot').value, rating: valueOf('dm-switch-rating') };
        if (!findNode(candidate.nodeId)) { fail('dm-switch-error', 'dm-switch-parent', 'Vui lòng chọn ESP32 cha.'); return; }
        if (candidate.gpio === null) { fail('dm-switch-error', 'dm-switch-gpio', 'Nhập số chân GPIO, ví dụ GPIO 18 hoặc 18.'); return; }
        if (switches.some((item) => item.nodeId === candidate.nodeId && item.gpio === candidate.gpio && item.key !== editingSwitchKey)) { fail('dm-switch-error', 'dm-switch-gpio', 'Chân GPIO này đã được gán cho một switch trên ESP32 đã chọn.'); return; }
        if (!candidate.name) { fail('dm-switch-error', 'dm-switch-name', 'Vui lòng nhập tên switch.'); return; }
        if (editingSwitchKey) { switches = switches.map((item) => item.key === editingSwitchKey ? { ...item, ...candidate } : item); }
        else { switches.push({ ...candidate, key: 'sw-' + nextSwitchKey++, on: candidate.boot === 'ON', schedule: null }); }
        byId('dm-switch-dialog').close();
        render();
        notify('Đã lưu cấu hình switch minh họa.');
    });

    function toggleSwitch(key) {
        const item = findSwitch(key);
        if (!item || !findNode(item.nodeId).online || pulseJobs.has(key)) { return; }
        item.on = !item.on;
        render();
        notify('Minh họa: ' + item.name + ' chuyển sang ' + (item.on ? 'ON' : 'OFF') + '.');
    }

    function testPulse(key) {
        const item = findSwitch(key);
        if (!item || !findNode(item.nodeId).online || pulseJobs.has(key)) { return; }
        const restore = item.on;
        const job = { restore, timer: null };
        pulseJobs.set(key, job);
        item.on = true;
        job.timer = setTimeout(() => {
            if (pulseJobs.get(key) !== job) { return; }
            pulseJobs.delete(key);
            const current = findSwitch(key);
            if (current) { current.on = restore; render(); notify('Đã kết thúc xung minh họa GPIO ' + current.gpio + '.'); }
        }, 1000);
        render();
        notify('Đang mô phỏng xung 1 giây trên GPIO ' + item.gpio + '.');
    }

    function cancelPulse(key) {
        const job = pulseJobs.get(key);
        if (job) { clearTimeout(job.timer); pulseJobs.delete(key); }
    }

    function confirmAction(title, description, label, action, danger) {
        byId('dm-confirm-title').textContent = title;
        byId('dm-confirm-description').textContent = description;
        byId('dm-confirm-submit').textContent = label;
        byId('dm-confirm-submit').className = 'um-button ' + (danger ? 'um-button-danger' : 'um-button-primary');
        pendingConfirm = action;
        openDialog('dm-confirm-dialog');
    }

    function confirmDeleteNode(id) {
        const node = findNode(id);
        const count = switches.filter((item) => item.nodeId === id).length;
        confirmAction('Xóa Thiết Bị ESP32?', 'Gỡ ' + node.name + ' và ' + count + ' switch đang gắn với thiết bị này trong bản xem UI.', 'Xóa Thiết Bị', () => {
            switches.filter((item) => item.nodeId === id).forEach((item) => { cancelPulse(item.key); });
            switches = switches.filter((item) => item.nodeId !== id);
            nodes = nodes.filter((item) => item.id !== id);
            refreshOptions();
            render();
            notify('Đã gỡ thiết bị và các switch liên quan trong bản xem UI.');
        }, true);
    }

    function confirmDeleteSwitch(key) {
        const item = findSwitch(key);
        confirmAction('Gỡ Cấu Hình Switch?', 'Gỡ ' + item.name + ' (GPIO ' + item.gpio + ') khỏi danh sách minh họa.', 'Gỡ Switch', () => {
            cancelPulse(key);
            switches = switches.filter((item) => item.key !== key);
            render();
            notify('Đã gỡ cấu hình switch minh họa.');
        }, true);
    }

    function confirmReboot(id) {
        const node = findNode(id);
        if (!node || !node.online || switches.some((item) => item.nodeId === id && pulseJobs.has(item.key))) { return; }
        confirmAction('Mô Phỏng Khởi Động Lại?', 'Áp dụng trạng thái khởi động cho các switch thuộc ' + node.name + '. Không gửi lệnh đến ESP32.', 'Mô Phỏng Reboot', () => {
            switches.filter((item) => item.nodeId === id).forEach((item) => { if (item.boot !== 'LAST') { item.on = item.boot === 'ON'; } });
            render();
            notify('Đã mô phỏng reboot ' + node.id + '.');
        }, false);
    }

    function localMinute(date) {
        const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
        return shifted.toISOString().slice(0, 16);
    }

    function openSchedule(key) {
        const item = findSwitch(key);
        schedulingKey = key;
        scheduleForm.reset();
        clearErrors(scheduleForm, 'dm-schedule-error');
        byId('dm-schedule-description').textContent = item.name + ' · GPIO ' + item.gpio;
        byId('dm-schedule-time').min = localMinute(new Date());
        if (item.schedule) {
            byId('dm-schedule-action').value = item.schedule.action;
            byId('dm-schedule-time').value = item.schedule.at;
        }
        byId('dm-remove-schedule').disabled = !item.schedule;
        openDialog('dm-schedule-dialog');
        byId('dm-schedule-action').focus();
    }

    scheduleForm.addEventListener('submit', (event) => {
        event.preventDefault();
        clearErrors(scheduleForm, 'dm-schedule-error');
        if (!scheduleForm.reportValidity()) { return; }
        const item = findSwitch(schedulingKey);
        const at = byId('dm-schedule-time').value;
        const timestamp = new Date(at).getTime();
        if (!Number.isFinite(timestamp) || timestamp <= Date.now()) { fail('dm-schedule-error', 'dm-schedule-time', 'Chọn thời gian trong tương lai.'); return; }
        if (!item) { return; }
        item.schedule = { action: byId('dm-schedule-action').value, at };
        byId('dm-schedule-dialog').close();
        render();
        notify('Đã lưu lịch minh họa; lịch không tự gửi lệnh đến ESP32.');
    });
    byId('dm-remove-schedule').addEventListener('click', () => {
        const item = findSwitch(schedulingKey);
        if (item) { item.schedule = null; }
        byId('dm-schedule-dialog').close();
        render();
        notify('Đã xóa lịch minh họa.');
    });

    byId('dm-confirm-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const action = pendingConfirm;
        pendingConfirm = null;
        if (action) { action(); }
        byId('dm-confirm-dialog').close();
    });
    document.querySelectorAll('[data-close-dialog]').forEach((button) => { button.addEventListener('click', () => { byId(button.dataset.closeDialog).close(); }); });
    ['dm-node-dialog', 'dm-switch-dialog', 'dm-schedule-dialog', 'dm-confirm-dialog'].forEach((id) => {
        byId(id).addEventListener('close', () => {
            if (id === 'dm-node-dialog') { nodeForm.reset(); clearErrors(nodeForm, 'dm-node-error'); editingNodeId = null; }
            if (id === 'dm-switch-dialog') { switchForm.reset(); clearErrors(switchForm, 'dm-switch-error'); editingSwitchKey = null; }
            if (id === 'dm-schedule-dialog') { scheduleForm.reset(); clearErrors(scheduleForm, 'dm-schedule-error'); schedulingKey = null; }
            if (id === 'dm-confirm-dialog') { pendingConfirm = null; }
            const opener = dialogOpeners.get(id);
            const replacement = opener && focusTargets.get(opener.dataset.focusKey);
            if (opener && opener.isConnected) { opener.focus(); }
            else if (replacement && !replacement.disabled) { replacement.focus(); }
            else { byId('dm-add-node').focus(); }
        });
    });
    [[nodeForm, 'dm-node-error'], [switchForm, 'dm-switch-error'], [scheduleForm, 'dm-schedule-error']].forEach(([form, errorId]) => { form.addEventListener('input', () => { clearErrors(form, errorId); }); });
    byId('dm-add-node').addEventListener('click', () => openNode());
    byId('dm-add-switch').addEventListener('click', () => openSwitch());
    document.querySelectorAll('[data-node-status]').forEach((button) => { button.addEventListener('click', () => { nodeStatusFilter = button.dataset.nodeStatus; render(); }); });
    ['dm-node-search', 'dm-switch-search'].forEach((id) => { byId(id).addEventListener('input', render); });
    ['dm-location', 'dm-parent-filter', 'dm-type-filter'].forEach((id) => { byId(id).addEventListener('change', render); });
    byId('dm-dismiss-toast').addEventListener('click', () => { clearTimeout(toastTimer); byId('dm-toast').hidden = true; });
    refreshOptions();
    render();
    ['dm-add-node', 'dm-node-save', 'dm-switch-save', 'dm-schedule-save'].forEach((id) => { byId(id).disabled = false; });
}());
