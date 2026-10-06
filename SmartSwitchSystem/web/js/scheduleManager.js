/* UI preview only: schedules live in memory and never send hardware commands. */
(() => {
    'use strict';
    const get = (id) => document.getElementById(id);
    const daysOfWeek = [1, 2, 3, 4, 5, 6, 0];
    const dayLabels = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
    const presets = { daily: daysOfWeek, weekdays: [1, 2, 3, 4, 5], weekend: [6, 0] };
    const switches = [
        { id: 'esp01-g16', name: 'Quạt hút thông gió', node: 'ESP32-01', location: 'Xưởng A', gpio: 16 },
        { id: 'esp01-g17', name: 'Đèn trần Xưởng A', node: 'ESP32-01', location: 'Xưởng A', gpio: 17 },
        { id: 'esp03-g22', name: 'Van cấp nước TĐ', node: 'ESP32-03', location: 'Kho Thiết Bị', gpio: 22 },
        { id: 'esp03-g21', name: 'Đèn UV khử khuẩn', node: 'ESP32-03', location: 'Phòng Thí Nghiệm', gpio: 21 },
        { id: 'esp02-g18', name: 'Bơm áp lực', node: 'ESP32-02', location: 'Vườn Ươm', gpio: 18 }
    ];
    let schedules = [
        { id: 'schedule-1', name: 'Tưới nước ca sáng tự động', switchId: 'esp03-g22', time: '06:30', action: 'ON', days: [1, 2, 3, 4, 5], mode: 'weekly', enabled: true },
        { id: 'schedule-2', name: 'Bật đèn chiếu sáng tăng ca', switchId: 'esp01-g17', time: '18:00', action: 'ON', days: [1, 2, 3, 4, 5, 6], mode: 'weekly', enabled: true },
        { id: 'schedule-3', name: 'Tắt quạt thông gió hết ca', switchId: 'esp01-g16', time: '22:00', action: 'OFF', days: [1, 2, 3, 4, 5], mode: 'weekly', enabled: true },
        { id: 'schedule-4', name: 'Khử khuẩn phòng thí nghiệm ca đêm', switchId: 'esp03-g21', time: '01:00', action: 'ON', days: [1, 3, 5], mode: 'weekly', enabled: true },
        { id: 'schedule-5', name: 'Tưới phun sương vườn ươm', switchId: 'esp02-g18', time: '16:45', action: 'ON', days: [1, 2, 3, 4, 5], mode: 'weekly', enabled: false },
        { id: 'schedule-6', name: 'Chiếu sáng cảnh quan cuối tuần', switchId: 'esp01-g17', time: '19:30', action: 'ON', days: [6, 0], mode: 'weekly', enabled: false }
    ];
    let nextId = 7;
    let statusFilter = 'all';
    let editingId = null;
    let repeatMode = 'weekly';
    let selectedDays = new Set(presets.weekdays);
    let pendingDeleteId = null;
    let deleteFocusKey = null;
    let toastTimer;
    let lastMinute = '';
    const rowRefs = new Map();
    const statusButtons = Array.from(document.querySelectorAll('[data-schedule-status]'));
    const presetButtons = Array.from(document.querySelectorAll('[data-repeat-preset]'));
    const dayButtons = Array.from(document.querySelectorAll('[data-repeat-day]'));
    const timeButtons = Array.from(document.querySelectorAll('[data-time-add]'));
    const formFields = ['sm-switch', 'sm-time', 'sm-date', 'sm-name'].map(get);
    const dateFormatter = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
    const clockFormatter = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });

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
        get('sm-toast-message').textContent = message;
        get('sm-feedback').textContent = message;
        get('sm-toast').hidden = false;
        toastTimer = setTimeout(() => { get('sm-toast').hidden = true; }, 6000);
    }

    function normalized(value) {
        return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
    }

    function switchById(id) { return switches.find((item) => item.id === id); }
    function pad(value) { return String(value).padStart(2, '0'); }
    function localDateValue(date) { return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()); }

    function parseLocalDate(dateText, timeText) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(dateText) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(timeText)) { return null; }
        const [year, month, day] = dateText.split('-').map(Number);
        const [hour, minute] = timeText.split(':').map(Number);
        const candidate = new Date(0);
        candidate.setFullYear(year, month - 1, day);
        candidate.setHours(hour, minute, 0, 0);
        return candidate.getFullYear() === year && candidate.getMonth() === month - 1 && candidate.getDate() === day && candidate.getHours() === hour && candidate.getMinutes() === minute ? candidate : null;
    }

    // Recalculate from local calendar dates, rather than adding fixed 24-hour durations.
    function nextOccurrence(schedule, now) {
        if (schedule.mode === 'once') {
            const candidate = parseLocalDate(schedule.date, schedule.time);
            return candidate && candidate > now ? candidate : null;
        }
        const [hour, minute] = schedule.time.split(':').map(Number);
        for (let offset = 0; offset <= 7; offset++) {
            const candidate = new Date(now);
            candidate.setDate(candidate.getDate() + offset);
            candidate.setHours(hour, minute, 0, 0);
            if (schedule.days.includes(candidate.getDay()) && candidate > now && candidate.getHours() === hour && candidate.getMinutes() === minute) { return candidate; }
        }
        return null;
    }

    function expireOnceSchedules(now) {
        let changed = false;
        schedules.forEach((schedule) => {
            if (schedule.mode === 'once' && schedule.enabled && !nextOccurrence(schedule, now)) {
                schedule.enabled = false;
                changed = true;
            }
        });
        return changed;
    }

    function earliestSchedule(now) {
        let result = null;
        schedules.forEach((schedule) => {
            if (!schedule.enabled) { return; }
            const date = nextOccurrence(schedule, now);
            if (date && (!result || date < result.date)) { result = { schedule, date }; }
        });
        return result;
    }

    function occurrenceLabel(date, now) {
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);
        const day = localDateValue(date) === localDateValue(now) ? 'Hôm nay' : localDateValue(date) === localDateValue(tomorrow) ? 'Ngày mai' : dayLabels[date.getDay()] + ', ' + dateFormatter.format(date);
        return day + ', ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
    }

    function countdown(date, now) {
        const minutes = Math.ceil((date - now) / 60000);
        if (minutes <= 1) { return 'dưới 1 phút'; }
        if (minutes >= 1440) { return Math.floor(minutes / 1440) + ' ngày ' + Math.floor((minutes % 1440) / 60) + ' giờ'; }
        return (minutes >= 60 ? Math.floor(minutes / 60) + ' giờ ' : '') + (minutes % 60) + ' phút';
    }

    function updateRowTime(schedule, ref, now, earliest) {
        const next = nextOccurrence(schedule, now);
        const expired = schedule.mode === 'once' && !next;
        const isNext = Boolean(schedule.enabled && earliest && earliest.schedule.id === schedule.id);
        ref.row.className = 'sm-schedule' + (schedule.action === 'OFF' ? ' is-off' : '') + (!schedule.enabled ? ' is-paused' : '') + (isNext ? ' is-next' : '') + (editingId === schedule.id ? ' is-editing' : '');
        ref.nextBadge.hidden = !isNext;
        ref.pausedBadge.hidden = schedule.enabled;
        ref.pausedBadge.textContent = expired ? 'QUÁ GIỜ' : 'TẠM DỪNG';
        ref.state.textContent = expired ? 'QUÁ GIỜ' : schedule.enabled ? 'ĐANG BẬT' : 'ĐÃ TẠM DỪNG';
        ref.toggle.setAttribute('aria-checked', String(schedule.enabled));
        ref.toggle.setAttribute('aria-label', (schedule.enabled ? 'Tạm dừng: ' : 'Bật lịch: ') + schedule.name);
        ref.toggle.disabled = expired;
        ref.toggle.title = expired ? 'Sửa ngày chạy để bật lại lịch một lần' : schedule.enabled ? 'Tạm dừng lịch' : 'Bật lại lịch';
        ref.occurrence.textContent = expired ? 'Đã qua giờ dự kiến. Sửa lịch để chọn ngày mới.' : !schedule.enabled ? 'Lịch đang tạm dừng.' : next ? 'Dự kiến: ' + occurrenceLabel(next, now) : 'Chưa chọn ngày lặp.';
        if (schedule.lastDemo) { ref.occurrence.textContent += ' · Chạy thử UI: ' + schedule.lastDemo; }
    }

    function updateClock(now = new Date()) {
        get('sm-clock').textContent = 'Giờ trình duyệt: ' + clockFormatter.format(now);
        const earliest = earliestSchedule(now);
        get('sm-next-summary').textContent = earliest ? 'LẦN CHẠY KẾ: ' + occurrenceLabel(earliest.date, now) + ' (còn ' + countdown(earliest.date, now) + ')' : 'LẦN CHẠY KẾ: Chưa có lịch đang bật';
        get('sm-next-summary').title = earliest ? earliest.schedule.name : '';
        schedules.forEach((schedule) => {
            const ref = rowRefs.get(schedule.id);
            if (ref) { updateRowTime(schedule, ref, now, earliest); }
        });
    }

    function restoreFocus(key) {
        if (!key) { return; }
        const target = Array.from(get('sm-schedules').querySelectorAll('button')).find((button) => button.dataset.focusKey === key);
        if (target && !target.disabled) { target.focus(); } else { get('sm-result').focus(); }
    }

    function operationButton(schedule, operation, iconName, label, callback) {
        const button = element('button', 'sm-icon-button');
        button.type = 'button';
        button.dataset.operation = operation;
        button.dataset.focusKey = schedule.id + ':' + operation;
        button.setAttribute('aria-label', label + ': ' + schedule.name);
        button.title = label;
        button.append(icon(iconName));
        button.addEventListener('click', callback);
        return button;
    }

    function renderSchedule(schedule) {
        const relay = switchById(schedule.switchId);
        const row = element('article', 'sm-schedule');
        const titleId = 'sm-title-' + schedule.id;
        row.setAttribute('aria-labelledby', titleId);
        const clock = element('div', 'sm-time-block');
        const time = element('time', '', schedule.time);
        time.setAttribute('datetime', schedule.time);
        clock.append(time, element('span', '', schedule.mode === 'once' ? 'MỘT LẦN' : '24H CYCLE'));
        const info = element('div', 'sm-schedule-info');
        const titleLine = element('div', 'sm-title-line');
        const title = element('h3', '', schedule.name);
        title.id = titleId;
        const nextBadge = element('span', 'sm-next-badge', 'KẾ TIẾP');
        const pausedBadge = element('span', 'sm-paused-badge', 'TẠM DỪNG');
        titleLine.append(title, element('span', 'sm-action-badge' + (schedule.action === 'OFF' ? ' is-off' : ''), schedule.action === 'ON' ? 'BẬT (ON)' : 'TẮT (OFF)'), nextBadge, pausedBadge);
        const switchLine = element('div', 'sm-switch-line');
        switchLine.append(element('span', 'sm-gpio', 'GPIO ' + relay.gpio), element('span', '', '· ' + relay.name + ' (' + relay.node + ')'));
        const repeat = element('div', 'sm-repeat-line');
        if (schedule.mode === 'once') {
            const date = parseLocalDate(schedule.date, schedule.time);
            repeat.append(element('span', 'sm-date-chip', 'Một lần: ' + dateFormatter.format(date)));
        } else {
            repeat.setAttribute('role', 'group');
            repeat.setAttribute('aria-label', 'Ngày lặp: ' + daysOfWeek.filter((day) => schedule.days.includes(day)).map((day) => dayLabels[day]).join(', '));
            daysOfWeek.forEach((day) => {
                const chip = element('span', 'sm-day-chip' + (schedule.days.includes(day) ? ' is-selected' : ''), dayLabels[day]);
                chip.setAttribute('aria-hidden', 'true');
                repeat.append(chip);
            });
        }
        const occurrence = element('p', 'sm-occurrence');
        info.append(titleLine, switchLine, repeat, occurrence);
        const controls = element('div', 'sm-schedule-controls');
        const state = element('span', 'sm-state');
        const toggle = element('button', 'sm-toggle');
        toggle.type = 'button';
        toggle.dataset.focusKey = schedule.id + ':toggle';
        toggle.setAttribute('role', 'switch');
        const track = element('span', 'sm-toggle-track');
        track.setAttribute('aria-hidden', 'true');
        track.append(element('span'));
        toggle.append(track);
        toggle.addEventListener('click', () => {
            if (schedule.mode === 'once' && !nextOccurrence(schedule, new Date())) {
                renderList(toggle.dataset.focusKey);
                notify('Lịch một lần đã qua giờ. Sửa ngày chạy để bật lại.');
                return;
            }
            schedule.enabled = !schedule.enabled;
            renderList(toggle.dataset.focusKey);
            notify((schedule.enabled ? 'Đã bật lịch: ' : 'Đã tạm dừng: ') + schedule.name);
        });
        const run = operationButton(schedule, 'run', 'play', 'Chạy thử UI (không gửi lệnh ESP32)', () => {
            schedule.lastDemo = clockFormatter.format(new Date());
            updateClock();
            notify('Chạy thử UI: ' + relay.name + ' → ' + schedule.action + '. Chưa gửi lệnh đến ESP32.');
        });
        const edit = operationButton(schedule, 'edit', 'pen', 'Sửa lịch', () => editSchedule(schedule));
        const remove = operationButton(schedule, 'delete', 'trash-can', 'Xóa lịch', () => {
            pendingDeleteId = schedule.id;
            deleteFocusKey = remove.dataset.focusKey;
            get('sm-delete-description').textContent = 'Xóa “' + schedule.name + '” lúc ' + schedule.time + '? Lịch này sẽ được bỏ khỏi bản xem UI.';
            get('sm-delete-dialog').showModal();
        });
        controls.append(state, toggle, run, edit, remove);
        row.append(clock, info, controls);
        rowRefs.set(schedule.id, { row, nextBadge, pausedBadge, state, toggle, occurrence });
        return row;
    }

    function renderList(focusKey) {
        const now = new Date();
        expireOnceSchedules(now);
        const query = normalized(get('sm-search').value);
        const relayFilter = get('sm-switch-filter').value;
        const visible = schedules.filter((schedule) => {
            const relay = switchById(schedule.switchId);
            return (statusFilter === 'all' || (statusFilter === 'enabled' ? schedule.enabled : !schedule.enabled)) && (relayFilter === 'all' || relayFilter === schedule.switchId) && normalized(schedule.name + ' ' + relay.name + ' ' + relay.node + ' ' + relay.location + ' GPIO ' + relay.gpio + ' ' + schedule.time).includes(query);
        });
        rowRefs.clear();
        get('sm-schedules').replaceChildren(...visible.map(renderSchedule));
        if (!visible.length) {
            const empty = element('div', 'sm-empty');
            empty.append(element('p', '', schedules.length ? 'Không tìm thấy lịch phù hợp với bộ lọc.' : 'Chưa có lịch hẹn giờ. Tạo lịch đầu tiên bằng form bên cạnh.'));
            if (schedules.length) {
                const clear = element('button', 'um-button um-button-secondary', 'Xóa bộ lọc');
                clear.type = 'button';
                clear.addEventListener('click', clearFilters);
                empty.append(clear);
            }
            get('sm-schedules').append(empty);
        }
        const enabled = schedules.filter((schedule) => schedule.enabled).length;
        get('sm-count-all').textContent = String(schedules.length);
        get('sm-count-enabled').textContent = String(enabled);
        get('sm-count-paused').textContent = String(schedules.length - enabled);
        get('sm-result').textContent = 'Hiển thị ' + visible.length + ' / ' + schedules.length + ' lịch · Dữ liệu minh họa, tải lại trang sẽ đặt lại.';
        get('sm-clear-filters').hidden = !query && relayFilter === 'all' && statusFilter === 'all';
        statusButtons.forEach((button) => button.setAttribute('aria-pressed', String(button.dataset.scheduleStatus === statusFilter)));
        updateClock(now);
        restoreFocus(focusKey);
    }

    function clearFilters() {
        get('sm-search').value = '';
        get('sm-switch-filter').value = 'all';
        statusFilter = 'all';
        renderList();
        get('sm-search').focus();
    }

    function updateSwitchContext() {
        const relay = switchById(get('sm-switch').value);
        get('sm-current-switch').textContent = relay.node + ' (' + relay.location + ') · GPIO ' + relay.gpio + ' (' + relay.name + ')';
        get('sm-switch-detail').textContent = relay.node + ' · ' + relay.location + ' · GPIO ' + relay.gpio;
    }

    function updateRepeatControls() {
        const once = repeatMode === 'once';
        get('sm-day-buttons').hidden = once;
        get('sm-date-field').hidden = !once;
        get('sm-date').required = once;
        get('sm-date').min = localDateValue(new Date());
        get('sm-day-count').textContent = once ? 'Chạy vào ngày đã chọn' : selectedDays.size + ' ngày đã chọn';
        dayButtons.forEach((button) => button.setAttribute('aria-pressed', String(selectedDays.has(Number(button.dataset.repeatDay)))));
        presetButtons.forEach((button) => {
            const preset = button.dataset.repeatPreset;
            const selected = preset === 'once' ? once : !once && selectedDays.size === presets[preset].length && presets[preset].every((day) => selectedDays.has(day));
            button.setAttribute('aria-pressed', String(selected));
        });
    }

    function clearFormError() {
        get('sm-form-error').hidden = true;
        formFields.forEach((field) => field.setCustomValidity(''));
    }

    function failForm(message, field) {
        get('sm-form-error').textContent = message;
        get('sm-form-error').hidden = false;
        if (field) { field.setCustomValidity(message); field.focus(); }
    }

    function resetEditor(announce = false) {
        editingId = null;
        get('sm-form').reset();
        get('sm-switch').value = 'esp01-g16';
        get('sm-time').value = '06:30';
        get('sm-date').value = localDateValue(new Date());
        get('sm-name').value = '';
        get('sm-action-on').checked = true;
        get('sm-action-off').checked = false;
        repeatMode = 'weekly';
        selectedDays = new Set(presets.weekdays);
        get('sm-form-title').textContent = 'Thêm Lịch Hẹn Giờ Mới';
        get('sm-form-tag').textContent = 'AUTO';
        clearFormError();
        updateSwitchContext();
        updateRepeatControls();
        updateClock();
        if (announce) { notify('Đã đặt lại form. Nhập thông tin để tạo lịch mới.'); }
    }

    function editSchedule(schedule) {
        editingId = schedule.id;
        get('sm-switch').value = schedule.switchId;
        get('sm-time').value = schedule.time;
        get('sm-name').value = schedule.name;
        get('sm-action-on').checked = schedule.action === 'ON';
        get('sm-action-off').checked = schedule.action === 'OFF';
        repeatMode = schedule.mode;
        selectedDays = new Set(schedule.days);
        get('sm-date').value = schedule.date || localDateValue(new Date());
        get('sm-form-title').textContent = 'Chỉnh Sửa Lịch Hẹn Giờ';
        get('sm-form-tag').textContent = 'EDIT';
        clearFormError();
        updateRepeatControls();
        updateSwitchContext();
        updateClock();
        get('sm-name').focus();
        notify('Đang sửa: ' + schedule.name + '. Bấm Lưu để áp dụng thay đổi.');
    }

    switches.forEach((relay) => {
        const option = element('option', '', relay.name + ' · GPIO ' + relay.gpio + ' · ' + relay.node);
        option.value = relay.id;
        get('sm-switch').append(option);
        const filterOption = element('option', '', relay.name + ' (GPIO ' + relay.gpio + ') · ' + relay.node);
        filterOption.value = relay.id;
        get('sm-switch-filter').append(filterOption);
    });
    statusButtons.forEach((button) => button.addEventListener('click', () => { statusFilter = button.dataset.scheduleStatus; renderList(); }));
    get('sm-search').addEventListener('input', () => renderList());
    get('sm-switch-filter').addEventListener('change', () => renderList());
    get('sm-clear-filters').addEventListener('click', clearFilters);
    get('sm-switch').addEventListener('change', updateSwitchContext);
    get('sm-form').addEventListener('input', clearFormError);
    get('sm-form').addEventListener('change', clearFormError);
    get('sm-reset').addEventListener('click', () => { resetEditor(true); get('sm-name').focus(); });
    presetButtons.forEach((button) => button.addEventListener('click', () => {
        const preset = button.dataset.repeatPreset;
        repeatMode = preset === 'once' ? 'once' : 'weekly';
        if (preset !== 'once') { selectedDays = new Set(presets[preset]); }
        clearFormError();
        updateRepeatControls();
    }));
    dayButtons.forEach((button) => button.addEventListener('click', () => {
        const day = Number(button.dataset.repeatDay);
        if (selectedDays.has(day)) { selectedDays.delete(day); } else { selectedDays.add(day); }
        clearFormError();
        updateRepeatControls();
    }));
    timeButtons.forEach((button) => button.addEventListener('click', () => {
        if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(get('sm-time').value)) { failForm('Chọn giờ hợp lệ trước khi cộng thời gian.', get('sm-time')); return; }
        const [hour, minute] = get('sm-time').value.split(':').map(Number);
        const total = hour * 60 + minute + Number(button.dataset.timeAdd);
        if (repeatMode === 'once' && total >= 1440) {
            const date = parseLocalDate(get('sm-date').value, get('sm-time').value);
            if (!date) { failForm('Chọn ngày hợp lệ trước khi cộng qua nửa đêm.', get('sm-date')); return; }
            date.setDate(date.getDate() + 1);
            get('sm-date').value = localDateValue(date);
        }
        get('sm-time').value = pad(Math.floor(total / 60) % 24) + ':' + pad(total % 60);
        clearFormError();
    }));

    get('sm-form').addEventListener('submit', (event) => {
        event.preventDefault();
        clearFormError();
        const name = get('sm-name').value.trim();
        const time = get('sm-time').value;
        const relay = switchById(get('sm-switch').value);
        if (!relay) { failForm('Chọn switch cần điều khiển.', get('sm-switch')); return; }
        if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(time)) { failForm('Chọn giờ hợp lệ theo định dạng 24h.', get('sm-time')); return; }
        if (!name) { failForm('Nhập tên lịch hẹn giờ.', get('sm-name')); return; }
        if (name.length > 120) { failForm('Tên lịch tối đa 120 ký tự.', get('sm-name')); return; }
        if (repeatMode === 'weekly' && !selectedDays.size) { failForm('Chọn ít nhất một ngày lặp trong tuần.'); dayButtons[0].focus(); return; }
        if (repeatMode === 'once') {
            const date = parseLocalDate(get('sm-date').value, time);
            if (!date || date <= new Date()) { failForm('Ngày và giờ chạy một lần phải nằm trong tương lai.', get('sm-date')); return; }
        }
        if (!get('sm-form').reportValidity()) { return; }
        const previous = editingId ? schedules.find((schedule) => schedule.id === editingId) : null;
        const data = { id: previous ? previous.id : 'schedule-' + nextId++, name, switchId: relay.id, time, action: get('sm-action-off').checked ? 'OFF' : 'ON', mode: repeatMode, days: repeatMode === 'weekly' ? daysOfWeek.filter((day) => selectedDays.has(day)) : [], date: repeatMode === 'once' ? get('sm-date').value : null, enabled: previous ? previous.enabled : true };
        if (previous) { schedules = schedules.map((schedule) => schedule.id === previous.id ? data : schedule); } else { schedules.push(data); }
        // Reveal the saved item even if earlier filters would have hidden it.
        get('sm-search').value = '';
        get('sm-switch-filter').value = 'all';
        statusFilter = 'all';
        resetEditor();
        renderList();
        notify((previous ? 'Đã cập nhật lịch: ' : 'Đã thêm lịch: ') + data.name + '. Chỉ lưu trong bản xem UI.');
        get('sm-save').focus();
    });

    const cancelDelete = () => get('sm-delete-dialog').close();
    get('sm-delete-close').addEventListener('click', cancelDelete);
    get('sm-delete-cancel').addEventListener('click', cancelDelete);
    get('sm-delete-dialog').addEventListener('close', () => { pendingDeleteId = null; restoreFocus(deleteFocusKey); deleteFocusKey = null; });
    get('sm-delete-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const schedule = schedules.find((item) => item.id === pendingDeleteId);
        if (!schedule) { cancelDelete(); return; }
        schedules = schedules.filter((item) => item.id !== schedule.id);
        if (editingId === schedule.id) { resetEditor(); }
        deleteFocusKey = null;
        cancelDelete();
        renderList();
        get('sm-result').focus();
        notify('Đã xóa lịch: ' + schedule.name);
    });
    get('sm-refresh').addEventListener('click', () => { renderList(); notify('Đã cập nhật thời gian dự kiến theo đồng hồ trình duyệt.'); });
    get('sm-dismiss-toast').addEventListener('click', () => { clearTimeout(toastTimer); get('sm-toast').hidden = true; });
    resetEditor();
    renderList();
    get('sm-save').disabled = false;
    get('sm-refresh').disabled = false;
    setInterval(() => {
        const now = new Date();
        const minute = localDateValue(now) + 'T' + now.getHours() + ':' + now.getMinutes();
        if (minute === lastMinute) { return; }
        lastMinute = minute;
        if (expireOnceSchedules(now)) {
            const focusKey = document.activeElement && document.activeElement.dataset.focusKey;
            renderList(focusKey);
        } else { updateClock(now); }
    }, 15000);
})();
