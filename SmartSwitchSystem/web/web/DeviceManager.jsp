<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="activePage" value="DEVICE_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Quản lý thiết bị &amp; GPIO | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceManagerCss.css">
        <script src="${pageContext.request.contextPath}/js/deviceManager.js" defer></script>
    </head>
    <body class="user-manager-page device-manager-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">QUẢN LÝ THIẾT BỊ &amp; GPIO</span>
            <div class="um-topbar-user"><span class="dm-topbar-role">Vai trò: <span class="um-admin-tag">Admin</span></span><span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user" aria-hidden="true"></i></span></div>
        </header>
        <main class="um-main dm-main" id="main-content">
            <h1 class="um-sr-only">Quản Lý Thiết Bị &amp; Switch</h1>
            <section class="dm-panel" aria-labelledby="dm-nodes-title">
                <header class="dm-panel-heading">
                    <div class="dm-section-identity"><span class="dm-section-icon"><i class="fa-solid fa-microchip" aria-hidden="true"></i></span><div><div class="dm-title-line"><h2 id="dm-nodes-title">1. Danh Sách Vi Điều Khiển ESP32</h2><span class="dm-section-tag">Master Nodes</span></div><p>Giám sát các bo mạch ESP32 kết nối mạng, địa chỉ mạng và nhịp tim phần cứng.</p></div></div>
                    <button type="button" class="um-button um-button-primary" id="dm-add-node" disabled><i class="fa-solid fa-circle-plus" aria-hidden="true"></i> Thêm Thiết Bị ESP32 Mới</button>
                </header>
                <div class="dm-toolbar">
                    <div class="dm-search"><label class="um-sr-only" for="dm-node-search">Tìm thiết bị theo ID, tên, IP, hostname hoặc vị trí</label><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><input id="dm-node-search" type="search" placeholder="Tìm theo Device ID, IP, tên gợi nhớ, vị trí..." autocomplete="off" aria-controls="dm-node-rows"></div>
                    <div class="dm-status-filters" role="group" aria-label="Lọc trạng thái ESP32"><span>Lọc:</span><button type="button" data-node-status="all" aria-pressed="true">Tất cả (<span id="dm-count-all">3</span>)</button><button type="button" data-node-status="online" aria-pressed="false">Online (<span id="dm-count-online">2</span>)</button><button type="button" data-node-status="offline" aria-pressed="false">Offline (<span id="dm-count-offline">1</span>)</button></div>
                    <div class="dm-location-filter"><label class="um-sr-only" for="dm-location">Lọc vị trí thiết bị</label><select id="dm-location"><option value="all">Tất cả vị trí xưởng</option></select></div>
                </div>
                <div class="dm-table-scroll" tabindex="0" role="region" aria-label="Bảng thiết bị ESP32, có thể cuộn ngang">
                    <table class="dm-table dm-nodes-table"><caption class="um-sr-only">Thiết bị ESP32 và trạng thái kết nối minh họa</caption><colgroup><col style="width:31%"><col style="width:28%"><col style="width:22%"><col style="width:19%"></colgroup><thead><tr><th scope="col">DEVICE ID / TÊN GỢI NHỚ</th><th scope="col">HOSTNAME / ĐỊA CHỈ IP</th><th scope="col">TRẠNG THÁI &amp; ĐỘ TRỄ</th><th scope="col">THAO TÁC</th></tr></thead><tbody id="dm-node-rows"></tbody></table>
                </div>
                <p class="dm-result-count" id="dm-node-result" role="status"></p>
            </section>

            <section class="dm-panel" aria-labelledby="dm-switches-title">
                <header class="dm-panel-heading">
                    <div class="dm-section-identity"><span class="dm-section-icon dm-section-icon-green"><i class="fa-solid fa-plug" aria-hidden="true"></i></span><div><div class="dm-title-line"><h2 id="dm-switches-title">2. Danh Sách Cấu Hình Switch Con (GPIO Relay Mapping)</h2><span class="dm-section-tag dm-section-tag-green">GPIO Actuators</span></div><p>Ánh xạ relay với các chân GPIO của từng bo vi điều khiển.</p></div></div>
                    <button type="button" class="um-button dm-button-green" id="dm-add-switch" disabled><i class="fa-solid fa-link" aria-hidden="true"></i> Gán Switch / Relay Vào ESP32</button>
                </header>
                <div class="dm-toolbar dm-switch-toolbar">
                    <div class="dm-search"><label class="um-sr-only" for="dm-switch-search">Tìm switch theo tên, GPIO hoặc ESP32</label><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><input id="dm-switch-search" type="search" placeholder="Tìm theo tên tải, chân GPIO (vd: GPIO 16), thiết bị..." autocomplete="off" aria-controls="dm-switch-rows"></div>
                    <label class="um-sr-only" for="dm-parent-filter">Lọc theo ESP32 cha</label><select id="dm-parent-filter"><option value="all">ESP32 cha: Tất cả</option></select>
                    <label class="um-sr-only" for="dm-type-filter">Lọc loại tải</label><select id="dm-type-filter"><option value="all">Loại tải: Tất cả</option><option value="light">Chiếu sáng</option><option value="fan">Quạt / Thông gió</option><option value="motor">Động cơ / Máy bơm</option><option value="other">Tải khác</option></select>
                </div>
                <div class="dm-table-scroll" tabindex="0" role="region" aria-label="Bảng switch và cấu hình GPIO, có thể cuộn ngang">
                    <table class="dm-table dm-switches-table"><caption class="um-sr-only">Switch, thiết bị cha, GPIO và trạng thái minh họa</caption><colgroup><col style="width:25%"><col style="width:20%"><col style="width:8%"><col style="width:17%"><col style="width:12%"><col style="width:18%"></colgroup><thead><tr><th scope="col">TÊN SWITCH (TẢI ĐIỆN)</th><th scope="col">THUỘC ESP32 NÀO</th><th scope="col">GPIO</th><th scope="col">TRẠNG THÁI KHỞI ĐỘNG</th><th scope="col">TRẠNG THÁI THỰC</th><th scope="col">THAO TÁC</th></tr></thead><tbody id="dm-switch-rows"></tbody></table>
                </div>
                <p class="dm-result-count" id="dm-switch-result" role="status"></p>
            </section>
            <p class="dm-preview-note"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Dữ liệu minh họa. Thay đổi chỉ giữ trong phiên xem UI, chưa lưu database hoặc gửi lệnh đến ESP32. Tải lại trang sẽ khôi phục dữ liệu ban đầu.</p>
            <noscript><p class="dm-noscript">Bật JavaScript để xem danh sách, tìm kiếm và thử quản lý thiết bị trong bản xem UI.</p></noscript>
        </main>

        <dialog class="um-dialog" id="dm-node-dialog" aria-labelledby="dm-node-dialog-title">
            <div class="um-dialog-heading"><h2 id="dm-node-dialog-title">Thêm Thiết Bị ESP32</h2><button type="button" class="um-icon-button" data-close-dialog="dm-node-dialog" aria-label="Đóng form thiết bị"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
            <p class="um-dialog-description">Cấu hình thông tin nhận dạng thiết bị trong bản xem UI. Thiết bị mới mặc định OFFLINE.</p>
            <form id="dm-node-form" method="post" novalidate>
                <div class="um-form-grid">
                    <div class="um-field"><label for="dm-node-id">Device ID</label><input id="dm-node-id" type="text" placeholder="ESP32-ROOM-103" maxlength="64" required><small>Chữ, số, dấu gạch ngang hoặc gạch dưới. ID cố định sau khi thêm.</small></div>
                    <div class="um-field"><label for="dm-node-name">Tên gợi nhớ</label><input id="dm-node-name" type="text" placeholder="Tủ điều khiển tầng 2" maxlength="100" required></div>
                    <div class="um-field"><label for="dm-node-ip">Địa chỉ IPv4</label><input id="dm-node-ip" type="text" inputmode="decimal" placeholder="192.168.1.160" maxlength="15" required></div>
                    <div class="um-field"><label for="dm-node-location">Vị trí lắp đặt</label><input id="dm-node-location" type="text" placeholder="Xưởng C, Kệ A3" maxlength="100" required></div>
                    <div class="um-field"><label for="dm-node-host">Hostname (tùy chọn)</label><input id="dm-node-host" type="text" placeholder="esp32-room-103.local" maxlength="253" autocapitalize="none" spellcheck="false"></div>
                    <div class="um-field"><label for="dm-node-token">Token thiết bị (tùy chọn)</label><input id="dm-node-token" type="password" placeholder="Nhập token ghép nối" autocomplete="off"><small>Token không được lưu trong bản xem UI.</small></div>
                </div>
                <p class="um-form-error" id="dm-node-error" role="alert" hidden></p>
                <div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary" data-close-dialog="dm-node-dialog">Hủy bỏ</button><button type="submit" class="um-button um-button-primary" id="dm-node-save" disabled>Lưu Thiết Bị</button></div>
            </form>
        </dialog>

        <dialog class="um-dialog" id="dm-switch-dialog" aria-labelledby="dm-switch-dialog-title">
            <div class="um-dialog-heading"><h2 id="dm-switch-dialog-title">Gán Switch / Relay Vào ESP32</h2><button type="button" class="um-icon-button" data-close-dialog="dm-switch-dialog" aria-label="Đóng form switch"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
            <p class="um-dialog-description">Liên kết một chân GPIO với một relay. Cấu hình khởi động được áp dụng khi mô phỏng reboot.</p>
            <form id="dm-switch-form" method="post" novalidate>
                <div class="um-form-grid">
                    <div class="um-field um-field-full"><label for="dm-switch-parent">Thiết bị ESP32 cha</label><select id="dm-switch-parent" required></select></div>
                    <div class="um-field"><label for="dm-switch-gpio">Chân GPIO</label><input id="dm-switch-gpio" type="text" inputmode="numeric" placeholder="GPIO 18 hoặc 18" required><small>Chân GPIO cần phù hợp với bo mạch sử dụng.</small></div>
                    <div class="um-field"><label for="dm-switch-wiring">Kiểu relay</label><select id="dm-switch-wiring"><option value="NO">Thường Mở (NO)</option><option value="NC">Thường Đóng (NC)</option></select></div>
                    <div class="um-field um-field-full"><label for="dm-switch-name">Tên switch / tải điện</label><input id="dm-switch-name" type="text" placeholder="Quạt làm mát tủ điện" maxlength="100" required></div>
                    <div class="um-field"><label for="dm-switch-type">Loại tải</label><select id="dm-switch-type"><option value="light">Chiếu sáng</option><option value="fan">Quạt / Thông gió</option><option value="motor">Động cơ / Máy bơm</option><option value="other">Tải khác</option></select></div>
                    <div class="um-field"><label for="dm-switch-boot">Trạng thái khởi động</label><select id="dm-switch-boot"><option value="OFF">Tắt mặc định (OFF / LOW)</option><option value="ON">Bật mặc định (ON / HIGH)</option><option value="LAST">Ghi nhớ trạng thái trước đó</option></select></div>
                    <div class="um-field um-field-full"><label for="dm-switch-rating">Ghi chú tải (tùy chọn)</label><input id="dm-switch-rating" type="text" placeholder="10A / 250VAC" maxlength="100"></div>
                </div>
                <p class="um-form-error" id="dm-switch-error" role="alert" hidden></p>
                <div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary" data-close-dialog="dm-switch-dialog">Hủy bỏ</button><button type="submit" class="um-button dm-button-green" id="dm-switch-save" disabled>Lưu Cấu Hình Switch</button></div>
            </form>
        </dialog>

        <dialog class="um-dialog um-dialog-small" id="dm-schedule-dialog" aria-labelledby="dm-schedule-title">
            <div class="um-dialog-heading"><h2 id="dm-schedule-title">Hẹn Giờ Switch</h2><button type="button" class="um-icon-button" data-close-dialog="dm-schedule-dialog" aria-label="Đóng hẹn giờ"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
            <p class="um-dialog-description" id="dm-schedule-description"></p>
            <form id="dm-schedule-form" method="post" novalidate><div class="um-form-grid"><div class="um-field"><label for="dm-schedule-action">Thao tác</label><select id="dm-schedule-action"><option value="ON">Bật (ON)</option><option value="OFF">Tắt (OFF)</option></select></div><div class="um-field"><label for="dm-schedule-time">Thời gian</label><input id="dm-schedule-time" type="datetime-local" required></div></div><p class="dm-dialog-note">Lịch minh họa chỉ hiển thị trong phiên xem UI, không tự gửi lệnh đến ESP32.</p><p class="um-form-error" id="dm-schedule-error" role="alert" hidden></p><div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary" id="dm-remove-schedule" disabled>Xóa hẹn giờ</button><button type="submit" class="um-button um-button-primary" id="dm-schedule-save" disabled>Lưu Hẹn Giờ</button></div></form>
        </dialog>
        <dialog class="um-dialog um-dialog-small" id="dm-confirm-dialog" aria-labelledby="dm-confirm-title" aria-describedby="dm-confirm-description"><div class="um-dialog-heading"><h2 id="dm-confirm-title"></h2><button type="button" class="um-icon-button" data-close-dialog="dm-confirm-dialog" aria-label="Đóng xác nhận"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div><p class="um-dialog-description" id="dm-confirm-description"></p><form id="dm-confirm-form" method="post"><div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary" data-close-dialog="dm-confirm-dialog">Hủy</button><button type="submit" class="um-button um-button-danger" id="dm-confirm-submit">Xác Nhận</button></div></form></dialog>
        <p class="um-sr-only" id="dm-feedback" role="status" aria-live="polite" aria-atomic="true"></p>
        <div class="um-toast" id="dm-toast" hidden><span id="dm-toast-message"></span><button type="button" class="um-icon-button" id="dm-dismiss-toast" aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
    </body>
</html>
