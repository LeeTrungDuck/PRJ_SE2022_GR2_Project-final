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
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Lịch sử điều khiển</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/controlHistoryCss.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css">
</head>
<body class="user-manager-page control-history-page">
<div class="ui-shell">
    <%@ include file="sideBar.jspf" %>
    <main class="um-main ch-main" id="main-content">
        <h1>Lịch sử điều khiển</h1>
        <p>Nhật ký thao tác được đọc từ cơ sở dữ liệu. Viewer chỉ xem lịch sử của tài khoản đang đăng nhập.</p>
        <c:if test="${not empty uiError}">
            <p class="ui-notice ui-error" role="alert"><c:out value="${uiError}"/></p>
        </c:if>
        <form class="ch-toolbar" action="${pageContext.request.contextPath}/MainController" method="get">
            <input type="hidden" name="action" value="CONTROL_HISTORY">
            <label for="history-search">Tìm lịch sử</label>
            <input id="history-search" name="q" type="search" maxlength="100" value="<c:out value='${q}'/>">
            <label for="history-switch">Switch ID</label>
            <input id="history-switch" name="switchId" maxlength="20" value="<c:out value='${switchId}'/>">
            <label for="history-command">Lệnh</label>
            <select id="history-command" name="command">
                <option value="all" ${empty command or command eq 'all' ? 'selected' : ''}>Tất cả</option>
                <option value="ON" ${command eq 'ON' ? 'selected' : ''}>ON</option>
                <option value="OFF" ${command eq 'OFF' ? 'selected' : ''}>OFF</option>
            </select>
            <label for="history-result">Kết quả</label>
            <select id="history-result" name="result">
                <option value="all" ${empty result or result eq 'all' ? 'selected' : ''}>Tất cả</option>
                <option value="ON" ${result eq 'ON' ? 'selected' : ''}>ON</option>
                <option value="OFF" ${result eq 'OFF' ? 'selected' : ''}>OFF</option>
                <option value="ERROR" ${result eq 'ERROR' ? 'selected' : ''}>ERROR</option>
            </select>
            <button type="submit">Lọc</button>
            <a href="${pageContext.request.contextPath}/MainController?action=CONTROL_HISTORY">Xóa bộ lọc</a>
        </form>

        <section class="ch-log-panel" aria-labelledby="history-heading">
            <h2 id="history-heading">Bản ghi (<c:out value="${fn:length(history)}"/>)</h2>
            <div class="ch-table-scroll" role="region" aria-label="Danh sách lịch sử điều khiển" tabindex="0">
                <table class="ch-table">
                    <thead>
                    <tr><th>ID</th><th>User ID</th><th>Switch ID</th><th>Lệnh</th><th>Kết quả</th><th>Thời gian</th></tr>
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
                        <tr><td colspan="6">Không có bản ghi phù hợp.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
</body>
</html>
