<%-- 
    Document   : login
    Created on : Oct 5, 2026, 4:35:44 PM
    Author     : ltrun
--%>

<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Sign In | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/loginCss.css?v=20261006-dashboard">
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
                    <p>Control and monitor your ESP32 devices in one place.</p>
                </div>
                <figure class="login-visual">
                    <img src="${pageContext.request.contextPath}/img/loginLogo.png" alt="ESP32 controller connected to relays on a test bench" width="1376" height="768">
                    <figcaption><span>Control &amp; Monitor Center</span><span>Group 2</span></figcaption>
                </figure>
            </section>

            <section class="login-access" aria-labelledby="login-title">
                <div class="login-form-content">
                    <header class="login-form-heading">
                        <h2 id="login-title">Sign In</h2>
                        <p>Enter your credentials to access the system.</p>
                    </header>
                    <c:if test="${not empty requestScope.ERROR}">
                        <div class="login-error" role="alert"><i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i><p><c:out value="${requestScope.ERROR}" /></p></div>
                    </c:if>
                    <form id="login-form" action="${pageContext.request.contextPath}/MainController?action=LOGIN" method="post">
                        <div class="login-field">
                            <label for="userName">Username</label>
                            <div class="login-input-wrap">
                                <i class="fa-regular fa-user" aria-hidden="true"></i>
                                <input id="userName" name="userName" type="text" value="<c:out value='${param.userName}' />" placeholder="Enter your username" autocomplete="username" autocapitalize="none" spellcheck="false" required>
                            </div>
                        </div>
                        <div class="login-field">
                            <label for="passWord">Password</label>
                            <div class="login-input-wrap login-password-wrap">
                                <i class="fa-solid fa-lock" aria-hidden="true"></i>
                                <input id="passWord" name="passWord" type="password" placeholder="Enter your password" autocomplete="current-password" required>
                            </div>
                        </div>
                        <button type="submit" class="um-button um-button-primary login-submit"><span>Sign In</span><i class="fa-solid fa-arrow-right" aria-hidden="true"></i></button>
                    </form>
                    <p class="login-form-footer">ESP32 IoT Hub <span aria-hidden="true">·</span> Smart Switch System</p>
                </div>
            </section>
        </main>
    </body>
</html>
