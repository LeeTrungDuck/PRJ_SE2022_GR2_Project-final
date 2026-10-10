<%-- 
    Document   : errorPage
    Created on : Oct 6, 2026, 12:57:56 PM
    Author     : ltrun
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <title>error Page</title>
        <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
    </head>
    <body>
        <h1>Hello Error</h1>
        <c:if test="${not empty requestScope.ERROR}">
            <div class="login-error" role="alert">
                <i class="fa-solid fa-circle-exclamation" aria-hidden="true"></i>
                <p><c:out value="${requestScope.ERROR}" /></p>
            </div>
        </c:if> 
    </body>
</html>
