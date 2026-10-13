<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="activePage" value="DEVICE_CONTROL" scope="request" />
<!DOCTYPE html>
<html lang="vi">

    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Điều Khiển Switch | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link
            href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
            rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/deviceControlcss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
    </head>

    <body class="user-manager-page device-control-page">
        <div class="ui-shell" ${ui.dialog ? 'inert' : '' }>
            <%@ include file="sideBar.jspf" %>

            <main class="um-main dc-main" id="main-content">

                <c:if test="${not empty requestScope.ERROR}">
                    <p class="ui-notice ui-error" role="alert">
                        <c:out value="${ERROR}" />
                    </p>
                </c:if>
                <header class="dc-page-heading">
                    <span class="dc-heading-dot" aria-hidden="true">
                    </span>
                    <h1>Bảng Điều Khiển Công Tắc <span>(Device Control)</span>
                    </h1>
                </header>
                <p class="ui-notice">Trạng thái được đồng bộ từ ESP32 khi tải lại trang.</p>
                <form class="dc-filter-form" action="${pageContext.request.contextPath}/DeviceControlController" method="get">
                    <label for="dc-esp-filter">ESP</label>
                    <select id="dc-esp-filter" name="esp">
                        <option value="all" ${empty espFilter or espFilter eq 'all' ? 'selected' : ''}>Tất cả ESP</option>
                        <c:forEach var="espId" items="${espOptions}">
                            <option value="<c:out value='${espId}'/>" ${espFilter eq espId ? 'selected' : ''}>
                                <c:out value="${espId}"/>
                            </option>
                        </c:forEach>
                    </select>
                    <label for="dc-status-filter">Trạng thái</label>
                    <select id="dc-status-filter" name="status">
                        <option value="all" ${empty statusFilter or statusFilter eq 'all' ? 'selected' : ''}>Tất cả</option>
                        <option value="ON" ${statusFilter eq 'ON' ? 'selected' : ''}>ON</option>
                        <option value="OFF" ${statusFilter eq 'OFF' ? 'selected' : ''}>OFF</option>
                    </select>
                    <button type="submit">Lọc</button>
                    <a href="${pageContext.request.contextPath}/DeviceControlController">Xóa lọc</a>
                </form>
                <section class="dc-device-grid" aria-label="Danh sách công tắc">
                    <c:forEach var="relay" items="${requestScope.list}">
                        <article class="dc-relay-card" data-on="${relay.status eq 'ON'}" data-esp-active="${relay.espActive}">
                            <div class="dc-card-heading">
                                <div class="dc-relay-meta">
                                    <span class="dc-gpio">GPIO ${relay.gpioPin}</span>
                                    <p class="dc-location">
                                        <c:out value="${relay.deviceId}" /> ·
                                        <c:out value="${relay.espName}" />
                                    </p>
                                </div>
                            </div>
                            <div class="dc-card-body">


                                </span>
                                <div class="dc-relay-identity">
                                    <h2 class="dc-relay-name">
                                        <c:out value="${relay.switchName}" />
                                    </h2>
                                </div>
                            </div>
                            <div class="dc-card-footer">
                                <div class="dc-status-box">
                                    <span class="dc-status-label">TRẠNG THÁI</span>
                                    <!-- So sánh trực tiếp với tên của Enum -->
                                    <span class="dc-relay-state">${relay.status eq 'ON' ? 'ON' : 'OFF'}</span>
                                </div>

                                <form action="MainController" method="POST">
                                    <!-- Các input hidden để truyền dữ liệu -->
                                    <input type="hidden" name="action" value="CHANGE_SWITCH_STAGE">
                                    <input name="id" value="${relay.switchId}" type="hidden">
                                    <input name="page" value="${requestScope.pageNumber}" type="hidden">
                                    <input type="hidden" name="esp" value="<c:out value='${espFilter}'/>">
                                    <input type="hidden" name="status" value="<c:out value='${statusFilter}'/>">

                                    <!-- Nút bấm đồng bộ trạng thái bật/tắt theo Enum -->
                                    <button type="submit" name="uiOp" value="toggleRelay"
                                            class="dc-switch ${relay.status eq 'ON' ? 'is-checked' : ''}" 
                                            role="switch" 
                                            aria-checked="${relay.status eq 'ON'}"
                                            aria-label="Đổi trạng thái ${fn:escapeXml(relay.espName)}"
                                            <c:if test="${not relay.canControl}">disabled="disabled" title="Tài khoản chưa được cấp quyền điều khiển switch này"</c:if>>
                                        <span class="dc-switch-track" aria-hidden="true">
                                            <span></span>
                                        </span>
                                    </button>
                                </form>
                            </div>
                            <c:if test="${not empty requestScope.switchSyncErrors[relay.switchId]}">
                                <p class="dc-sync-error" role="status">
                                    <c:out value="${requestScope.switchSyncErrors[relay.switchId]}" />
                                </p>
                            </c:if>
                        </article>
                    </c:forEach>
                </section>
                <c:if test="${requestScope.totalSwitches gt 0}">
                    <div class="dc-pagination-row">
                        <p class="dc-pagination-summary">
                            Hiển thị ${requestScope.firstSwitchNumber}–${requestScope.lastSwitchNumber}
                            trên ${requestScope.totalSwitches} switch
                        </p>
                        <c:if test="${requestScope.pageCount gt 1}">
                            <nav class="dc-pagination" aria-label="Phân trang danh sách switch">
                                <c:if test="${requestScope.pageNumber gt 1}">
                                    <a href="${pageContext.request.contextPath}/DeviceControlController?page=${requestScope.pageNumber - 1}&amp;esp=${espFilter}&amp;status=${statusFilter}">
                                        Trước
                                    </a>
                                </c:if>
                                <c:forEach var="page" begin="1" end="${requestScope.pageCount}">
                                    <c:choose>
                                        <c:when test="${page eq requestScope.pageNumber}">
                                            <span aria-current="page">${page}</span>
                                        </c:when>
                                        <c:otherwise>
                                            <a href="${pageContext.request.contextPath}/DeviceControlController?page=${page}&amp;esp=${espFilter}&amp;status=${statusFilter}">
                                                ${page}
                                            </a>
                                        </c:otherwise>
                                    </c:choose>
                                </c:forEach>
                                <c:if test="${requestScope.pageNumber lt requestScope.pageCount}">
                                    <a href="${pageContext.request.contextPath}/DeviceControlController?page=${requestScope.pageNumber + 1}&amp;esp=${espFilter}&amp;status=${statusFilter}">
                                        Sau
                                    </a>
                                </c:if>
                            </nav>
                        </c:if>
                    </div>
                </c:if>
                <footer class="ui-demo-footer">

                </footer>
            </main>
        </div>
    </body>

</html>
