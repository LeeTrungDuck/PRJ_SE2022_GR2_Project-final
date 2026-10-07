<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:if test="${empty requestScope.ui}">
<c:redirect url="/MainController">
<c:param name="action" value="ACCOUNT_MANAGER" />
</c:redirect>
</c:if>
<c:set var="activePage" value="ACCOUNT_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Hồ Sơ Tài Khoản | ESP32 IoT Hub</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/accountManagerCss.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
</head>
<body class="user-manager-page account-manager-page">
<div class="ui-shell" ${ui.dialog ? 'inert' : ''}>
<%@ include file="sideBar.jspf" %>
<header class="um-topbar">
<span class="um-section-label">Hồ Sơ Tài Khoản</span>
<div class="um-topbar-user">
<span class="um-admin-tag">Admin</span>
<span class="um-profile-icon" aria-label="Tài khoản minh họa">
<i class="fa-regular fa-user" aria-hidden="true">
</i>
</span>
</div>
</header>
<main class="um-main ac-main" id="main-content">
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
<header class="ac-page-heading">
<span class="ac-heading-dot" aria-hidden="true">
</span>
<h1>Hồ Sơ &amp; Cài Đặt Tài Khoản <span>(Profile / Account)</span>
</h1>
<p>Quản lý thông tin định danh và liên hệ trong phiên minh họa.</p>
</header>
<div class="ac-workspace">
<aside class="ac-profile-card">
<div class="ac-profile-identity">
<span class="ac-avatar">
<i class="fa-regular fa-circle-user" aria-hidden="true">
</i>
<span class="ac-online-dot" aria-hidden="true">
</span>
</span>
<div class="ac-profile-text">
<h2>
<c:out value="${ui.profile.fullName}" />
</h2>
<p class="ac-handle">@<c:out value="${ui.profile.userName}" />
</p>
<span class="ac-role-badge">ADMINISTRATOR (TOÀN QUYỀN)</span>
</div>
</div>
<dl class="ac-profile-details">
<div>
<dt>User ID</dt>
<dd>UID-8829</dd>
</div>
<div>
<dt>Trạng Thái</dt>
<dd class="ac-status">Hoạt Động</dd>
</div>
<div>
<dt>Ngày Tạo</dt>
<dd>12/10/2023</dd>
</div>
<div>
<dt>Phân Quyền</dt>
<dd class="ac-root-role">SuperAdmin / Root</dd>
</div>
</dl>
</aside>
<div class="ac-forms">
<section class="ac-panel">
<header class="ac-panel-heading">
<i class="fa-solid fa-id-card-clip" aria-hidden="true">
</i>
<div>
<h2>Chỉnh Sửa Thông Tin Cá Nhân</h2>
<p>Cập nhật họ tên, email và số điện thoại.</p>
</div>
<span class="ac-form-number">FORM 01/02</span>
</header>
<form action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<div class="ac-personal-grid">
<div class="ac-field">
<label for="ac-fullname">Họ và Tên</label>
<div class="ac-input-wrap">
<i class="fa-regular fa-user" aria-hidden="true">
</i>
<input id="ac-fullname" name="fullName" value="<c:out value='${ui.edit.fullName}' />" maxlength="100" autocomplete="name" required>
</div>
</div>
<div class="ac-field">
<label for="ac-username">Tên Đăng Nhập (Cố định)</label>
<div class="ac-input-wrap ac-readonly">
<i class="fa-solid fa-at" aria-hidden="true">
</i>
<input id="ac-username" value="<c:out value='${ui.profile.userName}' />" readonly>
</div>
</div>
<div class="ac-field">
<label for="ac-email">Email Nhận Thông Báo Khẩn</label>
<div class="ac-input-wrap">
<i class="fa-regular fa-envelope" aria-hidden="true">
</i>
<input id="ac-email" type="email" name="email" value="<c:out value='${ui.edit.email}' />" maxlength="254" required>
</div>
</div>
<div class="ac-field">
<label for="ac-phone">Số Điện Thoại Hỗ Trợ</label>
<div class="ac-input-wrap">
<i class="fa-solid fa-phone" aria-hidden="true">
</i>
<input id="ac-phone" name="phone" type="tel" value="<c:out value='${ui.edit.phone}' />" maxlength="30" required>
</div>
</div>
</div>
<footer class="ac-panel-footer">
<p>
<i class="fa-solid fa-circle-info" aria-hidden="true">
</i> Thay email yêu cầu OTP minh họa 246810.</p>
<button class="um-button um-button-primary" name="uiOp" value="saveProfile" type="submit">Lưu Thay Đổi</button>
</footer>
</form>
</section>
<section class="ac-panel">
<header class="ac-panel-heading">
<i class="fa-solid fa-clock-rotate-left" aria-hidden="true">
</i>
<div>
<h2>Đổi Mật Khẩu Truy Cập</h2>
<p>Tối thiểu 10 ký tự, có chữ hoa, số và ký tự đặc biệt.</p>
</div>
<span class="ac-form-number">FORM 02/02</span>
</header>
<form action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<div class="ac-password-grid">
<div class="ac-field">
<label for="ac-current-password">Mật Khẩu Hiện Tại</label>
<div class="ac-input-wrap">
<i class="fa-solid fa-key" aria-hidden="true">
</i>
<input id="ac-current-password" name="currentPassword" type="password" autocomplete="current-password" maxlength="128" required>
</div>
</div>
<div class="ac-field">
<label for="ac-new-password">Mật Khẩu Mới</label>
<div class="ac-input-wrap">
<i class="fa-solid fa-lock" aria-hidden="true">
</i>
<input id="ac-new-password" name="newPassword" type="password" autocomplete="new-password" minlength="10" maxlength="128" required>
</div>
</div>
<div class="ac-field">
<label for="ac-confirm-password">Nhập Lại Mật Khẩu</label>
<div class="ac-input-wrap">
<i class="fa-solid fa-check" aria-hidden="true">
</i>
<input id="ac-confirm-password" name="confirmPassword" type="password" autocomplete="new-password" minlength="10" maxlength="128" required>
</div>
</div>
</div>
<footer class="ac-panel-footer">
<p>Kiểm tra điều kiện sau khi gửi form; không lưu mật khẩu minh họa.</p>
<button class="um-button um-button-primary" name="uiOp" value="changePassword" type="submit">Cập Nhật Mật Khẩu</button>
</footer>
</form>
</section>
</div>
</div>
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
<p class="um-dialog-description">Email chờ xác nhận: <strong>
<c:out value="${ui.pendingProfile.email}" />
</strong>.</p>
<p>Mã OTP minh họa: <strong>246810</strong>. Không gửi email thực tế.</p>
<form action="${pageContext.request.contextPath}/MainController" method="post">
<%@ include file="uiPostFields.jspf" %>
<input name="formName" value="otp" type="hidden">
<div class="um-field">
<label for="ac-otp-code">Mã OTP</label>
<input id="ac-otp-code" name="otp" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required autofocus autocomplete="one-time-code">
</div>
<div class="um-dialog-footer">
<button name="uiOp" value="cancelOtp" type="submit" formnovalidate class="um-button um-button-secondary">Hủy</button>
<button name="uiOp" value="confirmOtp" type="submit" class="um-button um-button-primary">Xác Nhận</button>
</div>
</form>
</dialog>
</c:if>
</body>
</html>