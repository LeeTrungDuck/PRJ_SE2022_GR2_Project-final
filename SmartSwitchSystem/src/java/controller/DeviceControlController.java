/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dto.SwitchDTO;
import enums.Role;
import enums.SwitchStatus;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
        String success = "web/deviceControl.jsp";
        String error = URLMap.getERROR_PAGE();
        
        String url = error;
        try {
            // check role
            //
            List<SwitchDTO> list = new ArrayList<>();
//            SwitchDTO sw1 = new SwitchDTO("1111", "deviceID", "Switch 1", 2 , SwitchStatus.ON, true);
//            sw1.setEsp32Name("ESP1");
//            SwitchDTO sw2 = new SwitchDTO("1211", "deviceID", "Switch 2", 2 , SwitchStatus.OFF, true);
//            sw2.setEsp32Name("ESP2");
//            list.add(sw1);
//            list.add(sw2);
            request.setAttribute("list", list);
            url = success;            
        } catch (Exception e) {
            log("Error at DeviceControlController: " + e.toString());
            request.setAttribute("ERROR", "Device Controller has an error, please try again!");
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
