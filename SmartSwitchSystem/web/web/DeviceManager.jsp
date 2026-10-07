<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:if test="${empty requestScope.ui}">
<c:redirect url="/MainController">
<c:param name="action" value="DEVICE_MANAGER" />
</c:redirect>
</c:if>
<c:set var="activePage" value="DEVICE_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Quản Lý Thiết Bị & GPIO | ESP32 IoT Hub</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceManagerCss.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
</head>
<body class="user-manager-page device-manager-page">
<div class="ui-shell" ${ui.dialog ? 'inert' : ''}>
<%@ include file="sideBar.jspf" %>
<header class="um-topbar">
<span class="um-section-label">Quản Lý Thiết Bị & GPIO</span>
<div class="um-topbar-user">
<span class="um-admin-tag">Admin</span>
<span class="um-profile-icon" aria-label="Tài khoản minh họa">
<i class="fa-regular fa-user" aria-hidden="true">
</i>
</span>
</div>
</header>
<main class="um-main dm-main" id="main-content">
<c:if test="${not empty uiMessage}">
<p class="ui-notice" role="status">
<c:out value="${uiMessage}" />
</p>
</c:if>
<c:if test="${not empty uiError and not ui.dialog}">
<p class="ui-notice ui-error" role="alert">
<c:out value="${uiError}" />
</p>
</c:if>
<h1 class="um-sr-only">Quản Lý Thiết Bị &amp; Switch</h1>
<section class="dm-panel">
<header class="dm-panel-heading">
<div class="dm-section-identity">
<span class="dm-section-icon">
<i class="fa-solid fa-microchip" aria-hidden="true">
</i>
</span>
<div>
<div class="dm-title-line">
<h2>1. Danh Sách Vi Điều Khiển ESP32</h2>
<span class="dm-section-tag">Master Nodes</span>
</div>
<p>Quản lý bo mạch ESP32, địa chỉ mạng và cấu hình trong phiên minh họa.</p>
</div>
</div>
<a class="um-button um-button-primary" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=node">
<i class="fa-solid fa-circle-plus" aria-hidden="true">
</i> Thêm Thiết Bị ESP32 Mới</a>
</header>
<form class="dm-toolbar" action="${pageContext.request.contextPath}/MainController" method="get">
<input name="action" value="DEVICE_MANAGER" type="hidden">
<input name="sq" value="<c:out value='${ui.sq}'/>" type="hidden">
<input name="parent" value="<c:out value='${ui.parent}'/>" type="hidden">
<input name="type" value="<c:out value='${ui.type}'/>" type="hidden">
<div class="dm-search">
<label class="um-sr-only" for="dm-node-search">Tìm thiết bị</label>
<i class="fa-solid fa-magnifying-glass" aria-hidden="true">
</i>
<input id="dm-node-search" name="q" type="search" value="<c:out value='${ui.q}'/>" placeholder="Tìm Device ID, tên, IP, vị trí...">
</div>
<label class="um-sr-only" for="dm-status">Trạng thái</label>
<select id="dm-status" name="status">
<option value="all">Tất cả (${ui.nodeTotal})</option>
<option value="online" ${ui.status eq 'online' ? 'selected' : ''}>Online (${ui.nodeOnline})</option>
<option value="offline" ${ui.status eq 'offline' ? 'selected' : ''}>Offline (${ui.nodeOffline})</option>
</select>
<label class="um-sr-only" for="dm-location">Vị trí</label>
<select id="dm-location" name="location">
<option value="all">Tất cả vị trí xưởng</option>
<c:forEach var="location" items="${ui.locations}">
<option value="<c:out value='${location}'/>" ${ui.location eq location ? 'selected' : ''}>
<c:out value="${location}"/>
</option>
</c:forEach>
</select>
<button class="ui-get-button" type="submit">Lọc</button>
</form>
<div class="dm-table-scroll" tabindex="0" role="region" aria-label="Bảng ESP32 có thể cuộn ngang">
<table class="dm-table dm-nodes-table">
<thead>
<tr>
<th scope="col">DEVICE ID / TÊN GỢI NHỚ</th>
<th scope="col">HOSTNAME / ĐỊA CHỈ IP</th>
<th scope="col">TRẠNG THÁI &amp; ĐỘ TRỄ</th>
<th scope="col">THAO TÁC</th>
</tr>
</thead>
<tbody>
<c:forEach var="node" items="${ui.nodes}">
<c:url var="nodeEdit" value="/MainController">
<c:param name="action" value="DEVICE_MANAGER"/>
<c:param name="form" value="node"/>
<c:param name="id" value="${node.id}"/>
</c:url>
<tr class="${node.online ? '' : 'is-offline'}">
<td>
<div class="dm-item">
<span class="dm-item-icon">
<i class="fa-solid fa-microchip" aria-hidden="true">
</i>
</span>
<div>
<strong class="dm-item-name">
<c:out value="${node.name}"/>
</strong>
<span class="dm-item-subtitle">
<c:out value="${node.id}"/>
</span>
</div>
</div>
</td>
<td>
<strong class="dm-ip">
<c:out value="${node.ip}"/>
</strong>
<span class="dm-hostname">
<c:out value="${node.hostname}"/>
</span>
</td>
<td>
<span class="dm-node-status ${node.online ? 'is-online' : 'is-offline'}">● ${node.online ? 'ONLINE' : 'OFFLINE'}</span>
<span class="dm-latency">(${node.online ? node.latency : node.offlineReason}${node.online ? 'ms' : ''})</span>
</td>
<td>
<div class="dm-actions">
<form class="ui-inline-form" action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<input name="id" value="<c:out value='${node.id}'/>" type="hidden">
<button class="dm-icon-button" type="submit" name="uiOp" value="pingNode" aria-label="Kiểm tra trạng thái ${fn:escapeXml(node.name)}">
<i class="fa-solid fa-network-wired" aria-hidden="true">
</i>
</button>
</form>
<a class="dm-icon-button" href="<c:out value='${nodeEdit}'/>" aria-label="Sửa ${fn:escapeXml(node.name)}">
<i class="fa-solid fa-gear" aria-hidden="true">
</i>
</a>
<c:if test="${node.online}">
<a class="dm-icon-button" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=rebootNode&amp;id=${node.id}" aria-label="Mô phỏng reboot ${fn:escapeXml(node.name)}">
<i class="fa-solid fa-rotate-right" aria-hidden="true">
</i>
</a>
</c:if>
<a class="dm-icon-button" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=deleteNode&amp;id=${node.id}" aria-label="Xóa ${fn:escapeXml(node.name)}">
<i class="fa-solid fa-trash-can" aria-hidden="true">
</i>
</a>
</div>
</td>
</tr>
</c:forEach>
<c:if test="${empty ui.nodes}">
<tr>
<td colspan="4" class="ui-empty">Không có thiết bị phù hợp.</td>
</tr>
</c:if>
</tbody>
</table>
</div>
<p class="dm-result-count">Hiển thị ${fn:length(ui.nodes)} / ${ui.nodeTotal} thiết bị</p>
</section>
<section class="dm-panel">
<header class="dm-panel-heading">
<div class="dm-section-identity">
<span class="dm-section-icon dm-section-icon-green">
<i class="fa-solid fa-plug" aria-hidden="true">
</i>
</span>
<div>
<div class="dm-title-line">
<h2>2. Danh Sách Cấu Hình Switch Con (GPIO Relay Mapping)</h2>
<span class="dm-section-tag dm-section-tag-green">GPIO Actuators</span>
</div>
<p>Ánh xạ switch vào GPIO của từng bo mạch.</p>
</div>
</div>
<c:if test="${not empty ui.allNodes}">
<a class="um-button dm-button-green" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=switch">Gán Switch / Relay Vào ESP32</a>
</c:if>
</header>
<form class="dm-toolbar" action="${pageContext.request.contextPath}/MainController" method="get">
<input name="action" value="DEVICE_MANAGER" type="hidden">
<input name="q" value="<c:out value='${ui.q}'/>" type="hidden">
<input name="status" value="<c:out value='${ui.status}'/>" type="hidden">
<input name="location" value="<c:out value='${ui.location}'/>" type="hidden">
<div class="dm-search">
<label class="um-sr-only" for="dm-switch-search">Tìm switch</label>
<i class="fa-solid fa-magnifying-glass" aria-hidden="true">
</i>
<input id="dm-switch-search" name="sq" type="search" value="<c:out value='${ui.sq}'/>" placeholder="Tìm tên tải, GPIO, thiết bị...">
</div>
<label class="um-sr-only" for="dm-parent">ESP32 cha</label>
<select id="dm-parent" name="parent">
<option value="all">ESP32 cha: Tất cả</option>
<c:forEach var="node" items="${ui.allNodes}">
<option value="${node.id}" ${ui.parent eq node.id ? 'selected' : ''}>
<c:out value="${node.id}"/>
</option>
</c:forEach>
</select>
<label class="um-sr-only" for="dm-type">Loại tải</label>
<select id="dm-type" name="type">
<option value="all">Loại tải: Tất cả</option>
<option value="light" ${ui.type eq 'light' ? 'selected' : ''}>Đèn</option>
<option value="fan" ${ui.type eq 'fan' ? 'selected' : ''}>Quạt</option>
<option value="motor" ${ui.type eq 'motor' ? 'selected' : ''}>Bơm / Động cơ</option>
<option value="other" ${ui.type eq 'other' ? 'selected' : ''}>Khác</option>
</select>
<button class="ui-get-button" type="submit">Lọc</button>
</form>
<div class="dm-table-scroll" tabindex="0" role="region" aria-label="Bảng switch có thể cuộn ngang">
<table class="dm-table dm-switches-table">
<thead>
<tr>
<th scope="col">TÊN SWITCH (TẢI ĐIỆN)</th>
<th scope="col">THUỘC ESP32 NÀO</th>
<th scope="col">GPIO</th>
<th scope="col">KHỞI ĐỘNG</th>
<th scope="col">TRẠNG THÁI</th>
<th scope="col">THAO TÁC</th>
</tr>
</thead>
<tbody>
<c:forEach var="sw" items="${ui.switches}">
<tr>
<td>
<div class="dm-item">
<span class="dm-item-icon">
<i class="fa-solid fa-${sw.icon}" aria-hidden="true">
</i>
</span>
<div>
<strong class="dm-item-name">
<c:out value="${sw.name}"/>
</strong>
<span class="dm-item-subtitle">Relay ${sw.wiring} · <c:out value="${sw.rating}"/>
</span>
</div>
</div>
</td>
<td>
<span class="dm-parent-id">
<c:out value="${sw.nodeId}"/>
</span>
<span class="dm-item-subtitle">
<c:out value="${sw.parentIp}"/>
</span>
</td>
<td>
<code class="dm-gpio">GPIO ${sw.gpio}</code>
</td>
<td>
<span class="dm-boot ${sw.boot eq 'ON' ? 'is-on' : ''}">${sw.boot}</span>
</td>
<td>
<form class="ui-inline-form" action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<input name="id" value="${sw.key}" type="hidden">
<button class="dm-toggle" type="submit" name="uiOp" value="toggleSwitch" role="switch" aria-checked="${sw.on}" aria-label="Đổi trạng thái ${fn:escapeXml(sw.name)}" ${sw.online ? '' : 'disabled'}>
<span class="dm-toggle-track" aria-hidden="true">
<span>
</span>
</span>
<span class="ui-state-label">${sw.on ? 'ON' : 'OFF'}</span>
</button>
</form>
<c:if test="${not sw.online}">
<small>ESP32 OFFLINE</small>
</c:if>
</td>
<td>
<div class="dm-actions">
<a class="dm-icon-button ${empty sw.schedule ? '' : 'is-active'}" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=switchSchedule&amp;id=${sw.key}" aria-label="Hẹn giờ ${fn:escapeXml(sw.name)}">
<i class="fa-regular fa-clock" aria-hidden="true">
</i>
</a>
<form class="ui-inline-form" action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<input name="id" value="${sw.key}" type="hidden">
<button class="dm-icon-button" name="uiOp" value="testSwitch" type="submit" aria-label="Kiểm tra ${fn:escapeXml(sw.name)}" ${sw.online ? '' : 'disabled'}>
<i class="fa-solid fa-bolt" aria-hidden="true">
</i>
</button>
</form>
<a class="dm-icon-button" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=switch&amp;id=${sw.key}" aria-label="Sửa ${fn:escapeXml(sw.name)}">
<i class="fa-solid fa-pen" aria-hidden="true">
</i>
</a>
<a class="dm-icon-button" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER&amp;form=deleteSwitch&amp;id=${sw.key}" aria-label="Xóa ${fn:escapeXml(sw.name)}">
<i class="fa-solid fa-trash-can" aria-hidden="true">
</i>
</a>
</div>
</td>
</tr>
</c:forEach>
<c:if test="${empty ui.switches}">
<tr>
<td colspan="6" class="ui-empty">Không có switch phù hợp.</td>
</tr>
</c:if>
</tbody>
</table>
</div>
<p class="dm-result-count">Hiển thị ${fn:length(ui.switches)} / ${ui.switchTotal} switch</p>
</section>
<footer class="ui-demo-footer">
<p>Dữ liệu minh họa được giữ trong session. Thao tác tải lại trang, chưa ghi database hoặc gửi lệnh đến ESP32.</p>
<form action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<button class="um-button um-button-secondary" type="submit" name="uiOp" value="resetPreview">Đặt lại dữ liệu mẫu</button>
</form>
</footer>
</main>
</div>
<c:if test="${ui.dialog}">
<%@ include file="uiDialogStart.jspf" %>
<form action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<input name="id" value="<c:out value='${ui.form eq "switch" or ui.form eq "switchSchedule" or ui.form eq "deleteSwitch" ? ui.target.key : ui.target.id}'/>" type="hidden">
<c:choose>
<c:when test="${ui.form eq 'node'}">
<div class="um-form-grid">
<div class="um-field">
<label for="dm-node-id">Device ID</label>
<input id="dm-node-id" name="nodeId" value="<c:out value='${empty ui.target.id ? ui.edit.nodeId : ui.target.id}'/>" maxlength="64" required ${empty ui.target.id ? 'autofocus' : 'readonly'}>
</div>
<div class="um-field">
<label for="dm-node-name">Tên thiết bị</label>
<input id="dm-node-name" name="name" value="<c:out value='${ui.edit.name}'/>" maxlength="100" required>
</div>
<div class="um-field">
<label for="dm-ip">Địa chỉ IPv4</label>
<input id="dm-ip" name="ip" value="<c:out value='${ui.edit.ip}'/>" maxlength="15" required placeholder="192.168.1.100">
</div>
<div class="um-field">
<label for="dm-hostname">Hostname (tùy chọn)</label>
<input id="dm-hostname" name="hostname" value="<c:out value='${ui.edit.hostname}'/>" maxlength="253">
</div>
<div class="um-field">
<label for="dm-position">Vị trí</label>
<input id="dm-position" name="location" value="<c:out value='${ui.edit.location}'/>" maxlength="100" required>
</div>
</div>
<p class="um-dialog-description">Thiết bị mới ở trạng thái OFFLINE trong bản minh họa.</p>
</c:when>
<c:when test="${ui.form eq 'switch'}">
<div class="um-form-grid">
<div class="um-field">
<label for="dm-switch-name">Tên Switch / tải điện</label>
<input id="dm-switch-name" name="name" value="<c:out value='${ui.edit.name}'/>" maxlength="100" required autofocus>
</div>
<div class="um-field">
<label for="dm-switch-parent">ESP32 cha</label>
<select id="dm-switch-parent" name="nodeId" required>
<c:forEach var="node" items="${ui.allNodes}">
<option value="${node.id}" ${ui.edit.nodeId eq node.id ? 'selected' : ''}>
<c:out value="${node.id}"/>
</option>
</c:forEach>
</select>
</div>
<div class="um-field">
<label for="dm-switch-gpio">Chân GPIO</label>
<input id="dm-switch-gpio" name="gpio" type="number" min="0" max="39" value="<c:out value='${ui.edit.gpio}'/>" required>
</div>
<div class="um-field">
<label for="dm-wiring">Kiểu Relay</label>
<select id="dm-wiring" name="wiring">
<option value="NO" ${ui.edit.wiring eq 'NO' ? 'selected' : ''}>NO (thường mở)</option>
<option value="NC" ${ui.edit.wiring eq 'NC' ? 'selected' : ''}>NC (thường đóng)</option>
</select>
</div>
<div class="um-field">
<label for="dm-load-type">Loại tải</label>
<select id="dm-load-type" name="type">
<option value="light" ${ui.edit.type eq 'light' ? 'selected' : ''}>Đèn</option>
<option value="fan" ${ui.edit.type eq 'fan' ? 'selected' : ''}>Quạt</option>
<option value="motor" ${ui.edit.type eq 'motor' ? 'selected' : ''}>Bơm / động cơ</option>
<option value="other" ${ui.edit.type eq 'other' ? 'selected' : ''}>Khác</option>
</select>
</div>
<div class="um-field">
<label for="dm-boot">Trạng thái khởi động</label>
<select id="dm-boot" name="boot">
<option value="OFF" ${ui.edit.boot eq 'OFF' ? 'selected' : ''}>OFF (LOW)</option>
<option value="ON" ${ui.edit.boot eq 'ON' ? 'selected' : ''}>ON (HIGH)</option>
<option value="LAST" ${ui.edit.boot eq 'LAST' ? 'selected' : ''}>Giữ trạng thái trước</option>
</select>
</div>
<div class="um-field">
<label for="dm-rating">Thông số tải</label>
<input id="dm-rating" name="rating" value="<c:out value='${ui.edit.rating}'/>" maxlength="100">
</div>
</div>
</c:when>
<c:when test="${ui.form eq 'switchSchedule'}">
<p class="um-dialog-description">
<c:out value="${ui.target.name}"/> · GPIO ${ui.target.gpio}</p>
<div class="um-form-grid">
<div class="um-field">
<label for="dm-run-at">Ngày giờ chạy (UTC+7)</label>
<input id="dm-run-at" name="runAt" type="datetime-local" value="<c:out value='${ui.edit.runAt}'/>" required autofocus>
</div>
<div class="um-field">
<label for="dm-schedule-command">Hành động</label>
<select id="dm-schedule-command" name="command">
<option value="ON" ${ui.edit.command eq 'ON' ? 'selected' : ''}>Bật (ON)</option>
<option value="OFF" ${ui.edit.command eq 'OFF' ? 'selected' : ''}>Tắt (OFF)</option>
</select>
</div>
</div>
<p class="um-dialog-description">Lưu cấu hình minh họa; chưa tự chạy trên ESP32.</p>
</c:when>
<c:otherwise>
<p class="um-dialog-description">${ui.form eq 'rebootNode' ? 'Mô phỏng reboot' : 'Xóa'}: <strong>
<c:out value="${ui.target.name}"/>
</strong>?</p>
<c:if test="${ui.form eq 'deleteNode'}">
<p class="um-dialog-description">Các switch con của thiết bị cũng sẽ bị xóa trong session.</p>
</c:if>
</c:otherwise>
</c:choose>
<div class="um-dialog-footer">
<a class="um-button um-button-secondary" href="${pageContext.request.contextPath}/MainController?action=DEVICE_MANAGER">Hủy</a>
<c:if test="${ui.form eq 'switchSchedule' and not empty ui.target.schedule}">
<button class="um-button um-button-danger" type="submit" name="uiOp" value="removeSwitchSchedule" formnovalidate>Bỏ Hẹn Giờ</button>
</c:if>
<button class="um-button ${ui.form eq 'deleteNode' or ui.form eq 'deleteSwitch' ? 'um-button-danger' : 'um-button-primary'}" name="uiOp" value="${ui.postOperation}" type="submit">Xác Nhận</button>
</div>
</form>
</dialog>
</c:if>
</body>
</html>