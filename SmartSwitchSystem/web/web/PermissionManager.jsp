<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="activePage" value="PERMISSION_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Quản lý phân quyền | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <%-- Reuse the existing dashboard shell, color tokens, sidebar and buttons. --%>
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/permissionManagerCss.css">
        <script src="${pageContext.request.contextPath}/js/permissionManager.js" defer></script>
    </head>
    <body class="user-manager-page permission-manager-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">QUẢN TRỊ PHÂN QUYỀN</span>
            <div class="um-topbar-user">
                <span class="pm-topbar-role">Vai trò: <span class="um-admin-tag">Admin</span></span>
                <span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user" aria-hidden="true"></i></span>
            </div>
        </header>
        <main class="um-main pm-main" id="main-content">
            <section class="pm-page-heading" aria-labelledby="pm-title">
                <h1 id="pm-title">Quản Lý Phân Quyền Thiết Bị <span>(Permission Management)</span></h1>
                <p>Thiết lập quyền Xem (<code class="pm-view-text">canView</code>) và Điều Khiển (<code class="pm-control-text">canControl</code>) cho từng tài khoản Viewer trên từng Switch cụ thể.</p>
            </section>

            <div class="pm-workspace">
                <section class="pm-viewers-panel" aria-labelledby="pm-viewers-title">
                    <header class="pm-viewers-heading">
                        <h2 id="pm-viewers-title"><i class="fa-solid fa-id-card-clip" aria-hidden="true"></i> Tài Khoản Viewer</h2>
                        <span class="pm-account-count" id="pm-account-count">4 TÀI KHOẢN</span>
                    </header>
                    <label class="um-sr-only" for="pm-search">Tìm Viewer theo tên đăng nhập hoặc họ tên</label>
                    <div class="pm-search-wrap">
                        <i class="fa-solid fa-magnifying-glass" aria-hidden="true"></i>
                        <input id="pm-search" type="search" placeholder="Tìm tên đăng nhập, họ tên..." autocomplete="off" aria-controls="pm-viewer-list">
                    </div>
                    <div class="pm-viewer-list" id="pm-viewer-list" aria-label="Chọn tài khoản Viewer"></div>
                    <div class="pm-search-empty" id="pm-search-empty" hidden>
                        <i class="fa-solid fa-user-slash" aria-hidden="true"></i>
                        <p>Không tìm thấy tài khoản Viewer.</p>
                        <button type="button" class="um-button um-button-secondary" id="pm-clear-search">Xóa tìm kiếm</button>
                    </div>
                    <p class="um-sr-only" id="pm-search-result" role="status"></p>
                </section>

                <section class="pm-permissions" aria-label="Cấu hình quyền trên từng switch">
                    <header class="pm-target-panel">
                        <div class="pm-target-identity">
                            <span class="pm-target-icon"><i class="fa-solid fa-sliders" aria-hidden="true"></i></span>
                            <div>
                                <p class="pm-target-label">ĐANG CẤU HÌNH QUYỀN CHO:</p>
                                <span class="pm-target-handle" id="pm-target-handle"></span>
                                <h2 id="pm-target-name"></h2>
                                <p class="pm-target-description" id="pm-target-description"></p>
                            </div>
                        </div>
                        <div class="pm-toolbar">
                            <div class="pm-bulk-actions">
                                <button type="button" class="um-button um-button-secondary" id="pm-grant-view"><i class="fa-regular fa-eye" aria-hidden="true"></i> Cấp Toàn Quyền Xem</button>
                                <button type="button" class="um-button um-button-secondary" id="pm-revoke-all"><i class="fa-solid fa-ban" aria-hidden="true"></i> Hủy Toàn Bộ Quyền</button>
                            </div>
                            <div class="pm-save-actions">
                                <span class="pm-save-state" id="pm-save-state" role="status">Chưa có thay đổi</span>
                                <button type="button" class="pm-reset-button" id="pm-reset" disabled>Hoàn tác</button>
                                <button type="button" class="um-button um-button-primary" id="pm-save" disabled><i class="fa-solid fa-floppy-disk" aria-hidden="true"></i> Lưu Phân Quyền</button>
                            </div>
                        </div>
                    </header>

                    <p class="pm-rule-note"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Quyền điều khiển luôn bao gồm quyền xem. Bỏ quyền xem sẽ bỏ cả quyền điều khiển.</p>
                    <div class="pm-device-groups" id="pm-device-groups"></div>
                    <footer class="pm-workspace-footer">
                        <span id="pm-permission-summary" aria-live="polite"></span>
                        <p><i class="fa-solid fa-shield-halved" aria-hidden="true"></i> Dữ liệu minh họa. Lưu chỉ áp dụng trong phiên xem UI; tải lại trang sẽ khôi phục dữ liệu ban đầu.</p>
                    </footer>
                </section>
            </div>
            <noscript><p class="pm-noscript">Bật JavaScript để chọn Viewer và cấu hình quyền trong bản xem UI.</p></noscript>
        </main>

        <dialog class="um-dialog um-dialog-small" id="pm-revoke-dialog" aria-labelledby="pm-revoke-title" aria-describedby="pm-revoke-description">
            <div class="um-dialog-heading">
                <h2 id="pm-revoke-title">Hủy toàn bộ quyền?</h2>
                <button class="um-icon-button" type="button" id="pm-close-revoke" aria-label="Đóng hộp thoại"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
            </div>
            <p class="um-dialog-description" id="pm-revoke-description"></p>
            <form id="pm-revoke-form">
                <div class="um-dialog-footer">
                    <button type="button" class="um-button um-button-secondary" id="pm-cancel-revoke">Giữ nguyên quyền</button>
                    <button type="submit" class="um-button um-button-danger">Hủy toàn bộ quyền</button>
                </div>
            </form>
        </dialog>
        <div class="um-toast" id="pm-toast" role="status" aria-live="polite" aria-atomic="true" hidden>
            <i class="fa-solid fa-circle-check" aria-hidden="true"></i><span id="pm-toast-message"></span>
            <button class="um-icon-button" type="button" id="pm-toast-close" aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
        </div>
    </body>
</html>
