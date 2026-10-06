<%-- 
    Document   : login
    Created on : Oct 5, 2026, 4:35:44 PM
    Author     : ltrun
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>Login Page</title>
        <%@include file="includeFile.jspf" %>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/loginCss.css" />
    </head>
    <body>
        <div class="page">
            <div class="login-card">
                <div class="intro">
                    <h1>Smart Switch System</h1>
                    <p style="color: highlight">Make By Group 2</p>
                    <p>Control & Monitor Center</p>
                </div>
                <div class="logo">
                    <img class="logo_img" src="img/loginLogo.png" alt="login_logo"/>
                </div>
            </div>

            <div class="login-form">
                <form action="${pageContext.request.contextPath}/MainController?action=DEVICE_CONTROL" method="post">
                    <div class="form-group">
                        <label for="userName">USER NAME</label>
                        <input type="userName" class="form-control userName" id="userName" name="userName">
                    </div>
                    <div class="form-group">
                        <label for="passWord">Password</label>
                        <input type="password" class="form-control password" id="passWord" name="passWord">
                    </div>
                    <button type="submit" class="btn btn-default submit-btn">ĐĂNG NHẬP VÀO HỆ THỐNG <span>&rarr;</span></button>
                </form> 

            </div>
        </div>
    </body>
</html>
