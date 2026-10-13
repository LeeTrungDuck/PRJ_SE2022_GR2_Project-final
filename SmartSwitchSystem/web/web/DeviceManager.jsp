<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%
    dto.UserDTO currentUser = (dto.UserDTO) session.getAttribute("LOGIN_USER");
    if (currentUser == null) {
        response.sendRedirect(request.getContextPath() + "/web/login.jsp");
        return;
    }
    if (currentUser.getRole() != enums.Role.ADMIN && currentUser.getRole() != enums.Role.OPERATOR) {
        response.sendError(403);
        return;
    }
    if (request.getAttribute("devices") == null) {
        response.sendRedirect(request.getContextPath() + "/MainController?action=DEVICE_MANAGER");
        return;
    }
%>
<c:set var="activePage" value="DEVICE_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Quản lý thiết bị và switch</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceManagerCss.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css">
</head>
<body class="user-manager-page device-manager-page">
<div class="ui-shell">
    <%@ include file="sideBar.jspf" %>
    <main class="um-main dm-main" id="main-content">
        <h1>Quản lý thiết bị ESP32 và switch</h1>
        <c:if test="${not empty uiMessage}">
            <p class="ui-notice" role="status"><c:out value="${uiMessage}"/></p>
        </c:if>
        <c:if test="${not empty uiError}">
            <p class="ui-notice ui-error" role="alert"><c:out value="${uiError}"/></p>
        </c:if>

        <form class="dm-toolbar" action="${pageContext.request.contextPath}/MainController" method="get">
            <input type="hidden" name="action" value="DEVICE_MANAGER">
            <label for="device-search">Tìm thiết bị hoặc switch</label>
            <input id="device-search" name="q" type="search" maxlength="100"
                   value="<c:out value='${q}'/>">
            <button type="submit">Tìm</button>
        </form>

        <section class="dm-panel" aria-labelledby="devices-heading">
            <h2 id="devices-heading">Thiết bị ESP32</h2>
            <form action="${pageContext.request.contextPath}/MainController" method="post">
                <input type="hidden" name="action" value="DEVICE_MANAGER">
                <input type="hidden" name="uiOp" value="addDevice">
                <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                <fieldset>
                    <legend>Thêm thiết bị</legend>
                    <label>Device ID <input name="deviceId" maxlength="20" pattern="ESP[A-Za-z0-9_-]{1,17}" required
                        value="<c:out value='${submittedOperation eq "addDevice" ? submitted.deviceId : ""}'/>"></label>
                    <label>Tên <input name="deviceName" maxlength="50" required
                        value="<c:out value='${submittedOperation eq "addDevice" ? submitted.deviceName : ""}'/>"></label>
                    <label>Hostname <input name="hostName" maxlength="255" required
                        value="<c:out value='${submittedOperation eq "addDevice" ? submitted.hostName : ""}'/>"></label>
                    <button type="submit">Thêm thiết bị</button>
                </fieldset>
            </form>
            <div class="dm-table-scroll" role="region" aria-label="Danh sách thiết bị ESP32" tabindex="0">
                <table class="dm-table">
                    <thead>
                    <tr><th>Device ID</th><th>Tên</th><th>Hostname</th><th>Trạng thái</th><th>Lần cuối hoạt động</th><th>Thao tác</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="device" items="${devices}">
                        <tr>
                            <td><c:out value="${device.deviceId}"/></td>
                            <td>
                                <label class="um-sr-only" for="device-name-${device.deviceId}">Tên thiết bị</label>
                                <input form="update-device-${device.deviceId}" id="device-name-${device.deviceId}"
                                       name="deviceName" maxlength="50" required
                                       value="<c:out value='${submittedOperation eq "updateDevice" and submitted.deviceId eq device.deviceId ? submitted.deviceName : device.name}'/>">
                            </td>
                            <td>
                                <label class="um-sr-only" for="device-host-${device.deviceId}">Hostname</label>
                                <input form="update-device-${device.deviceId}" id="device-host-${device.deviceId}"
                                       name="hostName" maxlength="255" required
                                       value="<c:out value='${submittedOperation eq "updateDevice" and submitted.deviceId eq device.deviceId ? submitted.hostName : device.hostName}'/>">
                            </td>
                            <td><c:out value="${device.status}"/></td>
                            <td><c:out value="${device.lastSeen}"/></td>
                            <td>
                                <form id="update-device-${device.deviceId}" action="${pageContext.request.contextPath}/MainController" method="post">
                                    <input type="hidden" name="action" value="DEVICE_MANAGER">
                                    <input type="hidden" name="uiOp" value="updateDevice">
                                    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                    <input type="hidden" name="deviceId" value="<c:out value='${device.deviceId}'/>">
                                    <button type="submit">Lưu</button>
                                </form>
                                <form action="${pageContext.request.contextPath}/MainController" method="post">
                                    <input type="hidden" name="action" value="DEVICE_MANAGER">
                                    <input type="hidden" name="uiOp" value="deleteDevice">
                                    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                    <input type="hidden" name="deviceId" value="<c:out value='${device.deviceId}'/>">
                                    <label><input type="checkbox" name="confirm" value="yes" required> Xác nhận</label>
                                    <button type="submit">Ngừng hoạt động</button>
                                </form>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty devices}">
                        <tr><td colspan="6">Không có thiết bị phù hợp.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="dm-panel" aria-labelledby="switches-heading">
            <h2 id="switches-heading">Switch / GPIO</h2>
            <form action="${pageContext.request.contextPath}/MainController" method="post">
                <input type="hidden" name="action" value="DEVICE_MANAGER">
                <input type="hidden" name="uiOp" value="addSwitch">
                <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                <fieldset>
                    <legend>Thêm switch</legend>
                    <label>Switch ID <input name="switchId" maxlength="20" pattern="SW[A-Za-z0-9_-]{1,18}" required
                        value="<c:out value='${submittedOperation eq "addSwitch" ? submitted.switchId : ""}'/>"></label>
                    <label>Thiết bị ESP32
                        <select name="switchDeviceId" required>
                            <option value="">Chọn thiết bị</option>
                            <c:forEach var="device" items="${deviceOptions}">
                                <option value="<c:out value='${device.deviceId}'/>" ${submittedOperation eq 'addSwitch' and submitted.switchDeviceId eq device.deviceId ? 'selected' : ''}><c:out value="${device.deviceId}"/> — <c:out value="${device.name}"/></option>
                            </c:forEach>
                        </select>
                    </label>
                    <label>Tên switch <input name="switchName" maxlength="50" required
                        value="<c:out value='${submittedOperation eq "addSwitch" ? submitted.switchName : ""}'/>"></label>
                    <label>GPIO pin <input name="gpioPin" type="number" min="0" max="255" required
                        value="<c:out value='${submittedOperation eq "addSwitch" ? submitted.gpioPin : ""}'/>"></label>
                    <button type="submit">Thêm switch</button>
                </fieldset>
            </form>
            <div class="dm-table-scroll" role="region" aria-label="Danh sách switch" tabindex="0">
                <table class="dm-table">
                    <thead>
                    <tr><th>Switch ID</th><th>Thiết bị</th><th>Tên</th><th>GPIO</th><th>Trạng thái</th><th>Thao tác</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="sw" items="${switches}">
                        <tr>
                            <td><c:out value="${sw.switchId}"/></td>
                            <td>
                                <label class="um-sr-only" for="switch-device-${sw.switchId}">Thiết bị ESP32</label>
                                <select form="update-switch-${sw.switchId}" id="switch-device-${sw.switchId}" name="switchDeviceId" required>
                                    <c:forEach var="device" items="${deviceOptions}">
                                        <option value="<c:out value='${device.deviceId}'/>" ${(submittedOperation eq 'updateSwitch' and submitted.switchId eq sw.switchId ? submitted.switchDeviceId eq device.deviceId : sw.deviceId eq device.deviceId) ? 'selected' : ''}><c:out value="${device.deviceId}"/></option>
                                    </c:forEach>
                                </select>
                            </td>
                            <td>
                                <label class="um-sr-only" for="switch-name-${sw.switchId}">Tên switch</label>
                                <input form="update-switch-${sw.switchId}" id="switch-name-${sw.switchId}"
                                       name="switchName" maxlength="50" required
                                       value="<c:out value='${submittedOperation eq "updateSwitch" and submitted.switchId eq sw.switchId ? submitted.switchName : sw.switchName}'/>">
                            </td>
                            <td>
                                <label class="um-sr-only" for="switch-gpio-${sw.switchId}">GPIO pin</label>
                                <input form="update-switch-${sw.switchId}" id="switch-gpio-${sw.switchId}"
                                       name="gpioPin" type="number" min="0" max="255" required
                                       value="<c:out value='${submittedOperation eq "updateSwitch" and submitted.switchId eq sw.switchId ? submitted.gpioPin : sw.gpioPin}'/>">
                            </td>
                            <td><c:out value="${sw.status}"/></td>
                            <td>
                                <form id="update-switch-${sw.switchId}" action="${pageContext.request.contextPath}/MainController" method="post">
                                    <input type="hidden" name="action" value="DEVICE_MANAGER">
                                    <input type="hidden" name="uiOp" value="updateSwitch">
                                    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                    <input type="hidden" name="switchId" value="<c:out value='${sw.switchId}'/>">
                                    <button type="submit">Lưu</button>
                                </form>
                                <form action="${pageContext.request.contextPath}/MainController" method="post">
                                    <input type="hidden" name="action" value="DEVICE_MANAGER">
                                    <input type="hidden" name="uiOp" value="deleteSwitch">
                                    <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                    <input type="hidden" name="switchId" value="<c:out value='${sw.switchId}'/>">
                                    <label><input type="checkbox" name="confirm" value="yes" required> Xác nhận</label>
                                    <button type="submit">Ngừng hoạt động</button>
                                </form>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty switches}">
                        <tr><td colspan="6">Không có switch phù hợp.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
</body>
</html>
