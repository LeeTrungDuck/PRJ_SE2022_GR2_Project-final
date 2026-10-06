/* Read-only history preview. These reference records are not a live device feed. */
(() => {
    'use strict';
    const get = (id) => document.getElementById(id);
    const actors = {
        admin: { username: 'admin', initials: 'NA', role: 'ADMINISTRATOR', style: 'admin' },
        operator: { username: 'operator_01', initials: 'QT', role: 'OPERATOR', style: 'operator' },
        scheduler: { username: 'System-Schedule', initials: 'SYS', role: 'CRON_JOB', style: 'scheduler' },
        viewer: { username: 'viewer_02', initials: 'LH', role: 'VIEWER', style: 'viewer' }
    };
    const records = [
        { id: 89421, timestamp: '2025-05-12T14:20:01+07:00', actor: 'admin', source: 'WEB_UI', device: 'ESP32-01', ip: '192.168.1.150', switchName: 'Đèn chiếu sáng chính', gpio: 16, relay: 1, command: 'ON', result: '200_OK', rtt: 41 },
        { id: 89420, timestamp: '2025-05-12T14:18:22+07:00', actor: 'operator', source: 'SERVLET_API', device: 'ESP32-02', ip: '192.168.1.151', switchName: 'Máy bơm tưới tự động', gpio: 4, relay: 2, command: 'OFF', result: '200_OK', rtt: 38 },
        { id: 89419, timestamp: '2025-05-12T14:15:40+07:00', actor: 'scheduler', source: 'SCHEDULER', device: 'ESP32-03', ip: '192.168.1.152', switchName: 'Quạt hút thông gió', gpio: 23, relay: 4, command: 'ON', result: 'TIMEOUT', rtt: 5000 },
        { id: 89418, timestamp: '2025-05-12T14:10:12+07:00', actor: 'viewer', source: 'WEB_UI', device: 'ESP32-01', ip: '192.168.1.150', switchName: 'Đèn cảnh báo an ninh', gpio: 17, relay: 2, command: 'OFF', result: '200_OK', rtt: 45 },
        { id: 89417, timestamp: '2025-05-12T14:02:19+07:00', actor: 'admin', source: 'WEB_UI', device: 'ESP32-01', ip: '192.168.1.150', switchName: 'Đèn chiếu sáng chính', gpio: 16, relay: 1, command: 'OFF', result: '200_OK', rtt: 39 },
        { id: 89416, timestamp: '2025-05-12T13:45:00+07:00', actor: 'scheduler', source: 'SCHEDULER', device: 'ESP32-02', ip: '192.168.1.151', switchName: 'Máy bơm tưới tự động', gpio: 4, relay: 2, command: 'ON', result: '200_OK', rtt: 42 },
        { id: 89415, timestamp: '2025-05-12T13:30:11+07:00', actor: 'operator', source: 'SERVLET_API', device: 'ESP32-01', ip: '192.168.1.150', switchName: 'Còi báo động xưởng 1', gpio: 18, relay: 3, command: 'OFF', result: '200_OK', rtt: 35 },
        { id: 89414, timestamp: '2025-05-12T13:12:05+07:00', actor: 'admin', source: 'WEB_UI', device: 'ESP32-03', ip: '192.168.1.152', switchName: 'Cửa cuốn kho hàng', gpio: 25, relay: 1, command: 'ON', result: 'NODE_OFFLINE', rtt: null },
        { id: 89413, timestamp: '2025-05-12T12:58:34+07:00', actor: 'operator', source: 'SERVLET_API', device: 'ESP32-01', ip: '192.168.1.150', switchName: 'Quạt hút nhiệt tủ điện', gpio: 19, relay: 4, command: 'ON', result: '200_OK', rtt: 33 },
        { id: 89412, timestamp: '2025-05-12T12:40:50+07:00', actor: 'admin', source: 'WEB_UI', device: 'ESP32-02', ip: '192.168.1.151', switchName: 'Van điện từ xả tràn', gpio: 5, relay: 3, command: 'OFF', result: '200_OK', rtt: 46 }
    ];
    const sources = { WEB_UI: 'Web Interface', SERVLET_API: 'Servlet API', SCHEDULER: 'Scheduler (CRON)' };
    let newestFirst = true;
    let readAt = Date.now();
    let selectedRecord = null;
    let traceOpener = null;
    let copying = false;
    let toastTimer;

    function element(tag, className, text) {
        const item = document.createElement(tag);
        if (className) { item.className = className; }
        if (text !== undefined) { item.textContent = text; }
        return item;
    }

    function icon(name) {
        const item = element('i', 'fa-solid fa-' + name);
        item.setAttribute('aria-hidden', 'true');
        return item;
    }

    function notify(message) {
        clearTimeout(toastTimer);
        get('ch-toast-message').textContent = message;
        get('ch-feedback').textContent = message;
        get('ch-toast').hidden = false;
        toastTimer = setTimeout(() => { get('ch-toast').hidden = true; }, 6000);
    }

    function normalize(value) {
        return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().replace(/gpio\s*0*(\d+)/g, 'gpio $1').trim();
    }

    function hasFilters() { return get('ch-search').value.trim() !== '' || ['ch-device-filter', 'ch-command-filter', 'ch-result-filter'].some((id) => get(id).value !== 'all'); }
    function resultLabel(record) { return record.result === '200_OK' ? '200 OK' : record.result; }
    function isSuccess(record) { return record.result === '200_OK'; }
    function visibleRecords() {
        const query = normalize(get('ch-search').value);
        const device = get('ch-device-filter').value;
        const command = get('ch-command-filter').value;
        const result = get('ch-result-filter').value;
        return records.filter((record) => {
            const actor = actors[record.actor];
            const text = '#LOG-' + record.id + ' ' + record.timestamp + ' ' + actor.username + ' ' + actor.role + ' ' + sources[record.source] + ' ' + record.device + ' ' + record.ip + ' ' + record.switchName + ' GPIO ' + record.gpio + ' Relay ' + record.relay + ' ' + record.command + ' ' + (record.command === 'ON' ? 'Bật' : 'Tắt') + ' ' + resultLabel(record);
            const resultMatches = result === 'all' || (result === 'success' ? isSuccess(record) : result === 'error' ? !isSuccess(record) : result === record.result);
            return normalize(text).includes(query) && (device === 'all' || record.device === device) && (command === 'all' || record.command === command) && resultMatches;
        }).sort((a, b) => (new Date(a.timestamp) - new Date(b.timestamp) || a.id - b.id) * (newestFirst ? -1 : 1));
    }

    function openTrace(record, opener) {
        selectedRecord = record;
        traceOpener = opener;
        const actor = actors[record.actor];
        const traceId = 'ESP-TEL-' + record.timestamp.slice(0, 10).replace(/-/g, '') + '-' + record.id;
        get('ch-trace-description').textContent = '#LOG-' + record.id + ' · ' + record.switchName + ' · ' + record.timestamp.slice(0, 19).replace('T', ' ') + ' (UTC+7)';
        get('ch-trace-source').textContent = sources[record.source];
        get('ch-trace-protocol').textContent = record.source === 'SCHEDULER' ? 'MQTT v3.1.1' : 'HTTP POST';
        get('ch-trace-result').textContent = resultLabel(record) + ' · ' + (record.rtt === null ? 'RTT: N/A' : record.rtt + 'ms');
        get('ch-trace-result').className = isSuccess(record) ? 'is-success' : 'is-error';
        get('ch-trace-id').textContent = traceId;
        get('ch-trace-json').textContent = JSON.stringify({
            preview: true,
            trace_id: traceId,
            event_id: record.id,
            timestamp: record.timestamp,
            actor: { username: actor.username, role: actor.role, source: record.source },
            target: { device_id: record.device, ip: record.ip, switch_name: record.switchName, gpio: record.gpio, relay: record.relay },
            command: { state: record.command, value: record.command === 'ON' ? 1 : 0 },
            result: { status: isSuccess(record) ? 'SUCCESS' : 'ERROR', code: record.result, rtt_ms: record.rtt }
        }, null, 2);
        get('ch-copy-json').disabled = copying;
        get('ch-trace-dialog').showModal();
    }

    function renderRow(record) {
        const row = element('tr');
        const actor = actors[record.actor];
        const idCell = element('td');
        const id = element('button', 'ch-log-id', '#LOG-' + record.id);
        id.type = 'button';
        id.dataset.logId = String(record.id);
        id.setAttribute('aria-label', 'Xem chi tiết bản ghi #LOG-' + record.id);
        id.title = 'Xem chi tiết nhật ký và JSON';
        id.addEventListener('click', () => openTrace(record, id));
        idCell.append(id);
        const timeCell = element('td');
        const time = element('time', 'ch-timestamp', record.timestamp.slice(0, 19).replace('T', ' '));
        time.setAttribute('datetime', record.timestamp);
        time.title = record.timestamp;
        timeCell.append(time);
        const actorCell = element('td');
        const actorLayout = element('div', 'ch-actor');
        const avatar = element('span', 'ch-avatar is-' + actor.style, actor.initials);
        avatar.setAttribute('aria-hidden', 'true');
        const actorInfo = element('div', 'ch-actor-info');
        const username = element('span', 'ch-actor-name', actor.username);
        username.title = actor.username;
        actorInfo.append(username, element('span', 'ch-actor-role is-' + actor.style, actor.role));
        actorLayout.append(avatar, actorInfo);
        actorCell.append(actorLayout);
        const deviceCell = element('td');
        deviceCell.append(element('span', 'ch-device-name', record.device), element('span', 'ch-device-ip', record.ip));
        const switchCell = element('td');
        const switchName = element('span', 'ch-switch-name', record.switchName);
        switchName.title = record.switchName;
        switchCell.append(switchName, element('span', 'ch-gpio' + (!isSuccess(record) ? ' is-error' : ''), 'GPIO ' + String(record.gpio).padStart(2, '0') + ' (Relay ' + record.relay + ')'));
        const commandCell = element('td');
        const command = element('span', 'ch-command' + (record.command === 'OFF' ? ' is-off' : ''));
        const dot = element('span', 'ch-command-dot');
        dot.setAttribute('aria-hidden', 'true');
        command.append(dot, element('span', '', record.command === 'ON' ? 'BẬT (1)' : 'TẮT (0)'));
        commandCell.append(command);
        const resultCell = element('td');
        const result = element('div', 'ch-result' + (!isSuccess(record) ? ' is-error' : ''));
        const resultInfo = element('div', 'ch-result-info');
        resultInfo.append(element('span', 'ch-result-label', resultLabel(record)), element('span', 'ch-rtt', record.rtt === null ? '(N/A)' : '(' + record.rtt + 'ms)'));
        result.append(icon(isSuccess(record) ? 'circle-check' : record.result === 'TIMEOUT' ? 'circle-xmark' : 'link-slash'), resultInfo);
        resultCell.append(result);
        row.append(idCell, timeCell, actorCell, deviceCell, switchCell, commandCell, resultCell);
        return row;
    }

    function render() {
        const visible = visibleRecords();
        get('ch-rows').replaceChildren(...visible.map(renderRow));
        if (!visible.length) {
            const row = element('tr');
            const cell = element('td', 'ch-empty-cell');
            cell.setAttribute('colspan', '7');
            cell.append(element('p', 'ch-empty', 'Không tìm thấy nhật ký phù hợp. Thử đổi từ khóa hoặc xóa bộ lọc.'));
            const clear = element('button', 'um-button um-button-secondary', 'Xóa bộ lọc');
            clear.type = 'button';
            clear.addEventListener('click', clearFilters);
            cell.append(clear);
            row.append(cell);
            get('ch-rows').append(row);
        }
        get('ch-record-count').textContent = hasFilters() ? visible.length + ' / ' + records.length + ' bản ghi' : records.length + ' bản ghi gần nhất';
        get('ch-result-count').textContent = 'Hiển thị ' + visible.length + ' / ' + records.length + ' bản ghi';
        get('ch-clear-filters').disabled = !hasFilters();
        get('ch-time-heading').setAttribute('aria-sort', newestFirst ? 'descending' : 'ascending');
        get('ch-sort-time').setAttribute('aria-label', newestFirst ? 'Sắp xếp thời gian từ cũ đến mới' : 'Sắp xếp thời gian từ mới đến cũ');
        get('ch-sort-icon').className = 'fa-solid fa-' + (newestFirst ? 'arrow-down-short-wide' : 'arrow-up-short-wide');
    }

    function clearFilters() {
        get('ch-search').value = '';
        ['ch-device-filter', 'ch-command-filter', 'ch-result-filter'].forEach((id) => { get(id).value = 'all'; });
        render();
        get('ch-search').focus();
    }

    function updateAge() {
        const seconds = Math.max(0, Math.floor((Date.now() - readAt) / 1000));
        const age = seconds < 1 ? 'VỪA XONG' : seconds < 60 ? seconds + ' GIÂY TRƯỚC' : seconds < 3600 ? Math.floor(seconds / 60) + ' PHÚT TRƯỚC' : Math.floor(seconds / 3600) + ' GIỜ TRƯỚC';
        get('ch-updated').textContent = 'ĐỌC DỮ LIỆU: ' + age;
    }

    Array.from(new Set(records.map((record) => record.device))).sort().forEach((device) => {
        const option = element('option', '', device);
        option.value = device;
        get('ch-device-filter').append(option);
    });
    get('ch-search').addEventListener('input', render);
    ['ch-device-filter', 'ch-command-filter', 'ch-result-filter'].forEach((id) => get(id).addEventListener('change', render));
    get('ch-clear-filters').addEventListener('click', clearFilters);
    get('ch-sort-time').addEventListener('click', () => { newestFirst = !newestFirst; render(); });
    get('ch-refresh').addEventListener('click', () => { readAt = Date.now(); render(); updateAge(); notify('Đã làm mới bản xem nhật ký minh họa. Các bộ lọc được giữ nguyên.'); });
    ['ch-trace-close', 'ch-trace-done'].forEach((id) => get(id).addEventListener('click', () => get('ch-trace-dialog').close()));
    get('ch-trace-dialog').addEventListener('close', () => {
        selectedRecord = null;
        get('ch-copy-json').disabled = true;
        if (traceOpener && traceOpener.isConnected) { traceOpener.focus(); } else { get('ch-search').focus(); }
        traceOpener = null;
    });
    get('ch-copy-json').addEventListener('click', async () => {
        if (!selectedRecord || copying) { return; }
        const text = get('ch-trace-json').textContent;
        const id = selectedRecord.id;
        copying = true;
        get('ch-copy-json').disabled = true;
        get('ch-copy-json').setAttribute('aria-busy', 'true');
        try {
            if (!navigator.clipboard || typeof navigator.clipboard.writeText !== 'function') { throw new Error('Clipboard unavailable'); }
            await navigator.clipboard.writeText(text);
            notify('Đã sao chép JSON của #LOG-' + id + '.');
        } catch (error) {
            notify('Không thể sao chép tự động. Chọn nội dung JSON và dùng Ctrl+C để sao chép.');
        } finally {
            copying = false;
            get('ch-copy-json').disabled = !selectedRecord;
            get('ch-copy-json').setAttribute('aria-busy', 'false');
        }
    });
    get('ch-dismiss-toast').addEventListener('click', () => { clearTimeout(toastTimer); get('ch-toast').hidden = true; });
    render();
    updateAge();
    get('ch-refresh').disabled = false;
    get('ch-sort-time').disabled = false;
    setInterval(updateAge, 1000);
})();
