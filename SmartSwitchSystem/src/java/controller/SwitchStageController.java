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
        try {
            response.setContentType("text/html;charset=UTF-8");
            String url = new URLMap().getUrl("DEVICE_CONTROL");
            boolean redirectAfterAction = false;
            ControlHistoryDAO hisDao = new ControlHistoryDAO();
            try {
                HttpSession session = request.getSession();
                UserDTO user = (UserDTO) session.getAttribute("LOGIN_USER");
                String switchID = request.getParameter("id");
                if (switchID == null || switchID.trim().isEmpty()) {
                    request.setAttribute("ERROR", "Switch ID không được để trống ,vui lòng thử lại!");
                    return;
                }
                if (user == null) {
                    request.setAttribute("ERROR", "bạn cần phải đăng nhập trước!");
                    url = URLMap.getLOGIN_PAGE();
                    return;
                }
                redirectAfterAction = true;
                DevicePermissionDTO permission = new DevicePermissionDAO().getUserPermission(switchID, user.getUserId());
                if (permission == null || !permission.isActive() || !permission.isCanControl()) {
                    request.setAttribute("ERROR", "Bạn không có quyền điều khiển công tắc này.");
                    return;
                }
                
                SwitchDAO switchDAO = new SwitchDAO();
                SwitchDTO sw = switchDAO.findById(switchID);
                if (sw == null) {
                    request.setAttribute("ERROR", "switch ID không có trong danh sách có thể điều khiển, kiểm tra lại Switch id hoặc trạng thái active của esp");
                    return;
                }
                
                HardwareClient hardwareClient = new HardwareClient(sw.getEspHostName());
                String currentStatus = hardwareClient.getStatus(sw.getGpioPin());
                if (!"ON".equals(currentStatus) && !"OFF".equals(currentStatus)) {
                    request.setAttribute("ERROR", "Không đọc được trạng thái hiện tại của switch.");
                    return;
                }
                
                SwitchStatus status = "ON".equals(currentStatus) ? SwitchStatus.OFF : SwitchStatus.ON;
                boolean commandSucceeded = status == SwitchStatus.ON
                        ? hardwareClient.turnOn(sw.getGpioPin())
                        : hardwareClient.turnOff(sw.getGpioPin());
                ControlHistoryDTO hisDto = new ControlHistoryDTO();
                
                hisDto.setSwitchId(switchID);
                hisDto.setUserId(user.getUserId());
                hisDto.setCommand(status == SwitchStatus.ON ? Command.ON : Command.OFF);
                hisDto.setControlTime(hisDto.getTimeNow());
                if (!commandSucceeded) {
                    request.setAttribute("ERROR", "ESP32 không xác nhận lệnh bật/tắt switch.");
                    hisDto.setResult(ControlResult.ERROR);
                    hisDao.insert(hisDto);
                    return;
                } else {
                    hisDto.setResult(status == SwitchStatus.ON ? ControlResult.ON : ControlResult.OFF);
                }
                if (!switchDAO.updateStatus(switchID, status)) {
                    request.setAttribute("ERROR", "Thiết bị đã đổi trạng thái nhưng không thể lưu trạng thái vào hệ thống.");
                }
                if (!hisDao.insert(hisDto)) {
                    log("Error at SwitchStageController: History control cannot insert into database");
                    request.setAttribute("ERROR", "save fail, try again!");
                }
            } catch (SQLException ex) {
                log("Error at LoginController: " + ex.toString());
                request.setAttribute("ERROR", "Database connect error, please try again!");
            } catch (ClassNotFoundException ex) {
                Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
                request.setAttribute("ERROR", "Class not found, please try again!");
            } catch (HardwareException ex) {
                Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
                request.setAttribute("ERROR", "Không thể kết nối hoặc giao tiếp với ESP32: "
                        + ex.getMessage());
            } finally {
                if (redirectAfterAction) {
                    HttpSession session = request.getSession();
                    Object error = request.getAttribute("ERROR");
                    if (error != null) {
                        session.setAttribute(FLASH_ERROR_ATTRIBUTE, error);
                    } else {
                        session.removeAttribute(FLASH_ERROR_ATTRIBUTE);
                    }
                    response.sendRedirect(response.encodeRedirectURL(
                            request.getContextPath() + url
                    ));
                } else {
                    request.getRequestDispatcher(url).forward(request, response);
                }
                
            }
        } catch (SQLException ex) {
            Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(SwitchStageController.class.getName()).log(Level.SEVERE, null, ex);
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
