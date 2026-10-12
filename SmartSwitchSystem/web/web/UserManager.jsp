<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
            <c:if test="${empty sessionScope.LOGIN_USER or empty requestScope.ui}">
                <c:redirect url="/MainController">
                    <c:param name="action" value="USER_MANAGER" />
                </c:redirect>
            </c:if>
            <c:set var="activePage" value="USER_MANAGER" scope="request" />
            <!DOCTYPE html>
            <html lang="vi">

            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Quản Lý Tài Khoản | ESP32 IoT Hub</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link
                    href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
                    rel="stylesheet">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
                <link rel="stylesheet" href="${pageContext.request.contextPath}/css/noJsUi.css?v=1">
            </head>

            <body class="user-manager-page">
                <div class="ui-shell" ${ui.dialog ? 'inert' : '' }>
                    <%@ include file="sideBar.jspf" %>
                       
                        <main class="um-main " id="main-content">
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
                            <header class="um-page-heading">
                                <h1>Quản Lý Tài Khoản Người Dùng <span>(User Management)</span>
                                </h1>
                                <div class="um-heading-bottom">
                                    <p>Quản lý tài khoản, vai trò và trạng thái hoạt động trong hệ thống.</p>
                                    <a class="um-button um-button-primary"
                                        href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;form=add">
                                        <i class="fa-solid fa-user-plus" aria-hidden="true">
                                        </i> Thêm Người Dùng Mới</a>
                                </div>
                            </header>
                            <div class="um-table-panel">
                                <div class="um-table-scroll" tabindex="0" role="region"
                                    aria-label="Danh sách người dùng">
                                    <table class="um-table">
                                        <thead>
                                            <tr>
                                                <th scope="col">NGƯỜI DÙNG</th>
                                                <th scope="col">VAI TRÒ</th>
                                                <th scope="col">TRẠNG THÁI</th>
                                                <th scope="col" style="text-align: center;">THAO TÁC HỆ THỐNG</th>
                                            </tr>
                                        </thead>
                                        <tbody id="um-user-list">
                                            <c:forEach var="u" items="${ui.users}">
                                                <tr class="${u.active ? '' : 'um-user-locked'}">
                                                    <td>
                                                        <div class="um-user-identity">
                                                            
                                                            <div>
                                                                <div class="um-user-name">
                                                                    <strong>
                                                                        <c:out value="${u.fullName}" />
                                                                    </strong>
                                                                    <c:if test="${u.superAdmin}">
                                                                        <span class="um-mini-badge">BẠN</span>
                                                                    </c:if>
                                                                </div>
                                                                <span class="um-username">@
                                                                    <c:out value="${u.userName}" />
                                                                </span>
                                                                <span class="um-email">
                                                                    <c:out value="${u.email}" />
                                                                </span>
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td>
                                                        <span class="um-role-badge um-role-${u.roleClass}">
                                                            <c:out value="${u.roleLabel}" />
                                                        </span>
                                                    </td>
                                                    <td>
                                                        <span
                                                            class="um-status-badge ${u.active ? '' : 'um-status-locked'}">${u.active
                                                            ? 'HOẠT ĐỘNG' : 'ĐÃ KHÓA'}</span>
                                                    </td>
                                                   
                                                    <td>
                                                        <div class="um-row-actions">
                                                            <form
                                                                action="${pageContext.request.contextPath}/MainController"
                                                                method="post">
                                                                <%@ include file="uiPostFields.jspf" %>
                                                                    <input type="hidden" name="id" value="${u.id}">
                                                                    <select name="role" class="um-role-select"
                                                                        aria-label="Vai trò của ${fn:escapeXml(u.fullName)}"
                                                                        ${u.superAdmin ? 'disabled' : '' }>
                                                                        <option value="ADMIN" ${u.role eq 'ADMIN'
                                                                            ? 'selected' : '' }>Admin</option>
                                                                        <option value="OPERATOR" ${u.role
                                                                            eq 'OPERATOR' ? 'selected' : '' }>Operator
                                                                        </option>
                                                                        <option value="VIEWER" ${u.role eq 'VIEWER'
                                                                            ? 'selected' : '' }>Viewer</option>
                                                                    </select>
                                                                    <button class="um-icon-button" name="uiOp"
                                                                        value="changeRole" type="submit"
                                                                        aria-label="Lưu vai trò" ${u.superAdmin
                                                                        ? 'disabled' : '' }>
                                                                        <i class="fa-solid fa-check" aria-hidden="true">
                                                                        </i>
                                                                    </button>
                                                            </form>
                                                            <c:choose>
                                                                <c:when test="${u.superAdmin}">
                                                                    <span class="um-mini-badge">BẠN</span>
                                                                </c:when>
                                                                <c:otherwise><a class="um-icon-button" href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;form=edit&amp;id=${u.id}" aria-label="Sửa tài khoản"><i class="fa-solid fa-pen" aria-hidden="true"></i></a>
                                                                    <a class="um-icon-button"
                                                                        href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;form=password&amp;id=${u.id}"
                                                                        aria-label="Đặt lại mật khẩu">
                                                                        <i class="fa-solid fa-key" aria-hidden="true">
                                                                        </i>
                                                                    </a>
                                                                    <a class="um-icon-button"
                                                                        href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;form=lock&amp;id=${u.id}"
                                                                        aria-label="Đổi trạng thái">
                                                                        <i class="fa-solid fa-lock" aria-hidden="true">
                                                                        </i>
                                                                    </a>
                                                                    <a class="um-icon-button"
                                                                        href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;form=delete&amp;id=${u.id}"
                                                                        aria-label="Xóa tài khoản">
                                                                        <i class="fa-solid fa-trash-can"
                                                                            aria-hidden="true">
                                                                        </i>
                                                                    </a>
                                                                </c:otherwise>
                                                            </c:choose>
                                                        </div>
                                                    </td>
                                                </tr>
                                            </c:forEach>
                                        </tbody>
                                    </table>
                                </div>
                                <footer class="um-table-footer">
                                    <span>Tài khoản đang đăng nhập và Admin cuối cùng được bảo vệ khỏi đổi quyền, khóa và xóa.</span>
                                    <div class="ui-controls">
                                        <c:if test="${ui.pageNumber gt 1}">
                                            <a class="um-button um-button-secondary"
                                                href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;p=${ui.pageNumber-1}">Trang
                                                trước</a>
                                        </c:if>
                                        <span>${ui.pageNumber} / ${ui.pageCount}</span>
                                        <c:if test="${ui.pageNumber lt ui.pageCount}">
                                            <a class="um-button um-button-secondary"
                                                href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER&amp;p=${ui.pageNumber+1}">Trang
                                                kế</a>
                                        </c:if>
                                    </div>
                                </footer>
                            </div>
                            
                        </main>
                </div>
                <c:if test="${ui.dialog}">
