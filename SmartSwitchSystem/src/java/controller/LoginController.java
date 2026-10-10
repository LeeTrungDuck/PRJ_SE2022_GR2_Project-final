/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.UserDAO;
import dto.UserDTO;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import utills.URLMap;

/**
 *
 * @author ltrun
 */
public class LoginController extends HttpServlet {


    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        String success = new URLMap().getUrl("DEVICE_CONTROL");
        String error = URLMap.getLOGIN_PAGE();
        String url = URLMap.getLOGIN_PAGE();
        try{
            String userName = (String)request.getParameter("userName");
            String passWord = (String)request.getParameter("passWord");
            UserDTO dto = new UserDAO().checkLogin(userName, passWord);
            
            if (dto != null) {
                HttpSession session = request.getSession();
                session.setAttribute("LOGIN_USER",dto);
                request.setAttribute("UI_PAGE", "DEVICE_CONTROL");
                url = success;
            }else{
                request.setAttribute("ERROR", "USER DOEST NOT EXIST!");
                url = error;
            }
        }catch(SQLException e){
            log("Error at LoginController: " + e.toString());
            url = URLMap.getERROR_PAGE();
            request.setAttribute("ERROR", "Database connect error, please try again!");
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(LoginController.class.getName()).log(Level.SEVERE, null, ex);
            url = URLMap.getERROR_PAGE();
            request.setAttribute("ERROR", "Class not found, please try again!");
        }finally{
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
