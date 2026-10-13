<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%
    dto.UserDTO currentUser = (dto.UserDTO) session.getAttribute("LOGIN_USER");
    if (currentUser == null) {
        response.sendRedirect(request.getContextPath() + "/web/login.jsp");
        return;
    }
    if (request.getAttribute("history") == null) {
        response.sendRedirect(request.getContextPath() + "/MainController?action=CONTROL_HISTORY");
        return;
    }
%>
<c:set var="activePage" value="CONTROL_HISTORY" scope="request" />
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Control History</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/controlHistoryCss.css?v=20261013-filter-colors">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css">
</head>
<body class="user-manager-page control-history-page">
<div class="ui-shell">
    <%@ include file="sideBar.jspf" %>
    <main class="um-main ch-main" id="main-content">
        <h1>Control History</h1>
        <p>View recorded control actions. Viewers can only see their own history.</p>
        <c:if test="${not empty uiError}">
            <p class="ui-notice ui-error" role="alert"><c:out value="${uiError}"/></p>
        </c:if>
        <form class="ch-toolbar" action="${pageContext.request.contextPath}/MainController" method="get">
            <input type="hidden" name="action" value="CONTROL_HISTORY">
            <label for="history-search">Search history</label>
            <input id="history-search" name="q" type="search" maxlength="100" value="<c:out value='${q}'/>">
            <label for="history-switch">Switch ID</label>
            <input id="history-switch" name="switchId" maxlength="20" value="<c:out value='${switchId}'/>">
            <label for="history-command">Command</label>
            <select id="history-command" name="command">
                <option value="all" ${empty command or command eq 'all' ? 'selected' : ''}>All</option>
                <option value="ON" ${command eq 'ON' ? 'selected' : ''}>ON</option>
                <option value="OFF" ${command eq 'OFF' ? 'selected' : ''}>OFF</option>
            </select>
            <label for="history-result">Result</label>
            <select id="history-result" name="result">
                <option value="all" ${empty result or result eq 'all' ? 'selected' : ''}>All</option>
                <option value="ON" ${result eq 'ON' ? 'selected' : ''}>ON</option>
                <option value="OFF" ${result eq 'OFF' ? 'selected' : ''}>OFF</option>
                <option value="ERROR" ${result eq 'ERROR' ? 'selected' : ''}>ERROR</option>
            </select>
            <button type="submit">Filter</button>
            <a class="ch-clear-filters" href="${pageContext.request.contextPath}/MainController?action=CONTROL_HISTORY">Clear filters</a>
        </form>

        <section class="ch-log-panel" aria-labelledby="history-heading">
            <h2 id="history-heading">Latest 50 records (<c:out value="${fn:length(history)}"/>)</h2>
            <div class="ch-table-scroll" role="region" aria-label="Control history list" tabindex="0">
                <table class="ch-table">
                    <thead>
                    <tr><th>ID</th><th>User ID</th><th>Switch ID</th><th>Command</th><th>Result</th><th>Time</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="record" items="${history}">
                        <tr>
                            <td><c:out value="${record.historyId}"/></td>
                            <td><c:out value="${record.userId}"/></td>
                            <td><c:out value="${record.switchId}"/></td>
                            <td><c:out value="${record.command}"/></td>
                            <td><c:out value="${record.result}"/></td>
                            <td><c:out value="${record.controlTime}"/></td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty history}">
                        <tr><td colspan="6">No matching records found.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
</body>
</html>
