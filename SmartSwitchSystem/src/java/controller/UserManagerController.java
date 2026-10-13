package controller;

import dao.UserDAO;
import dto.UserDTO;
import enums.Role;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import utills.PasswordUtil;


@WebServlet(name="UserManagerController", urlPatterns={"/UserManagerController"})
public class UserManagerController extends ManagementController {
    private final String ERROR = "/web/UserManager.jsp";
    private final String SUCCESS = "/MainController?action=USER_MANAGER";

    protected String page() { return "USER_MANAGER"; }
    protected String errorView() { return ERROR; }
    protected String successUrl() { return SUCCESS; }
    protected boolean allowed(Role role) { return role == Role.ADMIN; }
    protected Map<String,Object> view(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException { return views.users(dao.findAll(),actor,r); }
    protected String change(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException {
        String op = support.param(r,"uiOp");
        if ("addUser".equals(op)) {
            String username = support.required(r,"userName",50,"tên đăng nhập");
            support.require(username.matches("[A-Za-z0-9_.-]{3,50}"),"Tên đăng nhập cần 3–50 ký tự chữ, số, chấm, gạch ngang hoặc gạch dưới.");
            support.require(dao.findByUserName(username)==null,"Tên đăng nhập đã tồn tại.");
            support.password(r,"password",8,false);
            UserDTO user = new UserDTO("U"+UUID.randomUUID().toString().replace("-","").substring(0,19),username,PasswordUtil.hash(r.getParameter("password")),support.required(r,"fullName",50,"họ tên"),role(r),true,views.email(r),views.phone(r));
            support.require(dao.insert(user),"Không thêm được người dùng.");
            message(r,"Đã thêm người dùng vào cơ sở dữ liệu."); return "&p="+Math.max(1,(dao.findAll().size()+5)/6);
        }
        UserDTO user = dao.findById(support.required(r,"id",20,"User ID"));
        support.require(user!=null,"Không tìm thấy tài khoản.");
        support.require(!actor.getUserId().equals(user.getUserId()),"Không được thay đổi quyền, khóa hoặc xóa chính tài khoản đang đăng nhập. Sửa hồ sơ tại Thông Tin Tài Khoản.");
        boolean ok;
        if ("deleteUser".equals(op)) ok = dao.delete(user.getUserId());
        else {
            if ("changeRole".equals(op)) { user.setRole(role(r));ok=dao.updateRole(user); }
            else if ("toggleUser".equals(op)) { user.setActive(!user.isActive());ok=dao.updateActive(user); }
            else if ("resetPassword".equals(op)) { support.password(r,"password",8,false);ok=dao.resetPassword(user.getUserId(),PasswordUtil.hash(r.getParameter("password"))); }
            else if ("updateUser".equals(op)) { user.setFullName(support.required(r,"fullName",50,"họ tên")); user.setEmail(views.email(r)); user.setPhone(views.phone(r)); user.setRole(role(r));ok=dao.updateDetails(user); }
            else throw new IllegalArgumentException("Thao tác người dùng không hợp lệ.");
        }
        support.require(ok,"Không cập nhật được tài khoản.");
        message(r,"deleteUser".equals(op)?"Đã xóa mềm tài khoản (is_active=false) và thu hồi quyền thiết bị.":"Đã cập nhật tài khoản."); return "";
    }
    private Role role(HttpServletRequest r) { return Role.valueOf(support.choice(r,"role","ADMIN","OPERATOR","VIEWER")); }
}
