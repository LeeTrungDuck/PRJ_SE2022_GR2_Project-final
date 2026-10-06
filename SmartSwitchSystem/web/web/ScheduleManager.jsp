<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="activePage" value="SCHEDULE_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Lịch Hẹn Giờ | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/scheduleManagerCss.css">
        <script src="${pageContext.request.contextPath}/js/scheduleManager.js" defer></script>
    </head>
    <body class="user-manager-page schedule-manager-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">QUẢN LÝ LỊCH HẸN GIỜ</span>
            <div class="um-topbar-user"><span class="sm-role">Vai trò: <span class="um-admin-tag">Admin</span></span><span class="um-profile-icon" aria-label="Giao diện quản trị viên">NA</span></div>
        </header>
        <main class="um-main sm-main" id="main-content">
            <div class="sm-context-bar"><span class="sm-clock" id="sm-clock">Giờ trình duyệt</span><span class="sm-context">Đang cấu hình: <strong id="sm-current-switch">ESP32-01 (Xưởng A) · GPIO 16</strong></span></div>
            <header class="sm-page-heading">
                <div class="sm-heading-identity"><span class="sm-heading-icon"><i class="fa-solid fa-stopwatch" aria-hidden="true"></i></span><div><h1>Lịch Hẹn Giờ <span>(Schedule Management)</span></h1><p>Lập lịch bật/tắt switch theo giờ và ngày lặp trong tuần.</p></div></div>
                <div class="sm-next-run"><i class="fa-solid fa-hourglass-half" aria-hidden="true"></i><span id="sm-next-summary">LẦN CHẠY KẾ: Đang tính...</span></div>
            </header>
            <div class="sm-layout">
                <section class="sm-list-section" aria-labelledby="sm-list-title">
                    <h2 class="um-sr-only" id="sm-list-title">Danh sách lịch hẹn giờ</h2>
                    <div class="sm-toolbar">
                        <div class="sm-search"><label class="um-sr-only" for="sm-search">Tìm theo tên lịch, switch, ESP32 hoặc GPIO</label><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><input id="sm-search" type="search" placeholder="Tìm lịch hẹn, tên tải, GPIO..." autocomplete="off" aria-controls="sm-schedules"></div>
                        <div class="sm-status-filters" role="group" aria-label="Lọc trạng thái lịch"><button type="button" data-schedule-status="all" aria-pressed="true">Tất cả (<span id="sm-count-all">6</span>)</button><button type="button" data-schedule-status="enabled" aria-pressed="false">Đang bật (<span id="sm-count-enabled">4</span>)</button><button type="button" data-schedule-status="paused" aria-pressed="false">Tạm dừng (<span id="sm-count-paused">2</span>)</button></div>
                        <button type="button" class="sm-icon-button" id="sm-refresh" title="Cập nhật thời gian dự kiến" aria-label="Cập nhật thời gian dự kiến" disabled><i class="fa-solid fa-arrows-rotate" aria-hidden="true"></i></button>
                    </div>
                    <div class="sm-relay-filter"><i class="fa-solid fa-filter" aria-hidden="true"></i><label for="sm-switch-filter">Lọc theo rơ-le:</label><select id="sm-switch-filter" aria-controls="sm-schedules"><option value="all">Tất cả switch</option></select><button type="button" id="sm-clear-filters" hidden>Xóa bộ lọc</button></div>
                    <div class="sm-schedules" id="sm-schedules"></div>
                    <p class="sm-result" id="sm-result" tabindex="-1" role="status" aria-live="polite"></p>
                    <noscript><p class="sm-empty">Bật JavaScript để xem danh sách và thử các thao tác hẹn giờ.</p></noscript>
                </section>
                <aside class="sm-side-panel" aria-labelledby="sm-form-title">
                    <section class="sm-editor">
                        <header class="sm-editor-heading"><h2 id="sm-form-title"><i class="fa-solid fa-calendar-plus" aria-hidden="true"></i> Thêm Lịch Hẹn Giờ Mới</h2><span class="sm-editor-tag" id="sm-form-tag">AUTO</span></header>
                        <form id="sm-form" method="post" novalidate>
                            <div class="sm-form-field"><label for="sm-switch">SWITCH / RƠ-LE ĐIỀU KHIỂN</label><select id="sm-switch" required></select><p class="sm-switch-detail" id="sm-switch-detail"></p></div>
                            <fieldset class="sm-fieldset"><legend>HÀNH ĐỘNG KHI KÍCH HOẠT</legend><div class="sm-action-options"><input class="um-sr-only" type="radio" name="scheduleAction" id="sm-action-on" value="ON" checked><label for="sm-action-on"><i class="fa-solid fa-plug" aria-hidden="true"></i> BẬT (ON)</label><input class="um-sr-only" type="radio" name="scheduleAction" id="sm-action-off" value="OFF"><label for="sm-action-off"><i class="fa-solid fa-power-off" aria-hidden="true"></i> TẮT (OFF)</label></div></fieldset>
                            <div class="sm-form-field"><label for="sm-time">GIỜ THỰC THI <span>Định dạng 24h</span></label><div class="sm-time-row"><input id="sm-time" type="time" value="06:30" step="60" required><button type="button" data-time-add="15" aria-label="Thêm 15 phút">+15m</button><button type="button" data-time-add="60" aria-label="Thêm 1 giờ">+1h</button></div></div>
                            <fieldset class="sm-fieldset"><legend>LẶP LẠI <span id="sm-day-count">5 ngày đã chọn</span></legend><div class="sm-presets" role="group" aria-label="Chọn kiểu lặp"><button type="button" data-repeat-preset="daily" aria-pressed="false">Hằng ngày</button><button type="button" data-repeat-preset="weekdays" aria-pressed="true">T2 - T6</button><button type="button" data-repeat-preset="weekend" aria-pressed="false">Cuối tuần</button><button type="button" data-repeat-preset="once" aria-pressed="false">Một lần</button></div><div class="sm-day-buttons" id="sm-day-buttons" role="group" aria-label="Ngày lặp trong tuần"><button type="button" data-repeat-day="1" aria-pressed="true" aria-label="Thứ hai">T2</button><button type="button" data-repeat-day="2" aria-pressed="true" aria-label="Thứ ba">T3</button><button type="button" data-repeat-day="3" aria-pressed="true" aria-label="Thứ tư">T4</button><button type="button" data-repeat-day="4" aria-pressed="true" aria-label="Thứ năm">T5</button><button type="button" data-repeat-day="5" aria-pressed="true" aria-label="Thứ sáu">T6</button><button type="button" data-repeat-day="6" aria-pressed="false" aria-label="Thứ bảy">T7</button><button type="button" data-repeat-day="0" aria-pressed="false" aria-label="Chủ nhật">CN</button></div></fieldset>
                            <div class="sm-form-field" id="sm-date-field" hidden><label for="sm-date">NGÀY CHẠY MỘT LẦN</label><input id="sm-date" type="date"><small>Chọn ngày và giờ trong tương lai.</small></div>
                            <div class="sm-form-field"><label for="sm-name">TÊN LỊCH HẸN</label><input id="sm-name" type="text" placeholder="Ví dụ: Hút mùi trước ca sản xuất" maxlength="120" required></div>
                            <p class="um-form-error" id="sm-form-error" role="alert" hidden></p>
                            <div class="sm-form-footer"><button type="button" class="um-button um-button-secondary" id="sm-reset">Hủy / Đặt lại</button><button type="submit" class="um-button um-button-primary" id="sm-save" disabled><i class="fa-solid fa-circle-check" aria-hidden="true"></i> Lưu Lịch Hẹn Giờ</button></div>
                        </form>
                    </section>
                    <section class="sm-guidance" aria-labelledby="sm-guidance-title"><h2 id="sm-guidance-title"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Lịch tự động theo ngày lặp</h2><p>Thời gian dự kiến được tính theo đồng hồ và múi giờ trình duyệt. Lịch tạm dừng sẽ không nằm trong lần chạy kế.</p><p>Đây là bản xem UI: lịch chỉ giữ trong trang hiện tại. Lưu và chạy thử chưa kết nối database, MQTT hoặc RTC của ESP32.</p></section>
                </aside>
            </div>
        </main>
        <dialog class="um-dialog um-dialog-small" id="sm-delete-dialog" aria-labelledby="sm-delete-title" aria-describedby="sm-delete-description"><div class="um-dialog-heading"><h2 id="sm-delete-title">Xóa Lịch Hẹn Giờ?</h2><button type="button" class="um-icon-button" id="sm-delete-close" aria-label="Đóng xác nhận"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div><p class="um-dialog-description" id="sm-delete-description"></p><form id="sm-delete-form" method="post"><div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary" id="sm-delete-cancel">Hủy</button><button type="submit" class="um-button um-button-danger">Xóa Lịch</button></div></form></dialog>
        <p class="um-sr-only" id="sm-feedback" role="status" aria-live="polite" aria-atomic="true"></p>
        <div class="um-toast" id="sm-toast" hidden><span id="sm-toast-message"></span><button type="button" class="um-icon-button" id="sm-dismiss-toast" aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
    </body>
</html>
