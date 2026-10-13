package controller;

import dao.ScheduleDAO;
import dao.SwitchDAO;
import dao.DevicePermissionDAO;
import dao.UserDAO;
import dto.ScheduleDTO;
import dto.SwitchDTO;
import dto.UserDTO;
import enums.Role;
import enums.ScheduleAction;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import static controller.ManagementController.*;

public class ScheduleManagementController extends HttpServlet {

    private static final String PAGE = "SCHEDULE_MANAGER";

    private static final String JSP = "/web/ScheduleManager.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        UserDTO login = session == null ? null : (UserDTO) session.getAttribute("LOGIN_USER");
        if (login == null) {
            response.sendRedirect(request.getContextPath() + "/web/login.jsp");
            return;
        }

        try ( UserDAO users = new UserDAO()) {
            UserDTO actor = users.findById(login.getUserId());
            if (actor == null || !actor.isActive()) {
                session.invalidate();
                response.sendRedirect(request.getContextPath() + "/web/login.jsp");
                return;
            }
            session.setAttribute("LOGIN_USER", actor);
            String operation = input(request, "uiOp", 100);
            if (!operation.isEmpty() && !"POST".equals(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Thay đổi dữ liệu phải dùng POST.");
                return;
            }
            if ("POST".equals(request.getMethod()) && !operation.isEmpty()
                    && !token(request).equals(request.getParameter("csrf"))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Form không hợp lệ. Vui lòng tải lại trang.");
                return;
            }
            if ("POST".equals(request.getMethod()) && !operation.isEmpty()) {
                try {
                    String suffix = change(users, actor, request);
                    String location = request.getContextPath() + "/MainController?action=" + PAGE + suffix;
                    response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                    response.setHeader("Location", response.encodeRedirectURL(location));
                    return;
                } catch (IllegalArgumentException e) {
                    log("Schedule management input rejected", e);
                    request.setAttribute("uiError", e.getMessage());
                } catch (SQLException e) {
                    log("Schedule management write failed", e);
                    request.setAttribute("uiError", sqlMessage(e));
                }
            }
            Object flash = session.getAttribute("MANAGEMENT_FLASH");
            if (flash != null) {
                request.setAttribute("uiMessage", flash);
                session.removeAttribute("MANAGEMENT_FLASH");
            }
            Map<String, Object> model;
            try {
                model = view(users, actor, request);
            } catch (IllegalArgumentException e) {
                log("Schedule management filter rejected", e);
                request.setAttribute("uiError", e.getMessage());
                model = view(users, actor, withoutParameters(request));
            }
            model.put("csrf", token(request));
            for (Map.Entry<String, Object> entry : model.entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }
            request.setAttribute("activePage", PAGE);
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (SQLException | ClassNotFoundException e) {
            log("Schedule management database access failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("uiError", "Không đọc được dữ liệu lịch. Vui lòng thử lại.");
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (IllegalArgumentException e) {
            log("Schedule management request rejected", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("uiError", e.getMessage());
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (RuntimeException e) {
            log("Unexpected schedule management error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute("uiError", "Không thực hiện được thao tác. Vui lòng thử lại.");
            request.getRequestDispatcher(JSP).forward(request, response);
        }
    }

    private String token(HttpServletRequest request) {
        HttpSession session = request.getSession();
        synchronized (session) {
            if (session.getAttribute("MANAGEMENT_CSRF") == null) {
                session.setAttribute("MANAGEMENT_CSRF", UUID.randomUUID().toString());
            }
            return (String) session.getAttribute("MANAGEMENT_CSRF");
        }
    }

    private void message(HttpServletRequest request, String value) {
        request.getSession().setAttribute("MANAGEMENT_FLASH", value);
    }

    private HttpServletRequest withoutParameters(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return null;
            }

            @Override
            public String getMethod() {
                return "GET";
            }
        };
    }

    private String sqlMessage(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if (current.getErrorCode() == 2601 || current.getErrorCode() == 2627) {
                return "Dữ liệu lịch bị trùng hoặc không hợp lệ.";
            }
            if (current.getErrorCode() == 547
                    || (current.getSQLState() != null && current.getSQLState().startsWith("23"))) {
                return "Switch không hợp lệ hoặc dữ liệu lịch đã thay đổi. Vui lòng tải lại trang.";
            }
        }
        return "Không lưu được lịch. Vui lòng thử lại sau.";
    }

    protected Map<String, Object> view(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        String query = input(request, "q", 100).toLowerCase(Locale.ROOT);
        List<ScheduleDTO> schedules;
        List<SwitchDTO> switches;
        try ( ScheduleDAO scheduleDao = new ScheduleDAO();  SwitchDAO switchDao = new SwitchDAO()) {
            boolean canManageAll = actor.getRole() == Role.ADMIN || actor.getRole() == Role.OPERATOR;
            switches = canManageAll ? switchDao.findAll()
                    : switchDao.getPermittedSwitchesForUser(actor.getUserId());
            if (canManageAll) {
                schedules = scheduleDao.findAll();
            } else {
                schedules = scheduleDao.findByPermittedUserId(actor.getUserId());
            }
        }
        if (!query.isEmpty()) {
            List<ScheduleDTO> filtered = new ArrayList<>();
            for (ScheduleDTO schedule : schedules) {
                if (schedule.getSwitchId().toLowerCase(Locale.ROOT).contains(query)
                        || schedule.getAction().name().toLowerCase(Locale.ROOT).contains(query)
                        || schedule.getRunTime().toString().contains(query)
                        || schedule.getUserId().toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(schedule);
                }
            }
            schedules = filtered;
        }
        return row("schedules", schedules, "switches", switches, "q", query,
                "canManage", actor.getRole() == Role.ADMIN || actor.getRole() == Role.OPERATOR
                        || actor.getRole() == Role.VIEWER,
                "actorId", actor.getUserId(),
                "submitted", submittedValues(request, "scheduleId", "switchId", "scheduleAction",
                        "runTime", "enabled"),
                "submittedScheduleId", param(request, "scheduleId"),
                "submittedOperation", param(request, "uiOp"));
    }

    protected String change(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        String operation = param(request, "uiOp");
        boolean canManageAll = actor.getRole() == Role.ADMIN || actor.getRole() == Role.OPERATOR;
        require(canManageAll || actor.getRole() == Role.VIEWER,
                "Bạn không có quyền thay đổi lịch.");
        try ( ScheduleDAO scheduleDao = new ScheduleDAO();  SwitchDAO switchDao = new SwitchDAO();
                DevicePermissionDAO permissionDao = new DevicePermissionDAO()) {
            if ("addSchedule".equals(operation)) {
                String switchId = required(request, "switchId", 20, "switch");
                require(switchDao.findById(switchId) != null, "Switch không tồn tại hoặc đã ngừng hoạt động.");
                requireSwitchAccess(permissionDao, actor, canManageAll, switchId);
                ScheduleDTO schedule = new ScheduleDTO();
                schedule.setSwitchId(switchId);
                schedule.setUserId(actor.getUserId());
                schedule.setAction(action(request));
                schedule.setRunTime(runTime(request));
                schedule.setEnabled(checked(request));
                schedule.setActive(true);
                require(scheduleDao.insert(schedule), "Không thêm được lịch.");
                message(request, "Đã thêm lịch hằng ngày.");
                return "";
            }

            int id = integer(required(request, "scheduleId", 10, "Schedule ID"));
            ScheduleDTO schedule = scheduleDao.findById(id);
            require(schedule != null, "Không tìm thấy lịch đang hoạt động.");
            requireSwitchAccess(permissionDao, actor, canManageAll, schedule.getSwitchId());
            if ("updateSchedule".equals(operation)) {
                String switchId = required(request, "switchId", 20, "switch");
                require(switchDao.findById(switchId) != null, "Switch không tồn tại hoặc đã ngừng hoạt động.");
                requireSwitchAccess(permissionDao, actor, canManageAll, switchId);
                schedule.setSwitchId(switchId);
                schedule.setAction(action(request));
                schedule.setRunTime(runTime(request));
                schedule.setEnabled(checked(request));
                require(scheduleDao.update(schedule), "Không cập nhật được lịch.");
                message(request, "Đã cập nhật lịch.");
            } else if ("deleteSchedule".equals(operation)) {
                require("yes".equals(param(request, "confirm")), "Vui lòng xác nhận ngừng hoạt động lịch.");
                require(scheduleDao.delete(id), "Không xóa được lịch.");
                message(request, "Đã ngừng hoạt động lịch.");
            } else {
                throw new IllegalArgumentException("Thao tác lịch không hợp lệ.");
            }
        }
        return "";
    }

    private void requireSwitchAccess(DevicePermissionDAO permissions, UserDTO actor,
            boolean canManageAll, String switchId) throws SQLException {
        if (canManageAll) {
            return;
        }
        dto.DevicePermissionDTO permission = permissions.getUserPermission(switchId, actor.getUserId());
        require(permission != null && permission.isActive(),
                "Switch này chưa được cấp quyền xem/sửa cho tài khoản của bạn.");
    }

    private static ScheduleAction action(HttpServletRequest request) {
        return ScheduleAction.valueOf(choice(request, "scheduleAction", "ON", "OFF"));
    }

    private static LocalTime runTime(HttpServletRequest request) {
        String value = required(request, "runTime", 8, "giờ chạy");
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Giờ chạy không hợp lệ. Vui lòng nhập theo dạng HH:mm.");
        }
    }

    private static boolean checked(HttpServletRequest request) {
        String value = param(request, "enabled");
        return "on".equals(value) || "true".equals(value);
    }

    private static int integer(String value) {
        try {
            int id = Integer.parseInt(value);
            require(id > 0, "Schedule ID không hợp lệ.");
            return id;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Schedule ID phải là số nguyên.");
        }
    }
}
