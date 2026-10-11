/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.SwitchDAO;
import dto.SwitchDTO;
import dto.UserDTO;
import enums.Role;
import enums.SwitchStatus;
import exception.HardwareException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
@WebServlet(name = "DeviceControlController", urlPatterns = {"/DeviceControlController"})
public class DeviceControlController extends HttpServlet {

    private static final String FLASH_ERROR_ATTRIBUTE = "DEVICE_CONTROL_FLASH_ERROR";

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
        String success = "web/DeviceControl.jsp";
        String error = URLMap.getLOGIN_PAGE();
        String url = error;
        try {
            HttpSession session = request.getSession(false);
            UserDTO user = (session == null) ? null : (UserDTO) session.getAttribute("LOGIN_USER");
            if (session != null) {
                Object flashError = session.getAttribute(FLASH_ERROR_ATTRIBUTE);
                if (flashError != null) {
                    request.setAttribute("ERROR", flashError);
                    session.removeAttribute(FLASH_ERROR_ATTRIBUTE);
                }
            }
            if (user == null) {
                url = error;
                if (request.getAttribute("ERROR") == null) {
                    request.setAttribute("ERROR","LOGIN PLS!");
                }
            } else {
                SwitchDAO dao = new SwitchDAO();
                List<SwitchDTO> list = new ArrayList<>();
                // lấy danh sách switch theo quyền (SwitchDAO) rồi đặt vào request
                if (user.getRole() == Role.VIEWER) {
                    list = dao.getSwitchesForViewer(user.getUserId());
                } else {
                    list = dao.findAll();
                }
                if (request.getAttribute("ERROR") == null) {
                    syncSwitchStatuses(dao, list, request);
                }
                request.setAttribute("list", list);
                url = success;
            }
        } catch (SQLException ex) {
            log("Error at DeviceControlController (SQL): " + ex.toString());
            request.setAttribute("ERROR", "Device Controller has an error (SQL), please try again!");
        } catch (ClassNotFoundException ex) {
            log("Error at DeviceControlController (class): " + ex.toString());
            request.setAttribute("ERROR", "Device Controller has an error(Class), please try again!");
        }finally {
            request.getRequestDispatcher(url).forward(request, response);
    }
}

    private void syncSwitchStatuses(SwitchDAO dao, List<SwitchDTO> switches,
            HttpServletRequest request) throws SQLException {
        Map<String, String> switchSyncErrors = new LinkedHashMap<>();

        for (SwitchDTO sw : switches) {
            try {
                String status = new HardwareClient(sw.getEspHostName())
                        .getStatus(sw.getGpioPin());
                if (!"ON".equals(status) && !"OFF".equals(status)) {
                    switchSyncErrors.put(sw.getSwitchId(), "Lỗi kết nối");
                    continue;
                }

                SwitchStatus actualStatus = SwitchStatus.valueOf(status);
                SwitchStatus previousStatus = sw.getStatus();
                sw.setStatus(actualStatus);
                if (actualStatus != previousStatus
                        && !dao.updateStatus(sw.getSwitchId(), actualStatus)) {
                    switchSyncErrors.put(sw.getSwitchId(), "Không lưu được trạng thái");
                }
            } catch (HardwareException ex) {
                log("Unable to sync switch " + sw.getSwitchId()
                        + " from ESP32: " + ex.getMessage());
                switchSyncErrors.put(sw.getSwitchId(), "Lỗi kết nối");
            }
        }

        request.setAttribute("switchSyncErrors", switchSyncErrors);
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
