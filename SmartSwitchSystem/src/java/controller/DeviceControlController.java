/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.SwitchDAO;
import dto.SwitchDTO;
import dto.UserDTO;
import enums.Role;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import utills.URLMap;

/**
 *
 * @author ltrun
 */
@WebServlet(name = "DeviceControlController", urlPatterns = {"/DeviceControlController"})
public class DeviceControlController extends HttpServlet {

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
        String error = URLMap.getERROR_PAGE();
        String url = error;
        try {
            HttpSession session = request.getSession(false);
            UserDTO user = (session == null) ? null : (UserDTO) session.getAttribute("LOGIN_USER");
            if (user == null) {
                url = error;
                request.setAttribute("ERROR","LOGIN PLS!");
            } else {
                SwitchDAO dao = new SwitchDAO();
                List<SwitchDTO> list = new ArrayList<>();
                // lấy danh sách switch theo quyền (SwitchDAO) rồi đặt vào request
                if (user.getRole() == Role.VIEWER) {
                    list = dao.getSwitchesForViewer(user.getUserId());
                } else {
                    list = dao.findAll();
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
