/* UI-only controls: no commands, requests or changes are sent to real devices. */
(function () {
    'use strict';

    const byId = (id) => document.getElementById(id);
    const cards = document.querySelectorAll('.dc-relay-card');
    let toastTimer;

    function renderState(card, on) {
        const kind = card.dataset.kind;
        const name = card.querySelector('.dc-relay-name').textContent.trim();
        const action = card.querySelector('.dc-action-button');
        const control = card.querySelector('.dc-switch');
        card.dataset.on = String(on);
        card.querySelector('.dc-relay-state').textContent = on ? 'ON' : 'OFF';
        control.setAttribute('aria-checked', String(on));
        if (kind === 'uv') {
            action.textContent = on ? 'TẮT SỚM' : 'BẬT ĐÈN';
        } else if (kind === 'valve') {
            action.textContent = on ? 'ĐÓNG VAN' : 'MỞ VAN';
            card.querySelector('.dc-valve-state').textContent = on ? 'OPEN' : 'CLOSED';
        } else {
            action.textContent = on ? 'TẮT' : 'BẬT';
        }
        const actionLabel = kind === 'valve' ? (on ? 'Đóng ' : 'Mở ') : kind === 'uv' && on ? 'Tắt sớm ' : on ? 'Tắt ' : 'Bật ';
        action.setAttribute('aria-label', actionLabel + name);
    }

    function notify(message) {
        clearTimeout(toastTimer);
        byId('dc-feedback').textContent = message;
        byId('dc-toast-message').textContent = message;
        byId('dc-toast').hidden = false;
        toastTimer = setTimeout(() => { byId('dc-toast').hidden = true; }, 5000);
    }

    cards.forEach((card) => {
        const action = card.querySelector('.dc-action-button');
        const control = card.querySelector('.dc-switch');
        const toggle = () => {
            const on = card.dataset.on !== 'true';
            renderState(card, on);
            const name = card.querySelector('.dc-relay-name').textContent.trim();
            notify('Minh họa: ' + name + ' chuyển sang ' + (on ? 'ON' : 'OFF') + '.');
        };
        renderState(card, card.dataset.on === 'true');
        action.addEventListener('click', toggle);
        control.addEventListener('click', toggle);
        action.disabled = false;
        control.disabled = false;
    });

    byId('dc-dismiss-toast').addEventListener('click', () => {
        clearTimeout(toastTimer);
        byId('dc-toast').hidden = true;
    });
}());
