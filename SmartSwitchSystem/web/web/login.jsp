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
                <form class="form-horizontal" action="${pageContext.request.contextPath}/web/deviceControl.jsp" method="post"> <!-- sau nay doi thanh "LOGIN" --> 
                    <div class="form-group">
                        <label class="control-label col-sm-2" for="user">User</label>
                        <div class="col-sm-10">
                            <input type="text" class="form-control userName" id="userName" placeholder="Enter User Name"
                                   value="${requestScope.OLD_USER_NAME}">
                        </div>
                    </div>
                    <div class="form-group">
                        <label class="control-label col-sm-2 password" for="password">Password</label>
                        <div class="col-sm-10">
                            <input type="password" class="form-control password" id="password" placeholder="Enter password">
                        </div>
                    </div>
                    <div class="form-group">
                        <div class=" col-md-12">
                            <button type="submit" class="btn btn-login submit-btn" style="width: 100%">
                                Login Into System<span>&rarr;</span>
                            </button>
                        </div>
                    </div>
                </form>
            </div>


        </div>
    </body>
</html>
