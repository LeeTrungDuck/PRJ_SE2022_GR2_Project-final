<%-- 
    Document   : login
    Created on : Oct 5, 2026, 4:35:44 PM
    Author     : ltrun
--%>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Đăng nhập | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/loginCss.css?v=20261006-dashboard">
        <script src="${pageContext.request.contextPath}/js/login.js" defer></script>
    </head>
    <body class="user-manager-page login-page">
        <main class="login-shell" aria-labelledby="login-title">
            <section class="login-intro" aria-labelledby="login-system-name">
                <div class="um-brand login-brand">
                    <span class="um-brand-icon"><i class="fa-solid fa-microchip" aria-hidden="true"></i></span>
                    <div><strong>ESP32 IoT Hub</strong><span>TELEMETRY ENGINE</span></div>
                </div>
                <div class="login-intro-copy">
                    <h1 id="login-system-name">Smart Switch System</h1>
                    <p>Điều khiển và giám sát thiết bị ESP32 trong cùng một hệ thống.</p>
                </div>
                <figure class="login-visual">
                    <img src="${pageContext.request.contextPath}/img/loginLogo.png" alt="Bộ điều khiển ESP32 kết nối với các relay trên bàn thử nghiệm" width="1376" height="768">
                    <figcaption><span>Control &amp; Monitor Center</span><span>Nhóm 2</span></figcaption>
                </figure>
            </section>

            <section class="login-access" aria-labelledby="login-title">
                <div class="login-form-content">
                    <header class="login-form-heading">
                        <span class="login-access-icon"><i class="fa-solid fa-right-to-bracket" aria-hidden="true"></i></span>
                        <h2 id="login-title">Đăng Nhập</h2>
                        <p>Nhập tài khoản của bạn để truy cập hệ thống.</p>
                    </header>
                    <c:if test="${not empty requestScope.ERROR}">
                        <div class="login-error" role="alert"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i><p><c:out value="${requestScope.ERROR}" /></p></div>
                    </c:if>
                    <form id="login-form" action="${pageContext.request.contextPath}/MainController?action=DEVICE_CONTROL" method="post">
                        <div class="login-field">
                            <label for="userName">Tên Đăng Nhập</label>
                            <div class="login-input-wrap">
                                <i class="fa-regular fa-user" aria-hidden="true"></i>
                                <input id="userName" name="userName" type="text" value="<c:out value='${param.userName}' />" placeholder="Nhập tên đăng nhập" autocomplete="username" autocapitalize="none" spellcheck="false" required>
                            </div>
                        </div>
                        <div class="login-field">
                            <label for="passWord">Mật Khẩu</label>
                            <div class="login-input-wrap login-password-wrap">
                                <i class="fa-solid fa-lock" aria-hidden="true"></i>
                                <input id="passWord" name="passWord" type="password" placeholder="Nhập mật khẩu" autocomplete="current-password" required>
                                <button type="button" class="login-password-toggle" id="login-password-toggle" aria-label="Hiện mật khẩu" aria-pressed="false" aria-controls="passWord" hidden><i class="fa-regular fa-eye" aria-hidden="true"></i></button>
                            </div>
                        </div>
                        <button type="submit" class="um-button um-button-primary login-submit"><span>Đăng Nhập Vào Hệ Thống</span><i class="fa-solid fa-arrow-right" aria-hidden="true"></i></button>
                    </form>
                    <p class="login-form-footer">ESP32 IoT Hub <span aria-hidden="true">·</span> Smart Switch System</p>
                </div>
            </section>
        </main>
    </body>
</html>
