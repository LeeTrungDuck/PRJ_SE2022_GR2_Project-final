package controller;

import dao.UserDAO;
import dto.UserDTO;
import enums.Role;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import static ui.UiSupport.*;

/**
 * Shared authentication, CSRF and POST/redirect/GET for the three management
 * pages.
 */
abstract class ManagementController extends HttpServlet {

    protected abstract String page();

    protected abstract String jsp();

    protected abstract boolean allowed(Role role);

    protected abstract Map<String, Object> view(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException, ClassNotFoundException;

    protected abstract String change(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException, ClassNotFoundException;

    protected UserDAO users() throws SQLException, ClassNotFoundException {
        return new UserDAO();
    }

    protected void message(HttpServletRequest r, String value) {
        r.getSession().setAttribute("MANAGEMENT_FLASH", value);
    }

    protected String token(HttpServletRequest r) {
        HttpSession session = r.getSession();
        synchronized (session) {
            if (session.getAttribute("MANAGEMENT_CSRF") == null) {
                session.setAttribute("MANAGEMENT_CSRF", UUID.randomUUID().toString());
            }
            return (String) session.getAttribute("MANAGEMENT_CSRF");
        }
    }

    @Override
    protected void doGet(HttpServletRequest r, HttpServletResponse s) throws ServletException, IOException {
        process(r, s);
    }

    @Override
    protected void doPost(HttpServletRequest r, HttpServletResponse s) throws ServletException, IOException {
        process(r, s);
    }

    private void process(HttpServletRequest r, HttpServletResponse s) throws ServletException, IOException {
        r.setCharacterEncoding("UTF-8");
        s.setContentType("text/html;charset=UTF-8");
        s.setHeader("Cache-Control", "no-store");
        HttpSession session = r.getSession(false);
        UserDTO login = session == null ? null : (UserDTO) session.getAttribute("LOGIN_USER");
        if (login == null) {
            s.sendRedirect(r.getContextPath() + "/web/login.jsp");
            return;
        }
        try ( UserDAO dao = users()) {
            UserDTO actor = dao.findById(login.getUserId());
            if (actor == null || !actor.isActive()) {
                session.invalidate();
                s.sendRedirect(r.getContextPath() + "/web/login.jsp");
                return;
            }
            session.setAttribute("LOGIN_USER", actor);
            if (!allowed(actor.getRole())) {
                s.sendError(403, "Bạn không có quyền sử dụng chức năng này.");
                return;
            }
            String op = input(r, "uiOp", 100);
            if (!op.isEmpty() && !"POST".equals(r.getMethod())) {
                s.sendError(405, "Thay đổi dữ liệu phải dùng POST.");
                return;
            }
            if ("POST".equals(r.getMethod()) && !op.isEmpty()) {
                if (!token(r).equals(r.getParameter("csrf"))) {
                    s.sendError(403, "Form không hợp lệ. Vui lòng tải lại trang.");
                    return;
                }
                try {
                    String suffix = change(dao, actor, r);
                    s.setStatus(303);
                    s.setHeader("Location", s.encodeRedirectURL(r.getContextPath() + "/MainController?action=" + page() + suffix));
                    return;
                } catch (IllegalArgumentException e) {
                    r.setAttribute("uiError", e.getMessage());
                } catch (SQLException e) {
                    log("Management write failed", e);
                    r.setAttribute("uiError", e.getSQLState() != null && e.getSQLState().startsWith("23") ? "Mã, tên đăng nhập, email hoặc số điện thoại đã tồn tại; hoặc dữ liệu tham chiếu không hợp lệ." : "Không lưu được dữ liệu. Vui lòng thử lại.");
                }
            }
            Object flash = session.getAttribute("MANAGEMENT_FLASH");
            if (flash != null) {
                r.setAttribute("uiMessage", flash);
                session.removeAttribute("MANAGEMENT_FLASH");
            }
            Map<String, Object> model;
            try {
                model = view(dao, actor, r);
            } catch (IllegalArgumentException e) {
                r.setAttribute("uiError", e.getMessage());
                model = view(dao, actor, new HttpServletRequestWrapper(r) {
                    @Override
                    public String getParameter(String key) {
                        return null;
                    }

                    @Override
                    public String getMethod() {
                        return "GET";
                    }
                });
            }
            model.put("csrf", token(r));
            r.setAttribute("ui", model);
            r.setAttribute("activePage", page());
            r.getRequestDispatcher("/web/" + jsp() + ".jsp").forward(r, s);
        } catch (SQLException | ClassNotFoundException e) {
            log("Management database access failed", e);
            s.setStatus(503);
            r.setAttribute("ERROR", "Không kết nối được cơ sở dữ liệu. Vui lòng thử lại.");
            r.getRequestDispatcher("/web/errorPage.jsp").forward(r, s);
        }
    }
}
