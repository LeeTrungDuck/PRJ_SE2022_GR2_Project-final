package controller;

import dao.ESP32DeviceDAO;
import dao.SwitchDAO;
import dao.UserDAO;
import dto.ESP32DeviceDTO;
import dto.SwitchDTO;
import dto.UserDTO;
import enums.DeviceStatus;
import enums.Role;
import enums.SwitchStatus;
import java.io.IOException;
import java.sql.SQLException;
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

public class DeviceManagementController extends HttpServlet {

    private static final String PAGE = "DEVICE_MANAGER";

    private static final String JSP = "/web/DeviceManager.jsp";

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
            if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.OPERATOR) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Bạn không có quyền sử dụng chức năng này.");
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
                    log("Device management input rejected", e);
                    request.setAttribute("uiError", e.getMessage());
                } catch (SQLException e) {
                    log("Device management write failed", e);
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
                log("Device management filter rejected", e);
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
            log("Device management database access failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("uiError", "Không đọc được dữ liệu thiết bị. Vui lòng thử lại.");
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (IllegalArgumentException e) {
            log("Device management request rejected", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("uiError", e.getMessage());
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (RuntimeException e) {
            log("Unexpected device management error", e);
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
                return "Mã thiết bị, switch hoặc hostname đã tồn tại.";
            }
            if (current.getErrorCode() == 547
                    || (current.getSQLState() != null && current.getSQLState().startsWith("23"))) {
                return "Dữ liệu tham chiếu không hợp lệ. Vui lòng tải lại trang.";
            }
        }
        return "Không lưu được dữ liệu. Vui lòng thử lại sau.";
    }

    protected Map<String, Object> view(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        String query = input(request, "q", 100).toLowerCase(Locale.ROOT);
        List<ESP32DeviceDTO> devices;
        List<ESP32DeviceDTO> deviceOptions;
        List<SwitchDTO> switches;
        try ( ESP32DeviceDAO deviceDao = new ESP32DeviceDAO();  SwitchDAO switchDao = new SwitchDAO()) {
            deviceOptions = deviceDao.findAll();
            devices = new ArrayList<>();
            for (ESP32DeviceDTO device : deviceOptions) {
                if (contains(query, device.getDeviceId(), device.getName(), device.getHostName())) {
                    devices.add(device);
                }
            }
            switches = new ArrayList<>();
            for (SwitchDTO sw : switchDao.findAll()) {
                if (contains(query, sw.getSwitchId(), sw.getSwitchName(), sw.getDeviceId(), sw.getEspName())) {
                    switches.add(sw);
                }
            }
        }
        return row("devices", devices, "deviceOptions", deviceOptions, "switches", switches, "q", query,
                "canManage", true, "submitted", submittedValues(request, "deviceId", "deviceName",
                        "hostName", "switchId", "switchDeviceId", "switchName", "gpioPin"),
                "submittedOperation", param(request, "uiOp"));
    }

    protected String change(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        require(actor.getRole() == Role.ADMIN || actor.getRole() == Role.OPERATOR,
                "Bạn không có quyền thay đổi thiết bị.");
        String operation = param(request, "uiOp");
        if (operation.endsWith("Device")) {
            try ( ESP32DeviceDAO dao = new ESP32DeviceDAO()) {
                if ("addDevice".equals(operation)) {
                    String id = required(request, "deviceId", 20, "Device ID");
                    require(id.matches("ESP[A-Za-z0-9_-]{1,17}"), "Device ID phải bắt đầu bằng ESP.");
                    ESP32DeviceDTO device = new ESP32DeviceDTO();
                    device.setDeviceId(id);
                    device.setName(required(request, "deviceName", 50, "tên thiết bị"));
                    device.setHostName(required(request, "hostName", 255, "hostname"));
                    device.setStatus(DeviceStatus.OFFLINE);
                    device.setActive(true);
                    require(dao.insert(device), "Không thêm được thiết bị.");
                    message(request, "Đã thêm thiết bị.");
                    return "";
                }
                String id = required(request, "deviceId", 20, "Device ID");
                ESP32DeviceDTO current = dao.findById(id);
                require(current != null, "Không tìm thấy thiết bị đang hoạt động.");
                if ("updateDevice".equals(operation)) {
                    current.setName(required(request, "deviceName", 50, "tên thiết bị"));
                    current.setHostName(required(request, "hostName", 255, "hostname"));
                    require(dao.update(current), "Không cập nhật được thiết bị.");
                    message(request, "Đã cập nhật thông tin thiết bị.");
                } else if ("deleteDevice".equals(operation)) {
                    require("yes".equals(param(request, "confirm")), "Vui lòng xác nhận ngừng hoạt động thiết bị.");
                    require(dao.delete(id), "Không xóa được thiết bị.");
                    message(request, "Đã ngừng hoạt động thiết bị.");
                } else {
                    throw new IllegalArgumentException("Thao tác thiết bị không hợp lệ.");
                }
            }
            return "";
        }

        if (operation.endsWith("Switch")) {
            try ( SwitchDAO switchDao = new SwitchDAO();  ESP32DeviceDAO deviceDao = new ESP32DeviceDAO()) {
                if ("addSwitch".equals(operation)) {
                    String id = required(request, "switchId", 20, "Switch ID");
                    require(id.matches("SW[A-Za-z0-9_-]{1,18}"), "Switch ID phải bắt đầu bằng SW.");
                    String deviceId = required(request, "switchDeviceId", 20, "thiết bị ESP32");
                    require(deviceDao.findById(deviceId) != null, "Thiết bị ESP32 không tồn tại hoặc đã ngừng hoạt động.");
                    int gpio = number(request, "gpioPin");
                    SwitchDTO sw = new SwitchDTO();
                    sw.setSwitchId(id);
                    sw.setDeviceId(deviceId);
                    sw.setSwitchName(required(request, "switchName", 50, "tên switch"));
                    sw.setGpioPin(gpio);
                    sw.setStatus(SwitchStatus.OFF);
                    sw.setActive(true);
                    require(switchDao.insert(sw), "Không thêm được switch.");
                    message(request, "Đã thêm switch.");
                    return "";
                }
                String id = required(request, "switchId", 20, "Switch ID");
                SwitchDTO current = switchDao.findById(id);
                require(current != null, "Không tìm thấy switch đang hoạt động.");
                if ("updateSwitch".equals(operation)) {
                    String deviceId = required(request, "switchDeviceId", 20, "thiết bị ESP32");
                    require(deviceDao.findById(deviceId) != null, "Thiết bị ESP32 không tồn tại hoặc đã ngừng hoạt động.");
                    current.setDeviceId(deviceId);
                    current.setSwitchName(required(request, "switchName", 50, "tên switch"));
                    current.setGpioPin(number(request, "gpioPin"));
                    require(switchDao.update(current), "Không cập nhật được switch.");
                    message(request, "Đã cập nhật thông tin switch.");
                } else if ("deleteSwitch".equals(operation)) {
                    require("yes".equals(param(request, "confirm")), "Vui lòng xác nhận ngừng hoạt động switch.");
                    require(switchDao.delete(id), "Không xóa được switch.");
                    message(request, "Đã ngừng hoạt động switch.");
                } else {
                    throw new IllegalArgumentException("Thao tác switch không hợp lệ.");
                }
            }
            return "";
        }
        throw new IllegalArgumentException("Thao tác thiết bị không hợp lệ.");
    }

    private static boolean contains(String query, String... values) {
        if (query.isEmpty()) {
            return true;
        }
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private static int number(HttpServletRequest request, String key) {
        String value = required(request, key, 3, "GPIO pin");
        try {
            int gpio = Integer.parseInt(value);
            require(gpio >= 0 && gpio <= 255, "GPIO pin phải từ 0 đến 255.");
            return gpio;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("GPIO pin phải là số nguyên.");
        }
    }
}
