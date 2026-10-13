<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%
    dto.UserDTO currentUser = (dto.UserDTO) session.getAttribute("LOGIN_USER");
    if (currentUser == null) {
        response.sendRedirect(request.getContextPath() + "/web/login.jsp");
        return;
    }
    if (currentUser.getRole() != enums.Role.ADMIN && currentUser.getRole() != enums.Role.OPERATOR
            && currentUser.getRole() != enums.Role.VIEWER) {
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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceManagerCss.css?v=20261013-device-esp-filter">
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
            <label for="device-esp-filter">ESP</label>
            <select id="device-esp-filter" name="esp">
                <option value="all" ${empty espFilter or espFilter eq 'all' ? 'selected' : ''}>Tất cả ESP</option>
                <c:forEach var="esp" items="${espFilterOptions}">
                    <option value="<c:out value='${esp.deviceId}'/>" ${espFilter eq esp.deviceId ? 'selected' : ''}>
                        <c:out value="${esp.deviceId}"/> — <c:out value="${esp.name}"/>
                    </option>
                </c:forEach>
            </select>
            <button type="submit">Tìm</button>
        </form>

        <section class="dm-panel" aria-labelledby="devices-heading">
            <h2 id="devices-heading">Thiết bị ESP32</h2>
            <c:if test="${canManageDevices}">
            <form class="dm-add-device-form" action="${pageContext.request.contextPath}/MainController" method="post">
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
                    <button type="submit" class="dm-add-device-button">Thêm thiết bị</button>
                </fieldset>
            </form>
            </c:if>
            <div class="dm-table-scroll" role="region" aria-label="Danh sách thiết bị ESP32" tabindex="0">
                <table class="dm-table">
                    <thead>
                    <tr><th>Device ID</th><th>Tên</th><th>Hostname</th><th>Trạng thái</th><th>Thao tác</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="device" items="${devices}">
                        <tr>
                            <td><c:out value="${device.deviceId}"/></td>
                            <td>
                                <c:if test="${not canManageDevices}"><c:out value="${device.name}"/></c:if>
                                <c:if test="${canManageDevices}">
                                <label class="um-sr-only" for="device-name-${device.deviceId}">Tên thiết bị</label>
                                <input form="update-device-${device.deviceId}" id="device-name-${device.deviceId}"
                                       name="deviceName" maxlength="50" required
                                       value="<c:out value='${submittedOperation eq "updateDevice" and submitted.deviceId eq device.deviceId ? submitted.deviceName : device.name}'/>">
                                </c:if>
                            </td>
                            <td><c:out value="${device.hostName}"/></td>
                            <td><c:out value="${devicePowerStates[device.deviceId] ? 'ON' : 'OFF'}"/></td>
                            <td>
                                <c:if test="${canManageDevices}">
                                <div class="dm-row-actions">
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
                                        <details class="dm-deactivate-confirm">
                                            <summary>Ngừng hoạt động</summary>
                                            <div class="dm-confirm-content">
                                                <button type="button" class="dm-return-button">Trở về</button>
                                                <div class="dm-confirm-actions">
                                                    <label><input type="checkbox" name="confirm" value="yes" required> Xác nhận</label>
                                                    <button type="submit" class="dm-danger-button">Ngừng hoạt động</button>
                                                </div>
                                            </div>
                                        </details>
                                    </form>
                                </div>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty devices}">
                        <tr><td colspan="5">Không có thiết bị phù hợp.</td></tr>
                    </c:if>
                    </tbody>
                </table>
            </div>
        </section>

        <section class="dm-panel" aria-labelledby="switches-heading">
            <h2 id="switches-heading">Switch / GPIO</h2>
            <c:if test="${canManageDevices}">
            <form class="dm-add-switch-form" action="${pageContext.request.contextPath}/MainController" method="post">
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
            </c:if>
            <div class="dm-table-scroll" role="region" aria-label="Danh sách switch" tabindex="0">
                <table class="dm-table">
                    <thead>
                    <tr><th>Switch ID</th><th>Thiết bị</th><th>Tên</th><th>GPIO</th><th>Trạng thái</th><th>Thao tác</th></tr>
                    </thead>
                    <tbody>
                    <c:forEach var="sw" items="${switches}">
                        <tr>
                            <td><c:out value="${sw.switchId}"/></td>
                            <td><c:out value="${sw.deviceId}"/></td>
                            <td>
                                <c:choose>
                                <c:when test="${sw.active}">
                                <label class="um-sr-only" for="switch-name-${sw.switchId}">Tên switch</label>
                                <input form="update-switch-${sw.switchId}" id="switch-name-${sw.switchId}"
                                       name="switchName" maxlength="50" required
                                       value="<c:out value='${submittedOperation eq "updateSwitch" and submitted.switchId eq sw.switchId ? submitted.switchName : sw.switchName}'/>">
                                </c:when>
                                <c:otherwise><c:out value="${sw.switchName}"/></c:otherwise>
                                </c:choose>
                            </td>
                            <td><c:out value="${sw.gpioPin}"/></td>
                            <td>
                                <c:choose>
                                    <c:when test="${sw.active}"><c:out value="${sw.status}"/></c:when>
                                    <c:otherwise>Ngừng hoạt động</c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:if test="${canManageSwitches}">
                                <div class="dm-row-actions">
                                    <c:if test="${sw.active}">
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
                                        <details class="dm-deactivate-confirm">
                                            <summary>Ngừng hoạt động</summary>
                                            <div class="dm-confirm-content">
                                                <button type="button" class="dm-return-button">Trở về</button>
                                                <div class="dm-confirm-actions">
                                                    <label><input type="checkbox" name="confirm" value="yes" required> Xác nhận</label>
                                                    <button type="submit" class="dm-danger-button">Ngừng hoạt động</button>
                                                </div>
                                            </div>
                                        </details>
                                    </form>
                                    </c:if>
                                    <c:if test="${canManageDevices and not sw.active}">
                                    <form action="${pageContext.request.contextPath}/MainController" method="post">
                                        <input type="hidden" name="action" value="DEVICE_MANAGER">
                                        <input type="hidden" name="uiOp" value="activateSwitch">
                                        <input type="hidden" name="csrf" value="<c:out value='${csrf}'/>">
                                        <input type="hidden" name="switchId" value="<c:out value='${sw.switchId}'/>">
                                        <button type="submit" <c:if test="${not sw.espActive}">disabled title="ESP32 này đang ngừng hoạt động"</c:if>>Kích hoạt</button>
                                    </form>
                                    </c:if>
                                </div>
                                </c:if>
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
<script>
    document.querySelectorAll('.dm-deactivate-confirm').forEach(function (details) {
        var actionCell = details.closest('td');
        details.addEventListener('toggle', function () {
            actionCell.classList.toggle('dm-deactivation-pending', details.open);
        });
        details.closest('form').querySelector('.dm-return-button').addEventListener('click', function () {
            details.open = false;
        });
    });
</script>
</body>
</html>
