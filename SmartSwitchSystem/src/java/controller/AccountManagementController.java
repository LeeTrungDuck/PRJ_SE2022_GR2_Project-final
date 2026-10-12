package controller;

import dao.UserDAO;
import dto.UserDTO;
import enums.Role;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import utills.PasswordUtil;

@WebServlet(name = "AccountManagementController", urlPatterns = { "/AccountManagementController" })
public class AccountManagementController extends ManagementController {
    private final String ERROR = "/web/AccountManager.jsp";
    private final String SUCCESS = "/MainController?action=ACCOUNT_MANAGER";

    protected String page() {
        return "ACCOUNT_MANAGER";
    }

    protected String errorView() {
        return ERROR;
    }

    protected String successUrl() {
        return SUCCESS;
    }

    protected boolean allowed(Role role) {
        return role == Role.ADMIN || role == Role.OPERATOR || role == Role.VIEWER;
    }

    protected Map<String, Object> view(UserDAO dao, UserDTO actor, HttpServletRequest r) {
        Map<String, Object> ui = views.base(actor, r);
        ui.put("edit", views.bound(r, views.profile(actor)));
        return ui;
    }

    protected String change(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException {
        String op = support.param(r, "uiOp");
        if ("saveProfile".equals(op)) {
            support.require(dao.updateProfile(actor.getUserId(), support.required(r, "fullName", 50, "họ tên"),
                    views.email(r), views.phone(r)), "Tài khoản không còn hoạt động hoặc không cập nhật được hồ sơ.");
            message(r, "Đã lưu thông tin cá nhân.");
        } else if ("changePassword".equals(op)) {
            support.password(r, "currentPassword", 1, false);
            support.password(r, "newPassword", 10, true);
            support.require(PasswordUtil.verify(r.getParameter("currentPassword"), actor.getPassword()),
                    "Mật khẩu hiện tại không đúng.");
            support.require(Objects.equals(r.getParameter("newPassword"), r.getParameter("confirmPassword")),
                    "Mật khẩu xác nhận không khớp.");
            support.require(!Objects.equals(r.getParameter("newPassword"), r.getParameter("currentPassword")),
                    "Mật khẩu mới phải khác mật khẩu hiện tại.");
            support.require(
                    dao.updatePassword(actor.getUserId(), actor.getPassword(),
                            PasswordUtil.hash(r.getParameter("newPassword"))),
                    "Mật khẩu đã thay đổi ở phiên khác. Vui lòng tải lại trang.");
            message(r, "Đã đổi mật khẩu truy cập.");
        } else
            throw new IllegalArgumentException("Thao tác hồ sơ không hợp lệ.");
        // User ID comes only from the authenticated session, never from submitted data.
        r.getSession().setAttribute("LOGIN_USER", dao.findById(actor.getUserId()));
        return "";
    }
}