<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:if test="${empty requestScope.ui}">
<c:redirect url="/MainController">
<c:param name="action" value="DEVICE_CONTROL" />
</c:redirect>
</c:if>
<c:set var="activePage" value="DEVICE_CONTROL" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Điều Khiển Switch | ESP32 IoT Hub</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceControlcss.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
</head>
<body class="user-manager-page device-control-page">
<div class="ui-shell" ${ui.dialog ? 'inert' : ''}>
<%@ include file="sideBar.jspf" %>
<header class="um-topbar">
<span class="um-section-label">Điều Khiển Switch</span>
<div class="um-topbar-user">
<span class="um-admin-tag">Admin</span>
<span class="um-profile-icon" aria-label="Tài khoản minh họa">
<i class="fa-regular fa-user" aria-hidden="true">
</i>
</span>
</div>
</header>
<main class="um-main dc-main" id="main-content">
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
<header class="dc-page-heading">
<span class="dc-heading-dot" aria-hidden="true">
</span>
<h1>Bảng Điều Khiển Công Tắc <span>(Device Control)</span>
</h1>
</header>
<section class="dc-device-grid" aria-label="Danh sách công tắc">
<c:forEach var="relay" items="${ui.relays}">
<article class="dc-relay-card" data-on="${relay.on}">
<div class="dc-card-heading">
<div class="dc-relay-meta">
<span class="dc-gpio">GPIO ${relay.gpio}</span>
<p class="dc-location">
<c:out value="${relay.node}" /> · <c:out value="${relay.location}" />
</p>
</div>
<c:if test="${relay.kind eq 'valve'}">
<span class="dc-valve-badge">
<span aria-hidden="true">
</span>${relay.on ? 'OPEN' : 'CLOSED'}</span>
</c:if>
</div>
<div class="dc-card-body">
<span class="dc-relay-icon">
<i class="fa-solid ${relay.kind eq 'uv' ? 'fa-spray-can-sparkles' : 'fa-faucet'}" aria-hidden="true">
</i>
</span>
<div class="dc-relay-identity">
<h2 class="dc-relay-name">
<c:out value="${relay.name}" />
</h2>
<span class="dc-relay-mode">
<c:out value="${relay.mode}" />
</span>
</div>
</div>
<div class="dc-card-footer">
<div class="dc-status-box">
<span class="dc-status-label">TRẠNG THÁI</span>
<span class="dc-relay-state">${relay.on ? 'ON' : 'OFF'}</span>
</div>
<form class="dc-controls" action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<input name="id" value="${relay.id}" type="hidden">
<button type="submit" name="uiOp" value="toggleRelay" class="dc-action-button">
<c:out value="${relay.commandLabel}" />
</button>
<button type="submit" name="uiOp" value="toggleRelay" class="dc-switch" role="switch" aria-checked="${relay.on}" aria-label="Đổi trạng thái ${fn:escapeXml(relay.name)}">
<span class="dc-switch-track" aria-hidden="true">
<span>
</span>
</span>
</button>
</form>
</div>
</article>
</c:forEach>
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
</body>
</html>