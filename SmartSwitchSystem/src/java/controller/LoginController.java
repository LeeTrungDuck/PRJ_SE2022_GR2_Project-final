/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package controller;

import dao.UserDAO;
import dto.UserDTO;
import java.io.IOException;
import java.sql.SQLException;
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
    private final String ERROR = URLMap.getERROR_PAGE();
    private final String LOGIN = URLMap.getLOGIN_PAGE();
    private final String SUCCESS = "/MainController?action=DEVICE_CONTROL";

    protected UserDAO users() throws SQLException, ClassNotFoundException {
        return new UserDAO();
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String url = ERROR;
        boolean redirect = false;
        try {
            request.setCharacterEncoding("UTF-8");
            response.setContentType("text/html;charset=UTF-8");
            HttpSession previousSession = request.getSession(false);
            if ("POST".equals(request.getMethod()) && previousSession != null) {
                previousSession.removeAttribute("LOGIN_USER");
            }
            String submittedName = request.getParameter("userName");
            String userName = submittedName == null ? "" : submittedName.trim();
            // Preserve password whitespace: it is part of the credential.
            String passWord = request.getParameter("passWord");
            UserDTO dto = null;
            if (!userName.isEmpty() && userName.length() <= 50
                    && passWord != null && !passWord.isEmpty() && passWord.length() <= 128) {
                try (UserDAO dao = users()) {
                    dto = dao.checkLogin(userName, passWord);
                }
            }

            if (dto == null) {
                request.setAttribute("ERROR", "Tài khoản hoặc mật khẩu sai!");
                url = LOGIN;
            } else if (!dto.isActive()) {
                request.setAttribute("ERROR", "Tài khoản đã bị vô hiệu hóa!");
                url = LOGIN;
            } else {
                // Đăng nhập thành công: Dùng Redirect qua MainController để tránh lỗi F5 (PRG Pattern)
                HttpSession session = request.getSession();
                request.changeSessionId();
                session.removeAttribute("PERMISSION_DRAFTS");
                session.removeAttribute("MANAGEMENT_CSRF");
                session.removeAttribute("MANAGEMENT_FLASH");
                session.setAttribute("LOGIN_USER", dto);
                url = request.getContextPath() + SUCCESS;
                redirect = true;
            }
        } catch (SQLException e) {
            log("Login database error", e);
            response.setStatus(503);
            request.setAttribute("ERROR", "Không kết nối được database. Vui lòng thử lại sau.");
        } catch (ClassNotFoundException e) {
            log("Login database driver unavailable", e);
            response.setStatus(503);
            request.setAttribute("ERROR", "Không kết nối được database. Vui lòng liên hệ người quản trị.");
        } catch (RuntimeException e) {
            log("Unexpected login error", e);
            response.setStatus(500);
            request.setAttribute("ERROR", "Không đăng nhập được. Vui lòng thử lại sau.");
        } finally {
            if (redirect) {
                response.sendRedirect(url);
            } else {
                request.getRequestDispatcher(url).forward(request, response);
            }
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
