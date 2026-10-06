<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="activePage" value="CONTROL_HISTORY" scope="request" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Lịch Sử Điều Khiển | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/controlHistoryCss.css">
        <script src="${pageContext.request.contextPath}/js/controlHistory.js" defer></script>
    </head>
    <body class="user-manager-page control-history-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">NHẬT KÝ ĐIỀU KHIỂN</span>
            <div class="um-topbar-user"><span class="ch-role">Vai trò: <span class="um-admin-tag">Admin</span></span><span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user" aria-hidden="true"></i></span></div>
        </header>
        <main class="um-main ch-main" id="main-content">
            <header class="ch-page-heading"><div class="ch-title-line"><h1>Lịch Sử Điều Khiển Thiết Bị</h1><span class="ch-version-tag">AUDIT_LOG_V2</span></div><p>Tra cứu thao tác bật/tắt rơ-le từ Web Interface, Servlet API và Scheduler.</p></header>
            <section class="ch-log-panel" aria-labelledby="ch-log-title">
                <header class="ch-panel-heading"><div class="ch-panel-identity"><i class="fa-solid fa-table-list" aria-hidden="true"></i><h2 id="ch-log-title">Nhật Ký Tác Vụ Rơ-le</h2><span class="ch-record-count" id="ch-record-count">10 bản ghi gần nhất</span></div><div class="ch-refresh-group"><button type="button" class="ch-refresh" id="ch-refresh" aria-label="Làm mới bản xem nhật ký" title="Làm mới bản xem UI, giữ các bộ lọc" disabled><i class="fa-solid fa-arrows-rotate" aria-hidden="true"></i></button><span id="ch-updated">ĐỌC DỮ LIỆU: Chưa tải</span></div></header>
                <div class="ch-toolbar">
                    <div class="ch-search"><label class="um-sr-only" for="ch-search">Tìm theo mã log, người thực hiện, thiết bị, switch hoặc GPIO</label><i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i><input id="ch-search" type="search" placeholder="Tìm #ID, tài khoản, switch, GPIO..." autocomplete="off" aria-controls="ch-rows"></div>
                    <label class="um-sr-only" for="ch-device-filter">Lọc thiết bị đích</label><select id="ch-device-filter" aria-controls="ch-rows"><option value="all">Thiết bị: Tất cả</option></select>
                    <label class="um-sr-only" for="ch-command-filter">Lọc lệnh điều khiển</label><select id="ch-command-filter" aria-controls="ch-rows"><option value="all">Lệnh: Tất cả</option><option value="ON">Bật (ON)</option><option value="OFF">Tắt (OFF)</option></select>
                    <label class="um-sr-only" for="ch-result-filter">Lọc kết quả điều khiển</label><select id="ch-result-filter" aria-controls="ch-rows"><option value="all">Kết quả: Tất cả</option><option value="success">Thành công</option><option value="error">Lỗi</option><option value="TIMEOUT">TIMEOUT</option><option value="NODE_OFFLINE">NODE_OFFLINE</option></select>
                    <button type="button" class="ch-clear-filters" id="ch-clear-filters" disabled>Xóa lọc</button>
                </div>
                <div class="ch-table-scroll" tabindex="0" role="region" aria-label="Bảng lịch sử điều khiển, có thể cuộn ngang trên màn hình nhỏ">
                    <table class="ch-table"><caption class="um-sr-only">10 bản ghi lịch sử minh họa. Chọn mã log để xem chi tiết.</caption><colgroup><col style="width:10%"><col style="width:16%"><col style="width:16%"><col style="width:14%"><col style="width:20%"><col style="width:10%"><col style="width:14%"></colgroup><thead><tr><th scope="col">#ID</th><th scope="col" id="ch-time-heading" aria-sort="descending"><button type="button" id="ch-sort-time" aria-label="Sắp xếp thời gian từ cũ đến mới" disabled>THỜI GIAN <i id="ch-sort-icon" class="fa-solid fa-arrow-down-short-wide" aria-hidden="true"></i></button></th><th scope="col">NGƯỜI THỰC HIỆN</th><th scope="col">THIẾT BỊ ĐÍCH</th><th scope="col">SWITCH &amp; GPIO</th><th scope="col">LỆNH</th><th scope="col">KẾT QUẢ (RTT)</th></tr></thead><tbody id="ch-rows"></tbody></table>
                </div>
                <footer class="ch-panel-footer"><span id="ch-result-count" role="status" aria-live="polite"></span><span>Giờ Việt Nam (UTC+7) · Chọn #ID để xem chi tiết</span></footer>
            </section>
            <p class="ch-preview-note"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Bản xem UI dùng 10 bản ghi minh họa theo mẫu. Làm mới chỉ cập nhật bản xem, chưa lấy nhật ký từ database hoặc ESP32.</p>
            <noscript><p class="ch-empty">Bật JavaScript để xem nhật ký, tìm kiếm và mở chi tiết bản ghi.</p></noscript>
        </main>
        <dialog class="um-dialog ch-trace-dialog" id="ch-trace-dialog" aria-labelledby="ch-trace-title" aria-describedby="ch-trace-description">
            <div class="um-dialog-heading"><h2 id="ch-trace-title"><i class="fa-solid fa-code" aria-hidden="true"></i> Chi Tiết Nhật Ký</h2><button type="button" class="um-icon-button" id="ch-trace-close" aria-label="Đóng chi tiết nhật ký"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
            <p class="um-dialog-description" id="ch-trace-description"></p>
            <dl class="ch-trace-summary"><div><dt>NGUỒN</dt><dd id="ch-trace-source"></dd></div><div><dt>GIAO THỨC MINH HỌA</dt><dd id="ch-trace-protocol"></dd></div><div><dt>KẾT QUẢ</dt><dd id="ch-trace-result"></dd></div></dl>
            <h3 class="ch-payload-title">Telemetry Trace Payload <span id="ch-trace-id"></span></h3>
            <pre class="ch-payload" tabindex="0" aria-label="Nội dung JSON của bản ghi"><code id="ch-trace-json"></code></pre>
            <p class="ch-trace-note">Payload minh họa, không phải phản hồi từ thiết bị thật.</p>
            <div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary" id="ch-copy-json" disabled><i class="fa-regular fa-copy" aria-hidden="true"></i> Sao chép JSON</button><button type="button" class="um-button um-button-primary" id="ch-trace-done">Đóng</button></div>
        </dialog>
        <p class="um-sr-only" id="ch-feedback" role="status" aria-live="polite" aria-atomic="true"></p>
        <div class="um-toast" id="ch-toast" hidden><span id="ch-toast-message"></span><button type="button" class="um-icon-button" id="ch-dismiss-toast" aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
    </body>
</html>
