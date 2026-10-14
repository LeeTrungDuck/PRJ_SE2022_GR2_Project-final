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
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.LinkedHashMap;
import java.util.Map;
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
        response.setContentType("text/html;charset=UTF-8");
        String url = new URLMap().getUrl("DEVICE_CONTROL");
        boolean redirectAfterAction = false;
        int pageNumber = parsePageNumber(request.getParameter("page"));
        String switchID = request.getParameter("id");
        try {
                HttpSession session = request.getSession();
                UserDTO user = (UserDTO) session.getAttribute("LOGIN_USER");
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
                    SwitchDTO sw = switchDAO.findById(switchID);
                    if (sw == null) {
                        request.setAttribute("ERROR", "The switch is inactive or does not exist.");
                        return;
                    }

                    HardwareClient hardwareClient = new HardwareClient(sw.getEspHostName());
                    String currentStatus = hardwareClient.getStatus(sw.getGpioPin());
                    SwitchStatus status = "ON".equals(currentStatus) ? SwitchStatus.OFF : SwitchStatus.ON;
                    if (status == SwitchStatus.ON) {
                        hardwareClient.turnOn(sw.getGpioPin());
                    } else {
                        hardwareClient.turnOff(sw.getGpioPin());
                    }
                    ControlHistoryDTO hisDto = new ControlHistoryDTO();

                    hisDto.setSwitchId(switchID);
                    hisDto.setUserId(user.getUserId());
                    hisDto.setCommand(status == SwitchStatus.ON ? Command.ON : Command.OFF);
                    hisDto.setControlTime(hisDto.getTimeNow());
                    hisDto.setResult(status == SwitchStatus.ON ? ControlResult.ON : ControlResult.OFF);
                    if (!switchDAO.updateStatus(switchID, status)) {
                        request.setAttribute("ERROR", "The device state changed, but the new state could not be saved.");
                    }
                    if (!hisDao.insert(hisDto)) {
                        log("Error at SwitchStageController: History control cannot insert into database");
                        request.setAttribute("ERROR", "Unable to save control history.");
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
                    session.removeAttribute(FLASH_ERROR_ATTRIBUTE);
                    session.removeAttribute(FLASH_SWITCH_ERROR_ATTRIBUTE);
                }
                response.sendRedirect(response.encodeRedirectURL(
                        request.getContextPath() + "/MainController?action=DEVICE_CONTROL&page=" + pageNumber
                ));
            } else {
                request.getRequestDispatcher(url).forward(request, response);
            }
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
        processRequest(request, response);
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