<%@ include file="uiDialogStart.jspf" %>
<form action="${pageContext.request.contextPath}/MainController" method="post">
    <%@ include file="uiPostFields.jspf" %>
    <input name="id" value="<c:out value='${ui.target.id}'/>" type="hidden">
    <c:choose><c:when test="${ui.form eq 'add' or ui.form eq 'edit'}">
        <div class="um-form-grid">
            <div class="um-field"><label for="um-fullname">Họ và tên</label><input id="um-fullname" name="fullName" value="<c:out value='${ui.edit.fullName}'/>" maxlength="50" required autofocus></div>
            <div class="um-field"><label for="um-username">Tên đăng nhập</label><input id="um-username" name="userName" value="<c:out value='${ui.edit.userName}'/>" minlength="3" maxlength="50" required ${ui.form eq 'edit' ? 'readonly' : ''}></div>
            <div class="um-field"><label for="um-email">Email</label><input id="um-email" name="email" type="email" value="<c:out value='${ui.edit.email}'/>" maxlength="50" required></div>
            <div class="um-field"><label for="um-phone">Số điện thoại</label><input id="um-phone" name="phone" type="tel" value="<c:out value='${ui.edit.phone}'/>" pattern="[0-9]{10}" maxlength="10" required placeholder="10 chữ số"></div>
            <c:if test="${ui.form eq 'add'}"><div class="um-field"><label for="um-password">Mật khẩu ban đầu</label><input id="um-password" name="password" type="password" minlength="8" maxlength="128" autocomplete="new-password" required><small>Tối thiểu 8 ký tự; mật khẩu được băm trước khi lưu.</small></div></c:if>
            <div class="um-field"><label for="um-role">Vai trò</label><select id="um-role" name="role"><option value="ADMIN" ${ui.edit.role eq 'ADMIN' ? 'selected' : ''}>Admin</option><option value="OPERATOR" ${ui.edit.role eq 'OPERATOR' ? 'selected' : ''}>Operator</option><option value="VIEWER" ${ui.edit.role eq 'VIEWER' ? 'selected' : ''}>Viewer</option></select></div>
        </div>
    </c:when><c:otherwise>
        <p class="um-dialog-description">Tài khoản: <strong><c:out value="${ui.target.fullName}"/></strong> (@<c:out value="${ui.target.userName}"/>).</p>
        <c:if test="${ui.form eq 'delete'}"><p class="um-dialog-description">Xóa mềm sẽ đặt is_active=false và thu hồi quyền thiết bị. Lịch sử điều khiển được giữ.</p></c:if>
        <c:if test="${ui.form eq 'password'}"><div class="um-field"><label for="um-password">Mật khẩu mới</label><input id="um-password" name="password" type="password" minlength="8" maxlength="128" autocomplete="new-password" required autofocus></div></c:if>
    </c:otherwise></c:choose>
    <div class="um-dialog-footer"><a class="um-button um-button-secondary" href="${pageContext.request.contextPath}/MainController?action=USER_MANAGER">Hủy</a><button class="um-button ${ui.form eq 'delete' ? 'um-button-danger' : 'um-button-primary'}" type="submit" name="uiOp" value="${ui.postOperation}">Xác Nhận</button></div>
</form></dialog></c:if></body></html>