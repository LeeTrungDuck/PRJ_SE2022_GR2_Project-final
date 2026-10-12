<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%
    dto.UserDTO currentUser = (dto.UserDTO) session.getAttribute("LOGIN_USER");
    if (currentUser == null) {
        response.sendRedirect(request.getContextPath() + "/web/login.jsp");
        return;
    }
    if (request.getAttribute("schedules") == null) {
        response.sendRedirect(request.getContextPath() + "/MainController?action=SCHEDULE_MANAGER");
        return;
    }
%>
<c:set var="activePage" value="SCHEDULE_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Quản lý lịch chạy</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css">
</head>
<body class="user-manager-page">
<div class="ui-shell">
    <%@ include file="sideBar.jspf" %>
    <main class="um-main" id="main-content">
        <h1>Lịch chạy switch</h1>
        <p>Cơ sở dữ liệu hiện lưu lịch chạy hằng ngày theo giờ, switch, lệnh và trạng thái bật/tắt.</p>
        <c:if test="${not empty uiMessage}">
            <p class="ui-notice" role="status"><c:out value="${uiMessage}"/></p>
        </c:if>
        <c:if test="${not empty uiError}">
            <p class="ui-notice ui-error" role="alert"><c:out value="${uiError}"/></p>
        </c:if>

        <form action="${pageContext.request.contextPath}/MainController" method="get">
            <input type="hidden" name="action" value="SCHEDULE_MANAGER">
            <label for="schedule-search">Tìm theo switch, lệnh, giờ hoặc người tạo</label>
            <input id="schedule-search" name="q" type="search" maxlength="100" value="<c:out value='${q}'/>">
            <button type="submit">Tìm</button>
        </form>

        <c:if test="${canManage}">
            <section class="um-table-panel" aria-labelledby="add-schedule-heading">
                <h2 id="add-schedule-heading">Thêm lịch</h2>
                <form action="${pageContext.request.contextPath}/MainController" method="post">
                    <input type="hidden" name="action" value="SCHEDULE_MANAGER">
                    <input type="hidden" name="uiOp" value="addSchedule">
                    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                    <label>Switch
                        <select name="switchId" required>
                            <option value="">Chọn switch</option>
                            <c:forEach var="sw" items="${switches}">
                                <option value="<c:out value='${sw.switchId}'/>" ${submittedOperation eq 'addSchedule' and submitted.switchId eq sw.switchId ? 'selected' : ''}><c:out value="${sw.switchName}"/> (<c:out value="${sw.switchId}"/>)</option>
                            </c:forEach>
                        </select>
                    </label>
                    <label>Lệnh
                        <select name="scheduleAction" required>
                            <option value="ON" ${submittedOperation eq 'addSchedule' and submitted.scheduleAction eq 'ON' ? 'selected' : ''}>Bật</option>
                            <option value="OFF" ${submittedOperation eq 'addSchedule' and submitted.scheduleAction eq 'OFF' ? 'selected' : ''}>Tắt</option>
                        </select>
                    </label>
                    <label>Giờ chạy <input name="runTime" type="time" required
                        value="<c:out value='${submittedOperation eq "addSchedule" ? submitted.runTime : ""}'/>"></label>
                    <label><input name="enabled" type="checkbox" value="true"
                        ${submittedOperation eq 'addSchedule' ? submitted.enabled eq 'true' ? 'checked' : '' : 'checked'}> Đang bật</label>
                    <button type="submit" <c:if test="${empty switches}">disabled</c:if>>Thêm lịch</button>
                    <c:if test="${empty switches}"><p>Chưa có switch hoạt động để lập lịch.</p></c:if>
                </form>
            </section>
        </c:if>

        <section class="um-table-panel" aria-labelledby="schedule-list-heading">
            <h2 id="schedule-list-heading">Danh sách lịch (<c:out value="${fn:length(schedules)}"/>)</h2>
            <div class="um-table-scroll" role="region" aria-label="Danh sách lịch" tabindex="0">
                <table class="um-table">
                    <thead>
                    <tr><th>ID</th><th>Người tạo</th><th>Switch</th><th>Lệnh</th><th>Giờ</th><th>Bật</th><th>Thao tác</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="schedule" items="${schedules}">
                        <c:set var="rowCanManage" value="${canManage and actorId eq schedule.userId}"/>
                        <c:set var="editingSchedule" value="${submittedOperation eq 'updateSchedule' and submittedScheduleId eq schedule.scheduleId}"/>
                        <tr>
                            <td><c:out value="${schedule.scheduleId}"/></td>
                            <td><c:out value="${schedule.userId}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${rowCanManage}">
                                        <label class="um-sr-only" for="schedule-switch-${schedule.scheduleId}">Switch</label>
                                        <select form="update-schedule-${schedule.scheduleId}" id="schedule-switch-${schedule.scheduleId}" name="switchId" required>
                                            <c:forEach var="sw" items="${switches}">
                                                <option value="<c:out value='${sw.switchId}'/>" ${(editingSchedule ? submitted.switchId eq sw.switchId : schedule.switchId eq sw.switchId) ? 'selected' : ''}><c:out value="${sw.switchName}"/></option>
                                            </c:forEach>
                                        </select>
                                    </c:when>
                                    <c:otherwise><c:out value="${schedule.switchId}"/></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${rowCanManage}">
                                        <label class="um-sr-only" for="schedule-action-${schedule.scheduleId}">Lệnh</label>
                                        <select form="update-schedule-${schedule.scheduleId}" id="schedule-action-${schedule.scheduleId}" name="scheduleAction">
                                            <option value="ON" ${(editingSchedule ? submitted.scheduleAction eq 'ON' : schedule.action eq 'ON') ? 'selected' : ''}>Bật</option>
                                            <option value="OFF" ${(editingSchedule ? submitted.scheduleAction eq 'OFF' : schedule.action eq 'OFF') ? 'selected' : ''}>Tắt</option>
                                        </select>
                                    </c:when>
                                    <c:otherwise><c:out value="${schedule.action}"/></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${rowCanManage}">
                                        <label class="um-sr-only" for="schedule-time-${schedule.scheduleId}">Giờ chạy</label>
                                        <input form="update-schedule-${schedule.scheduleId}" id="schedule-time-${schedule.scheduleId}" name="runTime" type="time" required
                                               value="<c:out value='${editingSchedule ? submitted.runTime : schedule.runTime}'/>">
                                    </c:when>
                                    <c:otherwise><c:out value="${schedule.runTime}"/></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${rowCanManage}">
                                        <label class="um-sr-only" for="schedule-enabled-${schedule.scheduleId}">Bật lịch</label>
                                        <input form="update-schedule-${schedule.scheduleId}" id="schedule-enabled-${schedule.scheduleId}" name="enabled" type="checkbox" value="true"
                                               ${editingSchedule ? submitted.enabled eq 'true' ? 'checked' : '' : schedule.enabled ? 'checked' : ''}>
                                    </c:when>
                                    <c:otherwise><c:out value="${schedule.enabled ? 'Có' : 'Không'}"/></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:if test="${rowCanManage}">
                                    <form id="update-schedule-${schedule.scheduleId}" action="${pageContext.request.contextPath}/MainController" method="post">
                                        <input type="hidden" name="action" value="SCHEDULE_MANAGER">
                                        <input type="hidden" name="uiOp" value="updateSchedule">
                                        <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                        <input type="hidden" name="scheduleId" value="<c:out value='${schedule.scheduleId}'/>">
                                        <button type="submit">Lưu</button>
                                    </form>
                                    <form action="${pageContext.request.contextPath}/MainController" method="post">
                                        <input type="hidden" name="action" value="SCHEDULE_MANAGER">
                                        <input type="hidden" name="uiOp" value="deleteSchedule">
                                        <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                        <input type="hidden" name="scheduleId" value="<c:out value='${schedule.scheduleId}'/>">
                                        <label><input type="checkbox" name="confirm" value="yes" required> Xác nhận</label>
                                        <button type="submit">Ngừng hoạt động</button>
                                    </form>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty schedules}">
                        <tr><td colspan="7">Không có lịch phù hợp.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
</body>
</html>
