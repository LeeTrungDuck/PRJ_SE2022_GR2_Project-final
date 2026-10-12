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


/** Request-local MVC2 flow shared by the three management servlets. */
abstract class ManagementController extends HttpServlet {
    private final String ERROR = "/web/errorPage.jsp";
    private final String LOGIN = "/web/login.jsp";
    protected final ManagementSupport support = new ManagementSupport();
    protected final ManagementViews views = new ManagementViews();

    protected abstract String page();
    protected abstract String errorView();
    protected abstract String successUrl();
    protected abstract boolean allowed(Role role);
    protected abstract Map<String,Object> view(UserDAO dao, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException;
    protected abstract String change(UserDAO dao, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException;

    protected UserDAO users() throws SQLException, ClassNotFoundException {
        return new UserDAO();
    }

    protected void message(HttpServletRequest request, String text) {
        request.getSession().setAttribute("MANAGEMENT_FLASH", text);
    }

    protected String token(HttpServletRequest request) {
        HttpSession session = request.getSession();
        synchronized (session) {
            if (session.getAttribute("MANAGEMENT_CSRF") == null) {
                session.setAttribute("MANAGEMENT_CSRF", UUID.randomUUID().toString());
            }
            return (String) session.getAttribute("MANAGEMENT_CSRF");
        }
    }

    private void error(HttpServletRequest request, String text) {
        request.setAttribute("ERROR", text);
        request.setAttribute("uiError", text);
    }

    private void rememberInput(HttpServletRequest request) {
        // Passwords are deliberately excluded from retained form values.
        for (String field : new String[]{"fullName", "userName", "email", "phone", "role"}) {
            String value = request.getParameter(field);
            if (value != null) request.setAttribute("OLD_" + field, value.trim());
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException { processRequest(request, response); }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException { processRequest(request, response); }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String url = ERROR;
        String message = null;
        boolean redirect = false;
        try {
            request.setCharacterEncoding("UTF-8");
            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            rememberInput(request);
            HttpSession session = request.getSession(false);
            UserDTO login = session == null ? null : (UserDTO) session.getAttribute("LOGIN_USER");
            if (login == null) {
                url = LOGIN;
                message = "Vui lòng đăng nhập để sử dụng chức năng này.";
            } else {
                try (UserDAO dao = users()) {
                    UserDTO actor = dao.findById(login.getUserId());
                    if (actor == null || !actor.isActive()) {
                        session.invalidate();
                        url = LOGIN;
                        message = "Tài khoản không còn hoạt động. Vui lòng đăng nhập lại.";
                    } else if (!allowed(actor.getRole())) {
                        response.setStatus(403);
                        message = "Bạn không có quyền sử dụng chức năng này.";
                    } else {
                        session.setAttribute("LOGIN_USER", actor);
                        String operation = support.input(request, "uiOp", 100);
                        if (!operation.isEmpty() && !"POST".equals(request.getMethod())) {
                            response.setStatus(405);
                            message = "Thay đổi dữ liệu phải được gửi bằng POST.";
                        } else if ("POST".equals(request.getMethod()) && !operation.isEmpty()
                                && !token(request).equals(request.getParameter("csrf"))) {
                            response.setStatus(403);
                            message = "Form không hợp lệ. Vui lòng tải lại trang và thử lại.";
                        }
                        if (message == null) {
                            if ("POST".equals(request.getMethod()) && !operation.isEmpty()) {
                                try {
                                    String suffix = change(dao, actor, request);
                                    url = request.getContextPath() + successUrl() + suffix;
                                    redirect = true;
                                } catch (IllegalArgumentException e) {
                                    log("Management input rejected", e);
                                    error(request, e.getMessage());
                                } catch (SQLException e) {
                                    log("Management write failed", e);
                                    error(request, sqlMessage(e));
                                }
                            }
                            if (!redirect) {
                                Object flash = session.getAttribute("MANAGEMENT_FLASH");
                                if (flash != null) {
                                    request.setAttribute("uiMessage", flash);
                                    session.removeAttribute("MANAGEMENT_FLASH");
                                }
                                Map<String,Object> model;
                                try {
                                    model = view(dao, actor, request);
                                } catch (IllegalArgumentException e) {
                                    log("Management view input rejected", e);
                                    error(request, e.getMessage());
                                    model = view(dao, actor, new HttpServletRequestWrapper(request) {
                                        @Override public String getParameter(String name) { return null; }
                                        @Override public String getMethod() { return "GET"; }
                                    });
                                }
                                model.put("csrf", token(request));
                                request.setAttribute("ui", model);
                                request.setAttribute("activePage", page());
                                url = errorView();
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            log("Management database read failed", e);
            response.setStatus(503);
            message = "Không đọc được dữ liệu. Vui lòng kiểm tra kết nối database hoặc thử lại sau.";
            url = ERROR;
            redirect = false;
        } catch (ClassNotFoundException e) {
            log("Management database driver unavailable", e);
            response.setStatus(503);
            message = "Không kết nối được database. Vui lòng liên hệ người quản trị.";
            url = ERROR;
            redirect = false;
        } catch (IllegalArgumentException e) {
            log("Management request input rejected", e);
            response.setStatus(400);
            message = e.getMessage() == null ? "Dữ liệu gửi lên không hợp lệ." : e.getMessage();
            url = ERROR;
            redirect = false;
        } catch (RuntimeException e) {
            log("Unexpected management error", e);
            response.setStatus(500);
            message = "Không thực hiện được thao tác. Vui lòng thử lại sau.";
            url = ERROR;
            redirect = false;
        } finally {
            if (message != null) error(request, message);
            if (redirect) {
                // MVC2 exception for successful writes: PRG prevents duplicate F5 submits.
                response.setStatus(303);
                response.setHeader("Location", response.encodeRedirectURL(url));
            } else {
                request.getRequestDispatcher(url).forward(request, response);
            }
        }
    }

    private String sqlMessage(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 2601 || current.getErrorCode() == 2627)
                return "Tên đăng nhập, email, số điện thoại hoặc quyền thiết bị đã tồn tại.";
            if (current.getErrorCode() == 547)
                return "Dữ liệu tham chiếu không hợp lệ hoặc đã thay đổi. Vui lòng tải lại trang.";
            if (current.getSQLState() != null && current.getSQLState().startsWith("23"))
                return "Dữ liệu bị trùng hoặc tham chiếu không hợp lệ. Vui lòng kiểm tra lại.";
        }
        return "Không lưu được dữ liệu. Vui lòng thử lại sau.";
    }
}
