<%-- 
    Document   : DeviceControl
    Created on : Oct 5, 2026, 6:22:16 PM
    Author     : ltrun
--%>

<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<c:set var="activePage" value="DEVICE_CONTROL" scope="request" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Điều khiển công tắc | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceControlcss.css?v=20261006-dashboard">
    </head>
    <body class="user-manager-page device-control-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">ĐIỀU KHIỂN &amp; GIÁM SÁT</span>
            <div class="um-topbar-user">
                <span class="dc-topbar-role">Vai trò: <span class="um-admin-tag">Admin</span></span>
                <span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user" aria-hidden="true"></i></span>
            </div>
        </header>
        <main class="um-main dc-main" id="main-content">
            <header class="dc-page-heading">
                <h1 id="dc-title">Bảng Điều Khiển Công Tắc</h1>
                <p>Theo dõi thiết bị và thao tác với các công tắc trong hệ thống ESP32.</p>
            </header>
            <c:choose>
                <c:when test="${empty requestScope.ds}">
                    <section class="dc-empty-state" aria-labelledby="dc-empty-title">
                        <span class="dc-empty-icon"><i class="fa-solid fa-sliders" aria-hidden="true"></i></span>
                        <h2 id="dc-empty-title">Không có thiết bị nào.</h2>
                        <p>Các công tắc sẽ hiển thị tại đây khi danh sách thiết bị được tải.</p>
                    </section>
                </c:when>
                <c:otherwise>
                    <section class="dc-device-grid" aria-label="Danh sách thiết bị và công tắc">
                        <c:forEach var="i" items="${requestScope.ds}">
                            <article class="device-card">
                                <div class="card-header-row">
                                    <span class="badge-gpio">ID: <c:out value="${i.id}" /></span>
                                    <c:url var="dcDeleteUrl" value="/DeleteTypeController"><c:param name="id" value="${i.id}" /></c:url>
                                    <a href="<c:out value='${dcDeleteUrl}' />" class="btn-delete" title="Xóa thiết bị" aria-label="Xóa thiết bị" onclick="return confirm('Bạn có chắc chắn muốn xóa thiết bị này?');">
                                        <i class="fa-solid fa-trash-can" aria-hidden="true"></i>
                                    </a>
                                </div>
                                <div class="card-body-row">
                                    <span class="device-icon"><i class="fa-solid fa-microchip" aria-hidden="true"></i></span>
                                    <div class="device-meta">
                                        <h2 class="device-title"><c:out value="${i.name}" /></h2>
                                        <span class="device-mode"><span aria-hidden="true"></span> CONNECTED</span>
                                    </div>
                                </div>
                                <div class="card-footer-row">
                                    <div class="status-box"><span class="status-label">TRẠNG THÁI</span><span class="status-text text-on">ACTIVE</span></div>
                                    <label class="switch-toggle">
                                        <input type="checkbox" role="switch" aria-label="Bật hoặc tắt công tắc" checked>
                                        <span class="slider" aria-hidden="true"></span>
                                    </label>
                                </div>
                            </article>
                        </c:forEach>
                    </section>
                </c:otherwise>
            </c:choose>
        </main>
    </body>
</html>
