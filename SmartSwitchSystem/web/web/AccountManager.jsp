<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="activePage" value="ACCOUNT_MANAGER" scope="request" />
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Hồ sơ tài khoản | ESP32 IoT Hub</title>
        <link rel="preconnect" href="https://fonts.googleapis.com">
        <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
        <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/userManagerCss.css">
        <link rel="stylesheet" href="${pageContext.request.contextPath}/css/accountManagerCss.css">
        <script src="${pageContext.request.contextPath}/js/accountManager.js" defer></script>
    </head>
    <body class="user-manager-page account-manager-page">
        <%@ include file="sideBar.jspf" %>
        <header class="um-topbar">
            <span class="um-section-label">HỒ SƠ TÀI KHOẢN</span>
            <div class="um-topbar-user">
                <span class="ac-topbar-role"><span aria-hidden="true"></span> Vai trò: <strong>Admin</strong></span>
                <span class="um-profile-icon" aria-label="Giao diện quản trị viên"><i class="fa-regular fa-user" aria-hidden="true"></i></span>
            </div>
        </header>
        <main class="um-main ac-main" id="main-content">
            <header class="ac-page-heading">
                <span class="ac-heading-dot" aria-hidden="true"></span>
                <h1 id="ac-title">Hồ Sơ &amp; Cài Đặt Tài Khoản <span>(Profile / Account)</span></h1>
                <p>Quản lý thông tin định danh cá nhân, phân quyền truy cập và bảo mật phiên điều khiển ESP32.</p>
            </header>

            <div class="ac-workspace">
                <aside class="ac-profile-card" aria-labelledby="ac-profile-name">
                    <div class="ac-profile-identity">
                        <span class="ac-avatar"><i class="fa-regular fa-circle-user" aria-hidden="true"></i><span class="ac-online-dot" aria-hidden="true"></span></span>
                        <div class="ac-profile-text">
                            <h2><span id="ac-profile-name">Nguyễn Văn An</span> <i class="fa-solid fa-circle-check" aria-label="Tài khoản minh họa đã xác minh"></i></h2>
                            <p class="ac-handle">@admin_sys</p>
                            <span class="ac-role-badge">ADMINISTRATOR (TOÀN QUYỀN)</span>
                        </div>
                    </div>
                    <dl class="ac-profile-details">
                        <div><dt>User ID</dt><dd><span class="ac-id-badge">UID-8829</span></dd></div>
                        <div><dt>Trạng Thái</dt><dd class="ac-status"><span aria-hidden="true"></span> Hoạt Động</dd></div>
                        <div><dt>Ngày Tạo</dt><dd>12/10/2023</dd></div>
                        <div><dt>Phân Quyền</dt><dd class="ac-root-role">SuperAdmin / Root</dd></div>
                    </dl>
                </aside>

                <div class="ac-forms">
                    <section class="ac-panel" aria-labelledby="ac-personal-title">
                        <header class="ac-panel-heading">
                            <i class="fa-solid fa-id-card-clip" aria-hidden="true"></i>
                            <div><h2 id="ac-personal-title">Chỉnh Sửa Thông Tin Cá Nhân</h2><p>Cập nhật hồ sơ định danh và phương thức liên hệ khi có cảnh báo máy chủ.</p></div>
                            <span class="ac-form-number">FORM 01/02</span>
                        </header>
                        <form id="ac-profile-form" method="post" novalidate>
                            <div class="ac-personal-grid">
                                <div class="ac-field">
                                    <label for="ac-fullname">Họ và Tên</label>
                                    <div class="ac-input-wrap"><i class="fa-regular fa-user" aria-hidden="true"></i><input id="ac-fullname" name="fullName" type="text" value="Nguyễn Văn An" autocomplete="name" maxlength="100" required></div>
                                </div>
                                <div class="ac-field">
                                    <label for="ac-username">Tên Đăng Nhập (Cố định)</label>
                                    <div class="ac-input-wrap ac-readonly"><i class="fa-solid fa-at" aria-hidden="true"></i><input id="ac-username" type="text" value="admin_sys" autocomplete="username" readonly aria-describedby="ac-username-hint"></div>
                                    <span id="ac-username-hint" class="um-sr-only">Tên đăng nhập cố định, không thể chỉnh sửa.</span>
                                </div>
                                <div class="ac-field">
                                    <label for="ac-email">Email Nhận Thông Báo Khẩn</label>
                                    <div class="ac-input-wrap"><i class="fa-regular fa-envelope" aria-hidden="true"></i><input id="ac-email" name="email" type="email" value="nguyenvanan.iot@datacenter.vn" autocomplete="email" maxlength="254" required aria-describedby="ac-email-note"></div>
                                </div>
                                <div class="ac-field">
                                    <label for="ac-phone">Số Điện Thoại Hỗ Trợ Sự Cố (24/7)</label>
                                    <div class="ac-input-wrap"><i class="fa-solid fa-phone" aria-hidden="true"></i><input id="ac-phone" name="phone" type="tel" value="(+84) 982.019.233" autocomplete="tel" maxlength="30" required></div>
                                </div>
                            </div>
                            <p class="ac-form-error" id="ac-profile-error" role="alert" hidden></p>
                            <footer class="ac-panel-footer">
                                <p id="ac-email-note"><i class="fa-solid fa-circle-info" aria-hidden="true"></i> Thay đổi email sẽ yêu cầu nhập mã OTP minh họa.</p>
                                <button type="submit" id="ac-save-profile" class="um-button um-button-primary" disabled><i class="fa-solid fa-floppy-disk" aria-hidden="true"></i> Lưu Thay Đổi</button>
                            </footer>
                        </form>
                    </section>

                    <section class="ac-panel" aria-labelledby="ac-password-title">
                        <header class="ac-panel-heading">
                            <i class="fa-solid fa-clock-rotate-left" aria-hidden="true"></i>
                            <div><h2 id="ac-password-title">Đổi Mật Khẩu Truy Cập</h2><p id="ac-password-requirements">Tối thiểu 10 ký tự, có chữ hoa, chữ số và ký tự đặc biệt.</p></div>
                            <span class="ac-form-number">FORM 02/02</span>
                        </header>
                        <form id="ac-password-form" method="post" novalidate>
                            <div class="ac-password-grid">
                                <div class="ac-field">
                                    <label for="ac-current-password">Mật Khẩu Hiện Tại</label>
                                    <div class="ac-input-wrap ac-password-wrap">
                                        <i class="fa-solid fa-key" aria-hidden="true"></i>
                                        <input id="ac-current-password" type="password" autocomplete="current-password" placeholder="Nhập mật khẩu hiện tại" required aria-describedby="ac-password-demo-note">
                                        <button type="button" class="ac-password-toggle" data-password-target="ac-current-password" aria-label="Hiện mật khẩu hiện tại" aria-pressed="false"><i class="fa-regular fa-eye" aria-hidden="true"></i></button>
                                    </div>
                                </div>
                                <div class="ac-field">
                                    <label for="ac-new-password">Mật Khẩu Mới</label>
                                    <div class="ac-input-wrap ac-password-wrap">
                                        <i class="fa-solid fa-lock" aria-hidden="true"></i>
                                        <input id="ac-new-password" type="password" autocomplete="new-password" placeholder="Tối thiểu 10 ký tự" minlength="10" required aria-describedby="ac-password-requirements ac-strength-text">
                                        <button type="button" class="ac-password-toggle" data-password-target="ac-new-password" aria-label="Hiện mật khẩu mới" aria-pressed="false"><i class="fa-regular fa-eye" aria-hidden="true"></i></button>
                                    </div>
                                </div>
                                <div class="ac-field">
                                    <label for="ac-confirm-password">Nhập Lại Mật Khẩu</label>
                                    <div class="ac-input-wrap ac-password-wrap">
                                        <i class="fa-solid fa-check" aria-hidden="true"></i>
                                        <input id="ac-confirm-password" type="password" autocomplete="new-password" placeholder="Xác nhận lại..." required aria-describedby="ac-password-error">
                                        <button type="button" class="ac-password-toggle" data-password-target="ac-confirm-password" aria-label="Hiện xác nhận mật khẩu" aria-pressed="false"><i class="fa-regular fa-eye" aria-hidden="true"></i></button>
                                    </div>
                                </div>
                            </div>
                            <p class="ac-form-error" id="ac-password-error" role="alert" hidden></p>
                            <footer class="ac-panel-footer ac-password-footer">
                                <div class="ac-strength" id="ac-strength" data-score="0">
                                    <span class="ac-strength-label">ĐỘ MẠNH:</span>
                                    <div class="ac-strength-bars" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
                                    <span id="ac-strength-text" role="status">Chưa nhập</span>
                                </div>
                                <button type="submit" id="ac-update-password" class="um-button ac-password-submit" disabled><i class="fa-solid fa-key" aria-hidden="true"></i> Cập Nhật Mật Khẩu</button>
                            </footer>
                            <p class="ac-password-demo-note" id="ac-password-demo-note">Chức năng minh họa; mật khẩu không được xác thực hoặc lưu.</p>
                        </form>
                    </section>
                </div>
            </div>
            <p class="ac-preview-note"><i class="fa-solid fa-shield-halved" aria-hidden="true"></i> Dữ liệu minh họa. Thay đổi hồ sơ chỉ áp dụng trong phiên xem UI; tải lại trang sẽ khôi phục dữ liệu ban đầu.</p>
            <noscript><p class="ac-noscript">Bật JavaScript để thử chỉnh sửa hồ sơ và đổi mật khẩu trong bản xem UI.</p></noscript>
        </main>

        <dialog class="um-dialog um-dialog-small" id="ac-otp-dialog" aria-labelledby="ac-otp-title" aria-describedby="ac-otp-description">
            <div class="um-dialog-heading">
                <h2 id="ac-otp-title">Xác Nhận Thay Đổi Email</h2>
                <button type="button" class="um-icon-button" id="ac-close-otp" aria-label="Đóng hộp xác nhận"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button>
            </div>
            <p class="um-dialog-description" id="ac-otp-description">Xác nhận địa chỉ email mới: <strong id="ac-pending-email"></strong></p>
            <p class="ac-otp-demo">Mã xác nhận minh họa: <strong>246810</strong>. Không có email được gửi.</p>
            <form id="ac-otp-form" method="post" novalidate>
                <div class="um-field"><label for="ac-otp-code">Mã OTP (6 chữ số)</label><input id="ac-otp-code" type="text" inputmode="numeric" autocomplete="off" pattern="[0-9]{6}" maxlength="6" placeholder="Nhập mã minh họa" required aria-describedby="ac-otp-error"></div>
                <p class="ac-form-error" id="ac-otp-error" role="alert" hidden></p>
                <div class="um-dialog-footer">
                    <button type="button" class="um-button um-button-secondary" id="ac-cancel-otp">Hủy</button>
                    <button type="submit" class="um-button um-button-primary">Xác Nhận &amp; Lưu</button>
                </div>
            </form>
        </dialog>
        <div class="um-toast" id="ac-toast" hidden><span id="ac-toast-message" role="status"></span><button type="button" class="um-icon-button" id="ac-dismiss-toast" aria-label="Đóng thông báo"><i class="fa-solid fa-xmark" aria-hidden="true"></i></button></div>
    </body>
</html>
