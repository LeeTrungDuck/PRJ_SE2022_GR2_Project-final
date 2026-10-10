/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.DevicePermissionDAO;
import dao.SwitchDAO;
import dto.DevicePermissionDTO;
import dto.SwitchDTO;
import dto.UserDTO;
import enums.SwitchStatus;
import exception.HardwareException;
import java.io.IOException;
import java.sql.SQLException;
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
        try {
            HttpSession session = request.getSession();
            UserDTO user = (UserDTO) session.getAttribute("LOGIN_USER");
            String switchID = request.getParameter("id");
            if (switchID == null || switchID.trim().isEmpty()) {
                request.setAttribute("ERROR", "Switch ID is missing, please try again!");
                return;
            }
            if (user == null) {
                request.setAttribute("ERROR", "THIS ACTION NEED TO BE LOGIN!");
                url = URLMap.getLOGIN_PAGE();
                return;
            }
            DevicePermissionDTO permission = new DevicePermissionDAO().getUserPermission(switchID, user.getUserId());
            if (permission == null || !permission.isCanControl()) {
                request.setAttribute("ERROR", "You cannot do this!, please try again!");
                return;
            }

            SwitchDTO sw = new SwitchDAO().findById(switchID);
            if (sw == null) {
                request.setAttribute("ERROR", "switch id is not exist!, please try again !");
                return;
            }

            HardwareClient hardwareClient = new HardwareClient(sw.getEspHostName());
            String statusStr = request.getParameter("status");
            SwitchStatus status = "ON".equalsIgnoreCase(statusStr) ? SwitchStatus.ON : SwitchStatus.OFF;
            if (status.equals(SwitchStatus.ON)) {
                hardwareClient.turnOn(sw.getGpioPin());
            } else {
                hardwareClient.turnOff(sw.getGpioPin());
            }

        } catch (SQLException ex) {
            log("Error at LoginController: " + ex.toString());
            request.setAttribute("ERROR", "Database connect error, please try again!");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
            request.setAttribute("ERROR", "Class not found, please try again!");
        } catch (HardwareException ex) {
            Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
            request.setAttribute("ERROR", ex.getMessage()+" Hardware Connect fail, please try again!");
        } finally {
            request.getRequestDispatcher(url).forward(request, response);
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
