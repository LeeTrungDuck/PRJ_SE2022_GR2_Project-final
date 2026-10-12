package controller;

import dao.UserDAO;
import dto.UserDTO;
import enums.Role;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import utills.PasswordUtil;
import static ui.UiSupport.*;

@WebServlet(name = "AccountManagementController", urlPatterns = {"/AccountManagementController"})
public class AccountManagementController extends ManagementController {

    protected String page() {
        return "ACCOUNT_MANAGER";
    }

    protected String jsp() {
        return "AccountManager";
    }

    protected boolean allowed(Role role) {
        return role == Role.ADMIN || role == Role.OPERATOR || role == Role.VIEWER;
    }

    protected Map<String, Object> view(UserDAO dao, UserDTO actor, HttpServletRequest r) {
        Map<String, Object> ui = ManagementViews.base(actor, r);
        ui.put("edit", ManagementViews.bound(r, ManagementViews.profile(actor)));
        return ui;
    }

    protected String change(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException {
        String op = param(r, "uiOp");
        if ("saveProfile".equals(op)) {
            require(dao.updateProfile(actor.getUserId(), required(r, "fullName", 50, "họ tên"), ManagementViews.email(r), ManagementViews.phone(r)), "Tài khoản không còn hoạt động hoặc không cập nhật được hồ sơ.");
            message(r, "Đã lưu thông tin cá nhân.");
        } else if ("changePassword".equals(op)) {
            password(r, "currentPassword", 1, false);
            password(r, "newPassword", 10, true);
            require(PasswordUtil.verify(r.getParameter("currentPassword"), actor.getPassword()), "Mật khẩu hiện tại không đúng.");
            require(Objects.equals(r.getParameter("newPassword"), r.getParameter("confirmPassword")), "Mật khẩu xác nhận không khớp.");
            require(!Objects.equals(r.getParameter("newPassword"), r.getParameter("currentPassword")), "Mật khẩu mới phải khác mật khẩu hiện tại.");
            require(dao.updatePassword(actor.getUserId(), actor.getPassword(), PasswordUtil.hash(r.getParameter("newPassword"))), "Mật khẩu đã thay đổi ở phiên khác. Vui lòng tải lại trang.");
            message(r, "Đã đổi mật khẩu truy cập.");
        } else {
            throw new IllegalArgumentException("Thao tác hồ sơ không hợp lệ.");
        }
        // User ID comes only from the authenticated session, never from submitted data.
        r.getSession().setAttribute("LOGIN_USER", dao.findById(actor.getUserId()));
        return "";
    }
}
