/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.ControlHistoryDAO;
import dao.DevicePermissionDAO;
import dao.SwitchDAO;
import dto.ControlHistoryDTO;
import dto.DevicePermissionDTO;
import dto.SwitchDTO;
import dto.UserDTO;
import enums.Command;
import enums.ControlResult;
import enums.SwitchStatus;
import exception.HardwareException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import utills.HardwareClient;
import utills.URLMap;

/**
 *
 * @author ltrun
 */
@WebServlet(name = "SwitchStageController", urlPatterns = {"/SwitchStageController"})
public class SwitchStageController extends HttpServlet {

    private static final String FLASH_ERROR_ATTRIBUTE = "DEVICE_CONTROL_FLASH_ERROR";
    private static final String FLASH_SWITCH_ERROR_ATTRIBUTE = "DEVICE_CONTROL_FLASH_SWITCH_ERROR";
    private static final String CSRF_ATTRIBUTE = "DEVICE_CONTROL_CSRF";
    private static final String CONTROL_TOKENS_ATTRIBUTE = "DEVICE_CONTROL_TOKENS";
    private static final Object[] SWITCH_LOCKS = createSwitchLocks();

    private static Object[] createSwitchLocks() {
        Object[] locks = new Object[64];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    protected DevicePermissionDAO permissions() throws SQLException, ClassNotFoundException {
        return new DevicePermissionDAO();
    }

    protected SwitchDAO switches() throws SQLException, ClassNotFoundException {
        return new SwitchDAO();
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        UserDTO user = session == null ? null : (UserDTO) session.getAttribute("LOGIN_USER");
        if (user != null && !consumeControlToken(session, request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Invalid or already-used control form. Reload the device page and try again.");
            return;
        }
        String switchID = request.getParameter("id");
        if (switchID == null || switchID.trim().isEmpty()) {
            processControlRequest(request, response);
            return;
        }
        Object lock = SWITCH_LOCKS[(switchID.hashCode() & 0x7fffffff) % SWITCH_LOCKS.length];
        synchronized (lock) {
            processControlRequest(request, response);
        }
    }

    private boolean consumeControlToken(HttpSession session, HttpServletRequest request) {
        String suppliedCsrf = request.getParameter("csrf");
        String expectedCsrf = (String) session.getAttribute(CSRF_ATTRIBUTE);
        if (suppliedCsrf == null || expectedCsrf == null
                || !MessageDigest.isEqual(expectedCsrf.getBytes(StandardCharsets.UTF_8),
                        suppliedCsrf.getBytes(StandardCharsets.UTF_8))
                || !"toggleRelay".equals(request.getParameter("uiOp"))) {
            return false;
        }
        String token = request.getParameter("controlToken");
        if (token == null) {
            return false;
        }
        synchronized (session) {
            Object stored = session.getAttribute(CONTROL_TOKENS_ATTRIBUTE);
            if (!(stored instanceof Map)) {
                return false;
            }
            @SuppressWarnings("unchecked")
            Map<String, String> tokens = (Map<String, String>) stored;
            String switchId = request.getParameter("id");
            if (switchId == null || !switchId.equals(tokens.get(token))) {
                return false;
            }
            tokens.remove(token);
            return true;
        }
    }

    private void processControlRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        String url = new URLMap().getUrl("DEVICE_CONTROL");
        boolean redirectAfterAction = false;
        int pageNumber = parsePageNumber(request.getParameter("page"));
        String switchID = request.getParameter("id");
        UserDTO user = null;
        SwitchDTO sw = null;
        Command command = null;
        String switchMessage = null;
        try {
                HttpSession session = request.getSession();
                user = (UserDTO) session.getAttribute("LOGIN_USER");
                if (switchID == null || switchID.trim().isEmpty()) {
                    request.setAttribute("ERROR", "Switch ID is required. Please try again.");
                    return;
                }
                if (user == null) {
                    request.setAttribute("ERROR", "Please sign in first.");
                    url = URLMap.getLOGIN_PAGE();
                    return;
                }
                redirectAfterAction = true;
                DevicePermissionDTO permission;
                try (DevicePermissionDAO permissionDao = permissions()) {
                    permission = permissionDao.getUserPermission(switchID, user.getUserId());
                }
                if (permission == null || !permission.isActive()) {
                    request.setAttribute("ERROR", "You do not have permission to control this switch.");
                    return;
                }

                try (SwitchDAO switchDAO = switches();
                     ControlHistoryDAO hisDao = new ControlHistoryDAO()) {
                    sw = switchDAO.findById(switchID);
                    if (sw == null) {
                        request.setAttribute("ERROR", "The switch is inactive or does not exist.");
                        return;
                    }

                    HardwareClient hardwareClient = new HardwareClient(sw.getEspHostName());
                    String currentStatus = hardwareClient.getStatus(sw.getGpioPin());
                    SwitchStatus status = "ON".equals(currentStatus) ? SwitchStatus.OFF : SwitchStatus.ON;
                    command = status == SwitchStatus.ON ? Command.ON : Command.OFF;
                    if (status == SwitchStatus.ON) {
                        hardwareClient.turnOn(sw.getGpioPin());
                    } else {
                        hardwareClient.turnOff(sw.getGpioPin());
                    }
                    ControlHistoryDTO hisDto = new ControlHistoryDTO();

                    hisDto.setSwitchId(switchID);
                    hisDto.setUserId(user.getUserId());
                    hisDto.setCommand(command);
                    hisDto.setControlTime(hisDto.getTimeNow());
                    hisDto.setResult(status == SwitchStatus.ON ? ControlResult.ON : ControlResult.OFF);
                    if (!switchDAO.updateStatus(switchID, status)) {
                        request.setAttribute("ERROR", "The device state changed, but the new state could not be saved.");
                    }
                    if (!hisDao.insert(hisDto)) {
                        log("Error at SwitchStageController: History control cannot insert into database");
                        request.setAttribute("ERROR", "Unable to save control history.");
                    }
                    if (request.getAttribute("ERROR") == null) {
                        switchMessage = "ESP32 connected. Switch turned " + status.name() + ".";
                    }
                }
            } catch (SQLException ex) {
                log("Error at LoginController: " + ex.toString());
                request.setAttribute("ERROR", "Database connect error, please try again!");
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
                request.setAttribute("ERROR", "Class not found, please try again!");
        } catch (HardwareException ex) {
                log("ESP32 hardware operation failed", ex);
                recordFailedControl(user, switchID, sw, command);
                request.setAttribute("SWITCH_SYNC_ERROR",
                        "Unable to connect to or communicate with ESP32. Please try again.");
        } finally {
            if (redirectAfterAction) {
                HttpSession session = request.getSession();
                Object error = request.getAttribute("ERROR");
                Object switchError = request.getAttribute("SWITCH_SYNC_ERROR");
                if (switchError != null && switchID != null) {
                    Map<String, String> switchErrors = new LinkedHashMap<>();
                    switchErrors.put(switchID, String.valueOf(switchError));
                    session.setAttribute(FLASH_SWITCH_ERROR_ATTRIBUTE, switchErrors);
                    session.removeAttribute(FLASH_ERROR_ATTRIBUTE);
                } else if (error != null) {
                    session.setAttribute(FLASH_ERROR_ATTRIBUTE, error);
                    session.removeAttribute(FLASH_SWITCH_ERROR_ATTRIBUTE);
                } else {
                    if (switchMessage != null && switchID != null) {
                        Map<String, String> switchMessages = new LinkedHashMap<>();
                        switchMessages.put(switchID, switchMessage);
                        session.setAttribute(FLASH_SWITCH_ERROR_ATTRIBUTE, switchMessages);
                    } else {
                        session.removeAttribute(FLASH_SWITCH_ERROR_ATTRIBUTE);
                    }
                    session.removeAttribute(FLASH_ERROR_ATTRIBUTE);
                }
                response.sendRedirect(response.encodeRedirectURL(
                        request.getContextPath() + "/MainController?action=DEVICE_CONTROL&page=" + pageNumber
                ));
            } else {
                request.getRequestDispatcher(url).forward(request, response);
            }
        }
    }

    /**
     * A failed hardware command is still a control attempt and must remain
     * visible in control history.
     */
    private void recordFailedControl(UserDTO user, String switchID, SwitchDTO sw,
            Command attemptedCommand) {
        if (user == null || switchID == null || switchID.trim().isEmpty()) {
            return;
        }

        Command command = attemptedCommand;
        if (command == null && sw != null) {
            command = sw.getStatus() == SwitchStatus.ON ? Command.OFF : Command.ON;
        }
        if (command == null) {
            command = Command.ON;
        }

        ControlHistoryDTO failedControl = new ControlHistoryDTO();
        failedControl.setUserId(user.getUserId());
        failedControl.setSwitchId(switchID);
        failedControl.setCommand(command);
        failedControl.setResult(ControlResult.ERROR);
        failedControl.setControlTime(failedControl.getTimeNow());

        try (ControlHistoryDAO historyDao = new ControlHistoryDAO()) {
            if (!historyDao.insert(failedControl)) {
                log("Error at SwitchStageController: Failed control history cannot insert into database");
            }
        } catch (SQLException | ClassNotFoundException historyException) {
            log("Error at SwitchStageController: Failed control history cannot insert into database",
                    historyException);
        }
    }

    private int parsePageNumber(String pageParameter) {
        if (pageParameter == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(pageParameter));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Switch controls must be submitted using POST.");
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
