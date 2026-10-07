<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
            <c:if test="${empty requestScope.ui}">
                <c:redirect url="/MainController">
                    <c:param name="action" value="CONTROL_HISTORY" />
                </c:redirect>
            </c:if>
            <c:set var="activePage" value="CONTROL_HISTORY" scope="request" />
            <!DOCTYPE html>
            <html lang="vi">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Lịch Sử Điều Khiển | ESP32 IoT Hub</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link
                    href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
                    rel="stylesheet">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/controlHistoryCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
            </head>

            <body class="user-manager-page control-history-page">
                <div class="ui-shell" ${ui.dialog ? 'inert' : '' }>
                    <%@ include file="sideBar.jspf" %>
                        
                        <main class="um-main ch-main" id="main-content">
                            <c:if test="${not empty uiMessage}">
                                <p class="ui-notice" role="status">
                                    <c:out value="${uiMessage}" />
                                </p>
                            </c:if>
                            <c:if test="${not empty uiError and not ui.dialog}">
                                <p class="ui-notice ui-error" role="alert">
                                    <c:out value="${uiError}" />
                                </p>
                            </c:if>
                            <header class="ch-page-heading">
                                <div class="ch-title-line">
                                    <h1>Lịch Sử Điều Khiển Thiết Bị</h1>
                                    <span class="ch-version-tag">AUDIT_LOG_V2</span>
                                </div>
                                <p>Tra cứu thao tác rơ-le từ Web Interface, Servlet API và Scheduler.</p>
                            </header>
                            <section class="ch-log-panel">
                                <c:url var="refreshUrl" value="/MainController">
                                    <c:param name="action" value="CONTROL_HISTORY" />
                                    <c:param name="q" value="${ui.q}" />
                                    <c:param name="device" value="${ui.device}" />
                                    <c:param name="command" value="${ui.command}" />
                                    <c:param name="result" value="${ui.result}" />
                                    <c:param name="sort" value="${ui.sort}" />
                                </c:url>
                                <header class="ch-panel-heading">
                                    <div class="ch-panel-identity">
                                        <i class="fa-solid fa-table-list" aria-hidden="true">
                                        </i>
                                        <h2>Nhật Ký Tác Vụ Rơ-le</h2>
                                        <span class="ch-record-count">${ui.historyTotal} bản ghi minh họa</span>
                                    </div>
                                    <div class="ch-refresh-group">
                                        <a class="ch-refresh" href="<c:out value='${refreshUrl}'/>"
                                            aria-label="Tải lại nhật ký">
                                            <i class="fa-solid fa-arrows-rotate" aria-hidden="true">
                                            </i>
                                        </a>
                                        <span>ĐỌC DỮ LIỆU: ${ui.readAt} (UTC+7)</span>
                                    </div>
                                </header>
                                <form class="ch-toolbar" action="${pageContext.request.contextPath}/MainController"
                                    method="get">
                                    <input type="hidden" name="action" value="CONTROL_HISTORY">
                                    <input type="hidden" name="sort" value="<c:out value='${ui.sort}' />">
                                    <div class="ch-search">
                                        <label class="um-sr-only" for="ch-search">Tìm lịch sử</label>
                                        <i class="fa-solid fa-magnifying-glass" aria-hidden="true">
                                        </i>
                                        <input id="ch-search" name="q" type="search" value="<c:out value='${ui.q}' />"
                                            placeholder="Tìm #ID, tài khoản, switch, GPIO...">
                                    </div>
                                    <label class="um-sr-only" for="ch-device-filter">Thiết bị</label>
                                    <select id="ch-device-filter" name="device">
                                        <option value="all">Thiết bị: Tất cả</option>
                                        <c:forEach var="device" items="${ui.historyDevices}">
                                            <option value="<c:out value='${device}' />" ${ui.device eq device
                                                ? 'selected' : '' }>
                                                <c:out value="${device}" />
                                            </option>
                                        </c:forEach>
                                    </select>
                                    <label class="um-sr-only" for="ch-command-filter">Lệnh</label>
                                    <select id="ch-command-filter" name="command">
                                        <option value="all">Lệnh: Tất cả</option>
                                        <option value="ON" ${ui.command eq 'ON' ? 'selected' : '' }>Bật (ON)</option>
                                        <option value="OFF" ${ui.command eq 'OFF' ? 'selected' : '' }>Tắt (OFF)</option>
                                    </select>
                                    <label class="um-sr-only" for="ch-result-filter">Kết quả</label>
                                    <select id="ch-result-filter" name="result">
                                        <option value="all">Kết quả: Tất cả</option>
                                        <option value="success" ${ui.result eq 'success' ? 'selected' : '' }>Thành công
                                        </option>
                                        <option value="error" ${ui.result eq 'error' ? 'selected' : '' }>Lỗi</option>
                                        <option value="TIMEOUT" ${ui.result eq 'TIMEOUT' ? 'selected' : '' }>TIMEOUT
                                        </option>
                                        <option value="NODE_OFFLINE" ${ui.result eq 'NODE_OFFLINE' ? 'selected' : '' }>
                                            NODE_OFFLINE</option>
                                        <option value="DEMO" ${ui.result eq 'DEMO' ? 'selected' : '' }>Mô phỏng UI
                                        </option>
                                    </select>
                                    <button class="ui-get-button" type="submit">Lọc</button>
                                    <a class="ch-clear-filters"
                                        href="${pageContext.request.contextPath}/MainController?action=CONTROL_HISTORY">Xóa
                                        lọc</a>
                                </form>
                                <c:url var="sortUrl" value="/MainController">
                                    <c:param name="action" value="CONTROL_HISTORY" />
                                    <c:param name="q" value="${ui.q}" />
                                    <c:param name="device" value="${ui.device}" />
                                    <c:param name="command" value="${ui.command}" />
                                    <c:param name="result" value="${ui.result}" />
                                    <c:param name="sort" value="${ui.sort eq 'asc' ? 'desc' : 'asc'}" />
                                </c:url>
                                <div class="ch-table-scroll" tabindex="0" role="region"
                                    aria-label="Bảng nhật ký, có thể cuộn ngang">
                                    <table class="ch-table">
                                        <colgroup>
                                            <col style="width:10%">
                                            <col style="width:16%">
                                            <col style="width:16%">
                                            <col style="width:14%">
                                            <col style="width:20%">
                                            <col style="width:10%">
                                            <col style="width:14%">
                                        </colgroup>
                                        <thead>
                                            <tr>
                                                <th scope="col">#ID</th>
                                                <th scope="col"
                                                    aria-sort="${ui.sort eq 'asc' ? 'ascending' : 'descending'}">
                                                    <a href="<c:out value='${sortUrl}'/>" id="ch-sort-time">THỜI GIAN <i
                                                            class="fa-solid ${ui.sort eq 'asc' ? 'fa-arrow-up-short-wide' : 'fa-arrow-down-short-wide'}"
                                                            aria-hidden="true">
                                                        </i>
                                                    </a>
                                                </th>
                                                <th scope="col">NGƯỜI THỰC HIỆN</th>
                                                <th scope="col">THIẾT BỊ ĐÍCH</th>
                                                <th scope="col">SWITCH &amp; GPIO</th>
                                                <th scope="col">LỆNH</th>
                                                <th scope="col">KẾT QUẢ (RTT)</th>
                                            </tr>
                                        </thead>
                                        <tbody id="ch-rows">
                                            <c:forEach var="record" items="${ui.history}">
                                                <tr>
                                                    <td>
                                                        <a class="ch-log-id"
                                                            href="${pageContext.request.contextPath}/MainController?action=CONTROL_HISTORY&amp;form=trace&amp;id=${record.id}">#LOG-${record.id}</a>
                                                    </td>
                                                    <td>
                                                        <time class="ch-timestamp" datetime="${record.timestamp}">
                                                            <c:out value="${record.timestampLabel}" />
                                                        </time>
                                                    </td>
                                                    <td>
                                                        <div class="ch-actor">
                                                            <span class="ch-avatar is-${record.actorInfo.style}"
                                                                aria-hidden="true">
                                                                <c:out value="${record.actorInfo.initials}" />
                                                            </span>
                                                            <div class="ch-actor-info">
                                                                <span class="ch-actor-name">
                                                                    <c:out value="${record.actorInfo.username}" />
                                                                </span>
                                                                <span
                                                                    class="ch-actor-role is-${record.actorInfo.style}">
                                                                    <c:out value="${record.actorInfo.role}" />
                                                                </span>
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <span class="ch-device-name">
                                                            <c:out value="${record.device}" />
                                                        </span>
                                                        <span class="ch-device-ip">
                                                            <c:out value="${record.ip}" />
                                                        </span>
                                                    </td>
                                                    <td>
                                                        <span class="ch-switch-name">
                                                            <c:out value="${record.switchName}" />
                                                        </span>
                                                        <span
                                                            class="ch-gpio ${record.resultClass eq 'is-error' ? 'is-error' : ''}">GPIO
                                                            ${record.gpio} (Relay ${record.relay})</span>
                                                    </td>
                                                    <td>
                                                        <span
                                                            class="ch-command ${record.command eq 'OFF' ? 'is-off' : ''}">
                                                            <span class="ch-command-dot" aria-hidden="true">
                                                            </span>${record.command eq 'ON' ? 'BẬT (1)' : 'TẮT
                                                            (0)'}</span>
                                                    </td>
                                                    <td>
                                                        <div class="ch-result ${record.resultClass}">
                                                            <i class="fa-solid ${record.resultClass eq 'is-error' ? 'fa-circle-xmark' : record.resultClass eq 'is-demo' ? 'fa-flask' : 'fa-circle-check'}"
                                                                aria-hidden="true">
                                                            </i>
                                                            <div class="ch-result-info">
                                                                <span class="ch-result-label">
                                                                    <c:out value="${record.resultLabel}" />
                                                                </span>
                                                                <span class="ch-rtt">${empty record.rtt ? '(N/A)' :
                                                                    '('}${empty record.rtt ? '' : record.rtt}${empty
                                                                    record.rtt ? '' : 'ms)'}</span>
                                                            </div>
                                                        </div>
                                                    </td>
                                                </tr>
                                            </c:forEach>
                                            <c:if test="${empty ui.history}">
                                                <tr>
                                                    <td colspan="7" class="ui-empty">Không tìm thấy nhật ký phù hợp.
                                                    </td>
                                                </tr>
                                            </c:if>
                                        </tbody>
                                    </table>
                                </div>
                                <footer class="ch-panel-footer">
                                    <span>Hiển thị ${fn:length(ui.history)} / ${ui.historyTotal} bản ghi</span>
                                    <span>Giờ Việt Nam (UTC+7) · Chọn #ID để xem chi tiết</span>
                                </footer>
                            </section>
                            <footer class="ui-demo-footer">
                                
                                <form action="${pageContext.request.contextPath}/MainController" method="post">
                                    <%@ include file="uiPostFields.jspf" %>
                                        <button class="um-button um-button-secondary" type="submit" name="uiOp"
                                            value="resetPreview">Đặt lại dữ liệu mẫu</button>
                                </form>
                            </footer>
                        </main>
                </div>
                <c:if test="${ui.dialog}">
                    <%@ include file="uiDialogStart.jspf" %>
                        <p class="um-dialog-description">#LOG-${ui.target.id} ·
                            <c:out value="${ui.target.switchName}" />
                        </p>
                        <h3 class="ch-payload-title">Telemetry Trace Payload</h3>
                        <pre class="ch-payload ui-readonly-code" tabindex="0">
<code>
<c:out value="${ui.traceJson}" />
</code>
</pre>
                        <p class="ch-trace-note">Payload minh họa. Chọn nội dung và dùng Ctrl+C, hoặc tải file JSON.</p>
                        <div class="um-dialog-footer">
                            <a class="um-button um-button-secondary"
                                href="${pageContext.request.contextPath}/MainController?action=CONTROL_HISTORY&amp;uiOp=downloadTrace&amp;id=${ui.target.id}">Tải
                                JSON</a>
                            <a class="um-button um-button-primary"
                                href="${pageContext.request.contextPath}/MainController?action=CONTROL_HISTORY">Đóng</a>
                        </div>
                        </dialog>
                </c:if>
            </body>

            </html>