package controller;

import dao.ESP32DeviceDAO;
import dao.DevicePermissionDAO;
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
import java.util.HashMap;
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
            if (actor.getRole() != Role.ADMIN && actor.getRole() != Role.OPERATOR
                    && actor.getRole() != Role.VIEWER) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "You do not have permission to use this feature.");
                return;
            }
            session.setAttribute("LOGIN_USER", actor);
            String operation = input(request, "uiOp", 100);
            if (!operation.isEmpty() && !"POST".equals(request.getMethod())) {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Changes must be submitted using POST.");
                return;
            }
            if ("POST".equals(request.getMethod()) && !operation.isEmpty()
                    && !token(request).equals(request.getParameter("csrf"))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid form. Please reload this page.");
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
            request.setAttribute("uiError", "Unable to load device data. Please try again.");
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (IllegalArgumentException e) {
            log("Device management request rejected", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            request.setAttribute("uiError", e.getMessage());
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (RuntimeException e) {
            log("Unexpected device management error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute("uiError", "Unable to complete the operation. Please try again.");
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
                return "The device ID, switch ID or hostname already exists.";
            }
            if (current.getErrorCode() == 547
                    || (current.getSQLState() != null && current.getSQLState().startsWith("23"))) {
                return "Invalid reference data. Please reload this page.";
            }
        }
        return "Unable to save data. Please try again later.";
    }

    protected Map<String, Object> view(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        String query = input(request, "q", 100).toLowerCase(Locale.ROOT);
        String espFilter = input(request, "esp", 20);
        List<ESP32DeviceDTO> devices;
        List<ESP32DeviceDTO> deviceOptions;
        List<ESP32DeviceDTO> espFilterOptions;
        List<SwitchDTO> switches;
        Map<String, Boolean> devicePowerStates = new HashMap<>();
        boolean canManageDevices = actor.getRole() == Role.ADMIN || actor.getRole() == Role.OPERATOR;
        boolean canManageSwitches = canManageDevices || actor.getRole() == Role.VIEWER;
        try ( ESP32DeviceDAO deviceDao = new ESP32DeviceDAO();  SwitchDAO switchDao = new SwitchDAO()) {
            List<ESP32DeviceDTO> allDevices = deviceDao.findAll();
            List<ESP32DeviceDTO> allEspFilterOptions = canManageDevices
                    ? deviceDao.findAllForManagement() : allDevices;
            List<SwitchDTO> allSwitches = actor.getRole() == Role.VIEWER
                    ? switchDao.getPermittedSwitchesForUser(actor.getUserId()) : switchDao.findAllForManagement();
            espFilterOptions = new ArrayList<>();
            for (ESP32DeviceDTO device : allEspFilterOptions) {
                if (canManageDevices || allSwitches.stream().anyMatch(sw -> sw.getDeviceId().equals(device.getDeviceId()))) {
                    espFilterOptions.add(device);
                }
            }
            deviceOptions = new ArrayList<>();
            for (ESP32DeviceDTO device : allDevices) {
                if (canManageDevices || allSwitches.stream().anyMatch(sw -> sw.getDeviceId().equals(device.getDeviceId()))) {
                    deviceOptions.add(device);
                }
            }
            for (ESP32DeviceDTO device : deviceOptions) {
                devicePowerStates.put(device.getDeviceId(), false);
            }
            devices = new ArrayList<>();
            for (ESP32DeviceDTO device : deviceOptions) {
                if ((espFilter.isEmpty() || "all".equals(espFilter) || espFilter.equals(device.getDeviceId()))
                        && contains(query, device.getDeviceId(), device.getName(), device.getHostName())) {
                    devices.add(device);
                }
            }
            switches = new ArrayList<>();
            for (SwitchDTO sw : allSwitches) {
                if (sw.isActive() && sw.getStatus() == SwitchStatus.ON) {
                    devicePowerStates.put(sw.getDeviceId(), true);
                }
                if ((espFilter.isEmpty() || "all".equals(espFilter) || espFilter.equals(sw.getDeviceId()))
                        && contains(query, sw.getSwitchId(), sw.getSwitchName(), sw.getDeviceId(),
                                sw.getEspName(), sw.getEspHostName())) {
                    switches.add(sw);
                }
            }
        }
        return row("devices", devices, "deviceOptions", deviceOptions, "espFilterOptions", espFilterOptions,
                "switches", switches,
                "devicePowerStates", devicePowerStates, "q", query, "espFilter", espFilter,
                "canManage", canManageDevices, "canManageDevices", canManageDevices,
                "canManageSwitches", canManageSwitches,
                "submitted", submittedValues(request, "deviceId", "deviceName",
                        "hostName", "switchId", "switchDeviceId", "switchName", "gpioPin"),
                "submittedOperation", param(request, "uiOp"));
    }

    protected String change(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        String operation = param(request, "uiOp");
        boolean canManageDevices = actor.getRole() == Role.ADMIN || actor.getRole() == Role.OPERATOR;
        require(canManageDevices || (actor.getRole() == Role.VIEWER && "updateSwitch".equals(operation)),
                "You do not have permission to perform this operation.");
        if (operation.endsWith("Device")) {
            require(canManageDevices, "You do not have permission to manage ESP32 devices.");
            try ( ESP32DeviceDAO dao = new ESP32DeviceDAO()) {
                if ("addDevice".equals(operation)) {
                    String id = required(request, "deviceId", 20, "Device ID");
                    require(id.matches("ESP[A-Za-z0-9_-]{1,17}"), "Device ID must start with ESP.");
                    ESP32DeviceDTO device = new ESP32DeviceDTO();
                    device.setDeviceId(id);
                    device.setName(required(request, "deviceName", 50, "device name"));
                    device.setHostName(required(request, "hostName", 255, "hostname"));
                    device.setStatus(DeviceStatus.OFFLINE);
                    device.setActive(true);
                    require(dao.insert(device), "Unable to add the device.");
                    message(request, "Device added.");
                    return "";
                }
                String id = required(request, "deviceId", 20, "Device ID");
                ESP32DeviceDTO current = dao.findById(id);
                require(current != null, "No active device was found.");
                if ("updateDevice".equals(operation)) {
                    current.setName(required(request, "deviceName", 50, "device name"));
                    require(dao.update(current), "Unable to update the device.");
                    message(request, "Device updated.");
                } else if ("deleteDevice".equals(operation)) {
                    require("yes".equals(param(request, "confirm")), "Please confirm device deactivation.");
                    require(dao.delete(id), "Unable to deactivate the device.");
                    message(request, "Device deactivated.");
                } else {
                    throw new IllegalArgumentException("Invalid device operation.");
                }
            }
            return "";
        }

        if (operation.endsWith("Switch")) {
            try ( SwitchDAO switchDao = new SwitchDAO();  ESP32DeviceDAO deviceDao = new ESP32DeviceDAO()) {
                if (!canManageDevices) {
                    String id = required(request, "switchId", 20, "Switch ID");
                    try (DevicePermissionDAO permissionDao = new DevicePermissionDAO()) {
                        dto.DevicePermissionDTO permission = permissionDao.getUserPermission(id, actor.getUserId());
                        require(permission != null && permission.isActive(),
                                "Your account does not have permission to view or edit this switch.");
                    }
                }
                if ("addSwitch".equals(operation)) {
                    require(canManageDevices, "You do not have permission to add switches.");
                    String id = required(request, "switchId", 20, "Switch ID");
                    require(id.matches("SW[A-Za-z0-9_-]{1,18}"), "Switch ID must start with SW.");
                    String deviceId = required(request, "switchDeviceId", 20, "ESP32 device");
                    require(deviceDao.findById(deviceId) != null, "The ESP32 device does not exist or is inactive.");
                    int gpio = number(request, "gpioPin");
                    SwitchDTO sw = new SwitchDTO();
                    sw.setSwitchId(id);
                    sw.setDeviceId(deviceId);
                    sw.setSwitchName(required(request, "switchName", 50, "switch name"));
                    sw.setGpioPin(gpio);
                    sw.setStatus(SwitchStatus.OFF);
                    sw.setActive(true);
                    require(switchDao.insert(sw), "Unable to add the switch.");
                    message(request, "Switch added.");
                    return "";
                }
                String id = required(request, "switchId", 20, "Switch ID");
                if ("activateSwitch".equals(operation)) {
                    require(canManageDevices, "You do not have permission to activate switches.");
                    SwitchDTO inactive = switchDao.findByIdForManagement(id);
                    require(inactive != null && !inactive.isActive(), "No inactive switch was found.");
                    require(inactive.isEspActive(), "The switch cannot be activated while its ESP32 device is inactive.");
                    require(switchDao.activate(id), "Unable to activate the switch. Please reload this page.");
                    message(request, "Switch activated.");
                    return "";
                }
                SwitchDTO current = switchDao.findById(id);
                require(current != null, "No active switch was found.");
                if ("updateSwitch".equals(operation)) {
                    current.setSwitchName(required(request, "switchName", 50, "switch name"));
                    require(switchDao.update(current), "Unable to update the switch.");
                    message(request, "Switch updated.");
                } else if ("deleteSwitch".equals(operation)) {
                    require(canManageDevices, "You do not have permission to deactivate switches.");
                    require("yes".equals(param(request, "confirm")), "Please confirm switch deactivation.");
                    require(switchDao.delete(id), "Unable to deactivate the switch.");
                    message(request, "Switch deactivated.");
                } else {
                    throw new IllegalArgumentException("Invalid switch operation.");
                }
            }
            return "";
        }
        throw new IllegalArgumentException("Invalid device operation.");
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
            require(gpio >= 0 && gpio <= 255, "GPIO pin must be between 0 and 255.");
            return gpio;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("GPIO pin must be an integer.");
        }
    }
}
