<%-- 
    Document   : DeviceControl
    Created on : Oct 5, 2026, 6:22:16 PM
    Author     : ltrun
--%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<c:set var="activePage" value="DEVICE_CONTROL" scope="request" />
<%-- Show the reference's two sample relays until a controller supplies a list. --%>
<c:set var="dcRelays" value="${requestScope.ds}" />
<c:if test="${requestScope.ds eq null}">
    <c:set var="dcRelays" value="${[
        {'id': 'uv-21', 'name': 'Đèn UV khử khuẩn', 'gpio': 21, 'node': 'ESP32-03', 'location': 'Kho Thiết Bị', 'mode': 'AUTO_CYCLE_MODE', 'kind': 'uv', 'status': 'ON'},
        {'id': 'valve-22', 'name': 'Van cấp nước TĐ', 'gpio': 22, 'node': 'ESP32-03', 'location': 'Kho Thiết Bị', 'mode': 'VALVE_SOLENOID', 'kind': 'valve', 'status': 'OFF'}
    ]}" />
</c:if>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Điều khiển công tắc | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceControlcss.css?v=20261006-relays">
        <script src="${pageContext.request.contextPath}/js/deviceControl.js" defer></script>
    </head>
    <body class="user-manager-page device-control-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">ĐIỀU KHIỂN &amp; GIÁM SÁT</span>
            <div class="um-topbar-user">
                <span class="dc-topbar-role">Vai trò: <span class="um-admin-tag">Admin</span></span>
                <span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user" aria-hidden="true"></i></span>
            </div>
        </header>
        <main class="um-main dc-main" id="main-content">
            <header class="dc-page-heading">
                <span class="dc-heading-dot" aria-hidden="true"></span>
                <h1 id="dc-title">Bảng Điều Khiển Công Tắc <span>(Device Control)</span></h1>
            </header>
            <c:choose>
                <c:when test="${empty dcRelays}">
                    <section class="dc-empty-state" aria-labelledby="dc-empty-title">
                        <span class="dc-empty-icon"><i class="fa-solid fa-sliders" aria-hidden="true"></i></span>
                        <h2 id="dc-empty-title">Không có thiết bị nào.</h2>
                        <p>Các công tắc sẽ hiển thị tại đây khi danh sách thiết bị được tải.</p>
                    </section>
                </c:when>
                <c:otherwise>
                    <section class="dc-device-grid" id="dc-relays" aria-label="Danh sách công tắc">
                        <c:forEach var="relay" items="${dcRelays}" varStatus="row">
                            <c:set var="dcKind" value="${relay.kind eq 'uv' ? 'uv' : relay.kind eq 'valve' ? 'valve' : 'relay'}" />
                            <c:set var="dcOn" value="${relay.status ne 'OFF'}" />
                            <article class="dc-relay-card" id="dc-card-${row.index}" data-kind="${dcKind}" data-on="${dcOn}" aria-labelledby="dc-name-${row.index}">
                                <div class="dc-card-heading">
                                    <div class="dc-relay-meta">
                                        <span class="dc-gpio"><c:choose><c:when test="${not empty relay.gpio}">GPIO <c:out value="${relay.gpio}" /></c:when><c:otherwise>ID: <c:out value="${relay.id}" /></c:otherwise></c:choose></span>
                                        <p class="dc-location"><c:choose><c:when test="${not empty relay.node}"><c:out value="${relay.node}" /><c:if test="${not empty relay.location}"> &bull; <c:out value="${relay.location}" /></c:if></c:when><c:otherwise>Thiết bị trong hệ thống</c:otherwise></c:choose></p>
                                    </div>
                                    <c:if test="${dcKind eq 'valve'}"><span class="dc-valve-badge"><span aria-hidden="true"></span><span class="dc-valve-state">${dcOn ? 'OPEN' : 'CLOSED'}</span></span></c:if>
                                </div>
                                <div class="dc-card-body">
                                    <span class="dc-relay-icon"><i class="fa-solid ${dcKind eq 'uv' ? 'fa-spray-can-sparkles' : dcKind eq 'valve' ? 'fa-faucet' : 'fa-microchip'}" aria-hidden="true"></i></span>
                                    <div class="dc-relay-identity">
                                        <h2 class="dc-relay-name" id="dc-name-${row.index}"><c:out value="${relay.name}" /></h2>
                                        <span class="dc-relay-mode"><c:out value="${empty relay.mode ? 'MANUAL_CONTROL' : relay.mode}" /></span>
                                    </div>
                                </div>
                                <div class="dc-card-footer">
                                    <div class="dc-status-box"><span class="dc-status-label">TRẠNG THÁI</span><span class="dc-relay-state">${dcOn ? 'ON' : 'OFF'}</span></div>
                                    <div class="dc-controls">
                                        <button type="button" class="dc-action-button" disabled><c:choose><c:when test="${dcKind eq 'uv'}">${dcOn ? 'TẮT SỚM' : 'BẬT ĐÈN'}</c:when><c:when test="${dcKind eq 'valve'}">${dcOn ? 'ĐÓNG VAN' : 'MỞ VAN'}</c:when><c:otherwise>${dcOn ? 'TẮT' : 'BẬT'}</c:otherwise></c:choose></button>
                                        <button type="button" class="dc-switch" role="switch" aria-checked="${dcOn}" aria-labelledby="dc-name-${row.index}" disabled><span class="dc-switch-track" aria-hidden="true"><span></span></span></button>
                                    </div>
                                </div>
                            </article>
                        </c:forEach>
                    </section>
                </c:otherwise>
            </c:choose>
            <p class="dc-preview-note"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Bản xem UI. Thao tác chỉ thay đổi trạng thái minh họa trên trang, chưa gửi lệnh đến ESP32. Tải lại trang sẽ khôi phục trạng thái ban đầu.</p>
            <noscript><p class="dc-noscript">Bật JavaScript để thử bật/tắt công tắc trong bản xem UI.</p></noscript>
        </main>
        <p class="um-sr-only" id="dc-feedback" role="status" aria-live="polite" aria-atomic="true"></p>
        <div class="um-toast" id="dc-toast" hidden><span id="dc-toast-message"></span><button type="button" class="um-icon-button" id="dc-dismiss-toast" aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
    </body>
</html>
