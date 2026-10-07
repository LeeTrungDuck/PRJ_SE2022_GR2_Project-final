<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
            <c:if test="${empty requestScope.ui}">
                <c:redirect url="/MainController">
                    <c:param name="action" value="SCHEDULE_MANAGER" />
                </c:redirect>
            </c:if>
            <c:set var="activePage" value="SCHEDULE_MANAGER" scope="request" />
            <!DOCTYPE html>
            <html lang="vi">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Lịch Hẹn Giờ | ESP32 IoT Hub</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link
                    href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
                    rel="stylesheet">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/scheduleManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
            </head>

            <body class="user-manager-page schedule-manager-page">
                <div class="ui-shell" ${ui.dialog ? 'inert' : '' }>
                    <%@ include file="sideBar.jspf" %>
                        
                        <main class="um-main sm-main" id="main-content">
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
                            <div class="sm-context-bar">
                                <span>GIỜ VIỆT NAM (UTC+7):
                                    <c:out value="${ui.clock}" />
                                </span>
                            </div>
                            <header class="sm-page-heading">
                                <div class="sm-heading-identity">
                                    <span class="sm-heading-icon">
                                        <i class="fa-solid fa-stopwatch" aria-hidden="true">
                                        </i>
                                    </span>
                                    <div>
                                        <h1>Lịch Hẹn Giờ <span>(Schedule Management)</span>
                                        </h1>
                                        <p>Cấu hình giờ bật/tắt thiết bị theo ngày trong tuần hoặc một lần.</p>
                                    </div>
                                </div>
                                <div class="sm-next-run">
                                    <i class="fa-solid fa-hourglass-half" aria-hidden="true">
                                    </i> LẦN CHẠY KẾ: <strong>
                                        <c:out value="${ui.nextSummary}" />
                                    </strong>
                                </div>
                            </header>
                            <div class="sm-layout">
                                <section class="sm-list-section" aria-label="Danh sách lịch hẹn">
                                    <form class="sm-toolbar" action="${pageContext.request.contextPath}/MainController"
                                        method="get">
                                        <input name="action" value="SCHEDULE_MANAGER" type="hidden">
                                        <div class="sm-search">
                                            <label class="um-sr-only" for="sm-search">Tìm lịch hẹn</label>
                                            <i class="fa-solid fa-magnifying-glass" aria-hidden="true">
                                            </i>
                                            <input id="sm-search" name="q" type="search"
                                                value="<c:out value='${ui.q}'/>"
                                                placeholder="Tìm tên lịch, tải, GPIO...">
                                        </div>
                                        <label class="um-sr-only" for="sm-status">Trạng thái lịch</label>
                                        <select id="sm-status" name="status">
                                            <option value="all">Tất cả (${ui.scheduleTotal})</option>
                                            <option value="enabled" ${ui.status eq 'enabled' ? 'selected' : '' }>Đang
                                                bật (${ui.scheduleEnabled})</option>
                                            <option value="paused" ${ui.status eq 'paused' ? 'selected' : '' }>Tạm dừng
                                                (${ui.schedulePaused})</option>
                                        </select>
                                        <label class="um-sr-only" for="sm-filter-relay">Lọc theo relay</label>
                                        <select id="sm-filter-relay" name="relay">
                                            <option value="all">Tất cả relay</option>
                                            <c:forEach var="relay" items="${ui.scheduleRelays}">
                                                <option value="${relay.id}" ${ui.relayFilter eq relay.id ? 'selected'
                                                    : '' }>
                                                    <c:out value="${relay.name}" /> (GPIO ${relay.gpio})
                                                </option>
                                            </c:forEach>
                                        </select>
                                        <button class="ui-get-button" type="submit">Lọc</button>
                                    </form>
                                    <div class="sm-schedules">
                                        <c:forEach var="schedule" items="${ui.schedules}">
                                            <article
                                                class="sm-schedule ${not schedule.enabled ? 'is-paused' : schedule.isNext ? 'is-next' : schedule.action eq 'OFF' ? 'is-off' : 'is-on'}">
                                                <div class="sm-time-block">
                                                    <strong>
                                                        <c:out value="${schedule.time}" />
                                                    </strong>
                                                    <span>${schedule.mode eq 'once' ? 'MỘT LẦN' : 'LẶP TUẦN'}</span>
                                                </div>
                                                <div class="sm-schedule-info">
                                                    <div class="sm-title-line">
                                                        <h2>
                                                            <c:out value="${schedule.name}" />
                                                        </h2>
                                                        <span
                                                            class="sm-action-badge ${schedule.action eq 'OFF' ? 'is-off' : ''}">${schedule.action
                                                            eq 'ON' ? 'BẬT (ON)' : 'TẮT (OFF)'}</span>
                                                        <c:if test="${schedule.isNext}">
                                                            <span class="sm-next-badge">KẾ TIẾP</span>
                                                        </c:if>
                                                    </div>
                                                    <p class="sm-switch-line">
                                                        <code>GPIO ${schedule.relay.gpio}</code> ·
                                                        <c:out value="${schedule.relay.name}" /> (
                                                        <c:out value="${schedule.relay.node}" />)
                                                    </p>
                                                    <div class="sm-repeat-line">
                                                        <c:choose>
                                                            <c:when test="${schedule.mode eq 'weekly'}">
                                                                <c:forEach var="day" items="${ui.weekDays}">
                                                                    <span
                                                                        class="${schedule.days.contains(day.value) ? 'is-selected' : ''}">${day.label}</span>
                                                                </c:forEach>
                                                            </c:when>
                                                            <c:otherwise>
                                                                <span>
                                                                    <c:out value="${schedule.date}" />
                                                                </span>
                                                            </c:otherwise>
                                                        </c:choose>
                                                    </div>
                                                    <p class="sm-occurrence">
                                                        <c:out value="${schedule.nextLabel}" />
                                                    </p>
                                                    <c:if test="${not empty schedule.lastDemo}">
                                                        <small>Chạy thử UI:
                                                            <c:out value="${schedule.lastDemo}" />
                                                        </small>
                                                    </c:if>
                                                </div>
                                                <div class="sm-schedule-controls">
                                                    <span class="sm-state">${schedule.enabled ? 'ĐANG BẬT' : 'TẠM
                                                        DỪNG'}</span>
                                                    <form class="ui-inline-form"
                                                        action="${pageContext.request.contextPath}/MainController"
                                                        method="post">
                                                        <%@ include file="uiPostFields.jspf" %>
                                                            <input name="id" value="${schedule.id}" type="hidden">
                                                            <button class="sm-toggle" type="submit" name="uiOp"
                                                                value="toggleSchedule" role="switch"
                                                                aria-checked="${schedule.enabled}"
                                                                aria-label="Đổi trạng thái ${fn:escapeXml(schedule.name)}"
                                                                ${schedule.expired ? 'disabled' : '' }>
                                                                <span class="sm-toggle-track" aria-hidden="true">
                                                                    <span>
                                                                    </span>
                                                                </span>
                                                            </button>
                                                            <button class="sm-icon-button" type="submit" name="uiOp"
                                                                value="runSchedule"
                                                                aria-label="Chạy thử ${fn:escapeXml(schedule.name)}">
                                                                <i class="fa-solid fa-play" aria-hidden="true">
                                                                </i>
                                                            </button>
                                                    </form>
                                                    <a class="sm-icon-button"
                                                        href="${pageContext.request.contextPath}/MainController?action=SCHEDULE_MANAGER&amp;edit=${schedule.id}"
                                                        aria-label="Sửa ${fn:escapeXml(schedule.name)}">
                                                        <i class="fa-solid fa-pen" aria-hidden="true">
                                                        </i>
                                                    </a>
                                                    <a class="sm-icon-button"
                                                        href="${pageContext.request.contextPath}/MainController?action=SCHEDULE_MANAGER&amp;form=delete&amp;id=${schedule.id}"
                                                        aria-label="Xóa ${fn:escapeXml(schedule.name)}">
                                                        <i class="fa-solid fa-trash-can" aria-hidden="true">
                                                        </i>
                                                    </a>
                                                </div>
                                            </article>
                                        </c:forEach>
                                        <c:if test="${empty ui.schedules}">
                                            <p class="ui-empty">Không có lịch phù hợp.</p>
                                        </c:if>
                                    </div>
                                </section>
                                <aside class="sm-side-panel">
                                    <section class="sm-editor">
                                        <header class="sm-editor-heading">
                                            <h2>
                                                <i class="fa-solid fa-stopwatch" aria-hidden="true">
                                                </i> ${empty ui.edit.id ? 'Thêm Lịch Hẹn Giờ Mới' : 'Chỉnh Sửa Lịch
                                                Hẹn'}
                                            </h2>
                                            <span class="sm-editor-tag">CRON / AUTO</span>
                                        </header>
                                        <form id="sm-form" action="${pageContext.request.contextPath}/MainController"
                                            method="post">
                                            <%@ include file="uiPostFields.jspf" %>
                                                <input name="id" value="<c:out value='${ui.edit.id}'/>" type="hidden">
                                                <div class="sm-form-field">
                                                    <label for="sm-relay">SWITCH / RƠ-LE ĐIỀU KHIỂN</label>
                                                    <select id="sm-relay" name="switchId" required>
                                                        <c:forEach var="relay" items="${ui.scheduleRelays}">
                                                            <option value="${relay.id}" ${ui.edit.switchId eq relay.id
                                                                ? 'selected' : '' }>
                                                                <c:out value="${relay.name}" /> · GPIO ${relay.gpio} ·
                                                                <c:out value="${relay.node}" />
                                                            </option>
                                                        </c:forEach>
                                                    </select>
                                                </div>
                                                <fieldset class="sm-fieldset">
                                                    <legend>HÀNH ĐỘNG KHI KÍCH HOẠT</legend>
                                                    <div class="sm-command-options">
                                                        <label class="sm-command-choice">
                                                            <input name="command" type="radio" value="ON"
                                                                ${ui.edit.action eq 'ON' ? 'checked' : '' } required>
                                                            <span>BẬT (ON)</span>
                                                        </label>
                                                        <label class="sm-command-choice">
                                                            <input name="command" type="radio" value="OFF"
                                                                ${ui.edit.action eq 'OFF' ? 'checked' : '' }>
                                                            <span>TẮT (OFF)</span>
                                                        </label>
                                                    </div>
                                                </fieldset>
                                                <div class="sm-form-field">
                                                    <label for="sm-time">GIỜ THỰC THI <span>Định dạng 24h</span>
                                                    </label>
                                                    <div class="sm-time-row">
                                                        <input id="sm-time" name="time" type="time"
                                                            value="<c:out value='${ui.edit.time}'/>" step="60" required>
                                                        <button name="uiOp" value="shift15" type="submit"
                                                            formnovalidate>+15m</button>
                                                        <button name="uiOp" value="shift60" type="submit"
                                                            formnovalidate>+1h</button>
                                                    </div>
                                                </div>
                                                <fieldset class="sm-fieldset ui-repeat">
                                                    <legend>LẶP LẠI</legend>
                                                    <div class="sm-presets">
                                                        <button name="uiOp" value="repeatDaily" type="submit"
                                                            formnovalidate>Hằng ngày</button>
                                                        <button name="uiOp" value="repeatWeekdays" type="submit"
                                                            formnovalidate>T2 - T6</button>
                                                        <button name="uiOp" value="repeatWeekend" type="submit"
                                                            formnovalidate>Cuối tuần</button>
                                                        <button name="uiOp" value="repeatOnce" type="submit"
                                                            formnovalidate>Một lần</button>
                                                    </div>
                                                    <input id="sm-weekly" name="mode" type="radio" value="weekly"
                                                        ${ui.edit.mode eq 'weekly' ? 'checked' : '' }>
                                                    <label for="sm-weekly" class="ui-repeat-label">Theo tuần</label>
                                                    <input id="sm-once" name="mode" type="radio" value="once"
                                                        ${ui.edit.mode eq 'once' ? 'checked' : '' }>
                                                    <label for="sm-once" class="ui-repeat-label">Một lần</label>
                                                    <div class="sm-weekly-days">
                                                        <div class="sm-day-buttons">
                                                            <c:forEach var="day" items="${ui.weekDays}">
                                                                <label>
                                                                    <input name="days" type="checkbox"
                                                                        value="${day.value}"
                                                                        ${ui.edit.days.contains(day.value) ? 'checked'
                                                                        : '' }>
                                                                    <span>${day.label}</span>
                                                                </label>
                                                            </c:forEach>
                                                        </div>
                                                    </div>
                                                    <div class="sm-date-field sm-form-field">
                                                        <label for="sm-date">NGÀY CHẠY MỘT LẦN</label>
                                                        <input id="sm-date" name="date" type="date"
                                                            value="<c:out value='${ui.edit.date}'/>">
                                                        <small>Chọn ngày và giờ trong tương lai (UTC+7).</small>
                                                    </div>
                                                </fieldset>
                                                <div class="sm-form-field">
                                                    <label for="sm-name">TÊN LỊCH HẸN</label>
                                                    <input id="sm-name" name="name" type="text"
                                                        value="<c:out value='${ui.edit.name}'/>"
                                                        placeholder="Ví dụ: Hút mùi trước ca sản xuất" maxlength="120"
                                                        required>
                                                </div>
                                                <div class="sm-form-footer">
                                                    <a class="um-button um-button-secondary"
                                                        href="${pageContext.request.contextPath}/MainController?action=SCHEDULE_MANAGER">Hủy
                                                        / Đặt lại</a>
                                                    <button name="uiOp" value="saveSchedule" type="submit"
                                                        class="um-button um-button-primary">
                                                        <i class="fa-solid fa-circle-check" aria-hidden="true">
                                                        </i> Lưu Lịch Hẹn Giờ</button>
                                                </div>
                                        </form>
                                    </section>
                                    <section class="sm-guidance">
                                        <h2>
                                            <i class="fa-solid fa-circle-info" aria-hidden="true">
                                            </i> Lịch tự động theo ngày lặp
                                        </h2>
                                        <p>Lần chạy kế được tính lại khi tải trang, theo giờ Việt Nam (UTC+7). Lịch tạm
                                            dừng không nằm trong lần chạy kế.</p>
                                        <p>Lịch được lưu trong session minh họa; chưa tự chạy qua MQTT hoặc RTC của
                                            ESP32.</p>
                                    </section>
                                </aside>
                            </div>
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
                        <p class="um-dialog-description">Xóa lịch <strong>
                                <c:out value="${ui.target.name}" />
                            </strong>?</p>
                        <form action="${pageContext.request.contextPath}/MainController" method="post">
                            <%@ include file="uiPostFields.jspf" %>
                                <input name="id" value="${ui.target.id}" type="hidden">
                                <div class="um-dialog-footer">
                                    <a class="um-button um-button-secondary"
                                        href="${pageContext.request.contextPath}/MainController?action=SCHEDULE_MANAGER">Hủy</a>
                                    <button class="um-button um-button-danger" type="submit" name="uiOp"
                                        value="deleteSchedule">Xóa Lịch</button>
                                </div>
                        </form>
                        </dialog>
                </c:if>
            </body>

            </html>