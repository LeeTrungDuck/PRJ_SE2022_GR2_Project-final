<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
    <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
        <c:set var="activePage" value="USER_MANAGER" scope="request" />
        <!DOCTYPE html>
        <html lang="vi">

        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Quản lý tài khoản | ESP32 IoT Hub</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
            <link
                href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap"
                rel="stylesheet">
            <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
            <script src="${pageContext.request.contextPath}/js/userManager.js" defer></script>
        </head>

        <body class="user-manager-page">
            <%@ include file="sideBar.jspf" %>

                <header class="um-topbar">
                    <span class="um-section-label">QUẢN TRỊ HỆ THỐNG</span>
                    <div class="um-topbar-user">
                        <span class="um-admin-tag"><i class="fa-solid fa-shield-halved" aria-hidden="true"></i>
                            Admin</span>
                        <span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user"
                                aria-hidden="true"></i></span>
                    </div>
                </header>

                <main class="um-main" id="main-content">
                    <section class="um-page-heading" aria-labelledby="um-title">
                        <p class="um-eyebrow">IDENTITY &amp; ACCESS CONTROL MATRIX</p>
                        <h1 id="um-title">Quản Lý Tài Khoản Người Dùng <span>(User Management)</span></h1>
                        <div class="um-heading-bottom">
                            <p>Quản trị danh sách tài khoản, phân cấp vai trò (Admin, Operator, Viewer) và kiểm soát
                                trạng thái hoạt động trên mạng lưới điều khiển vi mạch IoT.</p>
                            <button class="um-button um-button-primary" id="um-add-user" type="button">
                                <i class="fa-solid fa-user-plus" aria-hidden="true"></i> Thêm Người Dùng Mới
                            </button>
                        </div>
                    </section>

                    <section class="um-table-panel" aria-label="Danh sách tài khoản người dùng">
                        <div class="um-table-scroll" tabindex="0" role="region"
                            aria-label="Bảng tài khoản, có thể cuộn ngang">
                            <table class="um-table">
                                <caption class="um-sr-only">Tài khoản minh họa và các thao tác quản lý trong phiên xem
                                    UI</caption>
                                <thead>
                                    <tr>
                                        <th scope="col">NGƯỜI DÙNG</th>
                                        <th scope="col">VAI TRÒ (ROLE)</th>
                                        <th scope="col">TRẠNG THÁI</th>
                                        <th scope="col">NGÀY TẠO</th>
                                        <th scope="col" class="um-actions-heading">THAO TÁC HỆ THỐNG</th>
                                    </tr>
                                </thead>
                                <tbody id="um-user-list"></tbody>
                            </table>
                            <div class="um-empty" id="um-empty" hidden>
                                <i class="fa-solid fa-users" aria-hidden="true"></i>
                                <h2>Chưa có tài khoản nào</h2>
                                <p>Chọn “Thêm Người Dùng Mới” để tạo tài khoản đầu tiên.</p>
                            </div>
                            <noscript>
                                <p class="um-noscript">Bật JavaScript để xem danh sách và dùng các thao tác quản lý tài
                                    khoản trong bản xem UI.</p>
                            </noscript>
                        </div>
                        <footer class="um-table-footer">
                            <p><i class="fa-solid fa-shield-halved" aria-hidden="true"></i> Dữ liệu minh họa. Thay đổi
                                chỉ áp dụng trong phiên xem UI.</p>
                            <nav class="um-pagination" aria-label="Phân trang danh sách tài khoản">
                                <button id="um-previous" type="button" disabled>Trang Trước</button>
                                <span id="um-page-number" aria-live="polite">1 / 1</span>
                                <button id="um-next" type="button" disabled>Trang Kế</button>
                            </nav>
                        </footer>
                    </section>
                    <p class="um-result-count" id="um-result-count" aria-live="polite"></p>
                </main>

                <dialog class="um-dialog" id="um-user-dialog" aria-labelledby="um-dialog-title"
                    aria-describedby="um-dialog-description">
                    <div class="um-dialog-heading">
                        <div>
                            <p class="um-eyebrow">USER MANAGEMENT</p>
                            <h2 id="um-dialog-title">Thêm người dùng mới</h2>
                        </div>
                        <button type="button" class="um-icon-button" data-close-dialog aria-label="Đóng hộp thoại"><i
                                class="fa-solid fa-xmark" aria-hidden="true"></i></button>
                    </div>
                    <p id="um-dialog-description" class="um-dialog-description">Tạo tài khoản và chọn vai trò truy cập
                        hệ thống trong bản xem UI.</p>
                    <form id="um-user-form">
                        <div class="um-form-grid">
                            <div class="um-field"><label for="um-full-name">Họ và tên <span>*</span></label><input
                                    id="um-full-name" name="fullName" type="text" maxlength="100" autocomplete="off"
                                    required placeholder="Nguyễn Văn An"></div>
                            <div class="um-field"><label for="um-username">Tên đăng nhập <span>*</span></label><input
                                    id="um-username" name="userName" type="text" minlength="3" maxlength="40"
                                    pattern="[A-Za-z0-9._\-]{3,40}" autocomplete="off" required placeholder="an.nguyen"
                                    aria-describedby="um-username-help"><small id="um-username-help">3–40 ký tự: chữ
                                    không dấu, số, dấu . _ hoặc -</small></div>
                            <div class="um-field um-field-full"><label for="um-email">Email <span>*</span></label><input
                                    id="um-email" name="email" type="email" maxlength="150" autocomplete="off" required
                                    placeholder="an.nguyen@example.com"></div>
                            <div class="um-field um-field-full">
                                <label for="um-initial-password">Mật khẩu ban đầu <span>*</span></label>
                                <div class="um-password-wrap"><input id="um-initial-password" name="password"
                                        type="password" minlength="8" maxlength="128" autocomplete="new-password"
                                        required aria-describedby="um-initial-password-help"><button type="button"
                                        class="um-icon-button" id="um-initial-password-toggle"
                                        aria-label="Hiện mật khẩu ban đầu" aria-pressed="false"><i
                                            class="fa-regular fa-eye" aria-hidden="true"></i></button></div>
                                <small id="um-initial-password-help">Tối thiểu 8 ký tự. Mật khẩu không được lưu trong
                                    bản xem UI.</small>
                            </div>
                        </div>
                        <fieldset class="um-role-fieldset">
                            <legend>Vai trò truy cập</legend>
                            <div class="um-role-options">
                                <label><input type="radio" name="role" value="ADMIN"><span><i
                                            class="fa-solid fa-shield-halved" aria-hidden="true"></i> Admin<small>Quản
                                            trị hệ thống</small></span></label>
                                <label><input type="radio" name="role" value="OPERATION"><span><i
                                            class="fa-solid fa-sliders" aria-hidden="true"></i> Operator<small>Điều
                                            khiển thiết bị</small></span></label>
                                <label><input type="radio" name="role" value="VIEWER" checked><span><i
                                            class="fa-regular fa-eye" aria-hidden="true"></i> Viewer<small>Xem và giám
                                            sát</small></span></label>
                            </div>
                        </fieldset>
                        <p class="um-form-error" id="um-form-error" role="alert" hidden></p>
                        <div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary"
                                data-close-dialog>Hủy bỏ</button><button type="submit"
                                class="um-button um-button-primary"><i class="fa-solid fa-user-plus"
                                    aria-hidden="true"></i> Tạo tài khoản</button></div>
                    </form>
                </dialog>

                <dialog class="um-dialog um-dialog-small" id="um-action-dialog" aria-labelledby="um-action-title"
                    aria-describedby="um-action-description">
                    <div class="um-dialog-heading">
                        <h2 id="um-action-title"></h2><button type="button" class="um-icon-button" data-close-dialog
                            aria-label="Đóng hộp thoại"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
                    </div>
                    <p class="um-dialog-description" id="um-action-description"></p>
                    <form id="um-action-form">
                        <div class="um-field" id="um-password-field" hidden>
                            <label for="um-password">Mật khẩu mới <span>*</span></label>
                            <div class="um-password-wrap"><input id="um-password" type="password" minlength="8"
                                    maxlength="128" autocomplete="new-password"
                                    aria-describedby="um-password-help"><button type="button" id="um-password-toggle"
                                    class="um-icon-button" aria-label="Hiện mật khẩu" aria-pressed="false"><i
                                        class="fa-regular fa-eye" aria-hidden="true"></i></button></div>
                            <small id="um-password-help">Tối thiểu 8 ký tự. Mật khẩu không được lưu trong bản xem
                                UI.</small>
                        </div>
                        <div class="um-dialog-footer"><button type="button" class="um-button um-button-secondary"
                                data-close-dialog>Hủy bỏ</button><button type="submit" id="um-action-confirm"
                                class="um-button um-button-primary">Xác nhận</button></div>
                    </form>
                </dialog>
                <div class="um-toast" id="um-toast" role="status" aria-live="polite" aria-atomic="true" hidden><i
                        class="fa-solid fa-circle-check" aria-hidden="true"></i><span
                        id="um-toast-message"></span><button class="um-icon-button" type="button" id="um-toast-close"
                        aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
        </body>

        </html>