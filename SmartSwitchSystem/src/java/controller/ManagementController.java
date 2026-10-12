package controller;

import dao.UserDAO;
import dto.UserDTO;
import enums.Role;
import java.io.IOException;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    static Map<String, Object> row(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Map entries must be key/value pairs.");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            values.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return values;
    }

    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static boolean yes(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(str(value)) || "1".equals(str(value));
    }

    static String normalize(Object value) {
        return Normalizer.normalize(str(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("gpio\\s*0*(\\d+)", "gpio $1")
                .trim();
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> list(Object value) {
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("Expected a list value.");
        }
        return (List<T>) value;
    }

    static String param(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value.trim();
    }

    static String input(HttpServletRequest request, String key, int max) {
        String value = param(request, key);
        require(value.length() <= max, "Trường " + key + " vượt quá độ dài cho phép.");
        return value;
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    static String required(HttpServletRequest request, String key, int max, String label) {
        String value = input(request, key, max);
        require(!value.isEmpty(), "Vui lòng nhập " + label + ".");
        return value;
    }

    static String choice(HttpServletRequest request, String key, String... allowed) {
        String value = param(request, key);
        require(Arrays.asList(allowed).contains(value), "Giá trị " + key + " không hợp lệ.");
        return value;
    }

    static void password(HttpServletRequest request, String key, int min, boolean strong) {
        String value = request.getParameter(key);
        require(value != null && value.length() >= min && value.length() <= 128,
                "Mật khẩu cần từ " + min + " đến 128 ký tự.");
        if (strong) {
            require(value.matches("(?s).*[A-Z].*") && value.matches("(?s).*\\d.*")
                    && value.matches("(?s).*[^\\p{L}\\p{N}\\s].*"),
                    "Mật khẩu cần có chữ hoa, chữ số và ký tự đặc biệt.");
        }
    }

    static String initials(Object name) {
        String[] words = str(name).trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) {
            return "?";
        }
        String first = words[0].substring(0, 1);
        String last = words.length > 1 ? words[words.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    static Map<String, Object> copyMap(Map<String, Object> value) {
        return new LinkedHashMap<>(value);
    }

    static Map<String, Object> submittedValues(HttpServletRequest request, String... names) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!"POST".equals(request.getMethod())) {
            return values;
        }
        for (String name : names) {
            String value = request.getParameter(name);
            if (value != null) {
                values.put(name, value.substring(0, Math.min(value.length(), 512)));
            }
        }
        return values;
    }

    protected abstract String page();
    protected abstract String errorView();
    protected abstract String successUrl();
    protected abstract boolean allowed(Role role);
    protected abstract Map<String,Object> view(UserDAO dao, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException;
    protected abstract String change(UserDAO dao, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException;

    protected boolean useLegacyModelWrapper() {
        return true;
    }

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

    private HttpServletRequest requestWithoutSubmittedValues(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String key) {
                return null;
            }

            @Override
            public String getMethod() {
                return "GET";
            }
        };
    }
}
