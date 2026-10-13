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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
@WebServlet(name = "DeviceControlController", urlPatterns = { "/DeviceControlController" })
public class DeviceControlController extends HttpServlet {

    private final String FLASH_ERROR_ATTRIBUTE = "DEVICE_CONTROL_FLASH_ERROR";
    private final int PAGE_SIZE = 20;
    private final int MAX_CONCURRENT_ESP_CHECKS = 5;

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
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
                    request.setAttribute("ERROR", "LOGIN PLS!");
                }
            } else {
                url = success;
                try (SwitchDAO dao = new SwitchDAO()) {
                    // Admin/operator can view all active switches on active ESP32
                    // devices. Viewer can only view switches granted to the user.
                    List<SwitchDTO> list = user.getRole() != Role.VIEWER
                            ? dao.findAll()
                            : dao.getPermittedSwitchesForUser(user.getUserId());

                    String espFilter = request.getParameter("esp");
                    String statusFilter = request.getParameter("status");
                    espFilter = espFilter == null ? "" : espFilter.trim();
                    statusFilter = statusFilter == null ? "" : statusFilter.trim().toUpperCase(java.util.Locale.ROOT);
                    if (!statusFilter.isEmpty() && !"all".equalsIgnoreCase(statusFilter)
                            && !"ON".equals(statusFilter) && !"OFF".equals(statusFilter)) {
                        request.setAttribute("ERROR", "Invalid status filter.");
                        statusFilter = "";
                    }
                    LinkedHashSet<String> espIds = new LinkedHashSet<>();
                    for (SwitchDTO sw : list) {
                        espIds.add(sw.getDeviceId());
                    }
                    List<String> espOptions = new ArrayList<>(espIds);
                    List<SwitchDTO> filteredSwitches = new ArrayList<>();
                    for (SwitchDTO sw : list) {
                        if ((!espFilter.isEmpty() && !"all".equals(espFilter) && !espFilter.equals(sw.getDeviceId()))
                                || (!statusFilter.isEmpty() && !"all".equalsIgnoreCase(statusFilter)
                                        && !statusFilter.equals(sw.getStatus().name()))) {
                            continue;
                        }
                        filteredSwitches.add(sw);
                    }
                    list = filteredSwitches;
                    int totalSwitches = list.size();
                    int pageCount = Math.max(1, (totalSwitches + PAGE_SIZE - 1) / PAGE_SIZE);
                    int pageNumber = parsePageNumber(request.getParameter("page"));
                    pageNumber = Math.min(pageNumber, pageCount);
                    int startIndex = (pageNumber - 1) * PAGE_SIZE;
                    int endIndex = Math.min(startIndex + PAGE_SIZE, totalSwitches);
                    List<SwitchDTO> pageSwitches = list.subList(startIndex, endIndex);

                    request.setAttribute("list", new ArrayList<>(pageSwitches));
                    request.setAttribute("pageNumber", pageNumber);
                    request.setAttribute("pageCount", pageCount);
                    request.setAttribute("totalSwitches", totalSwitches);
                    request.setAttribute("firstSwitchNumber", totalSwitches == 0 ? 0 : startIndex + 1);
                    request.setAttribute("lastSwitchNumber", endIndex);
                    request.setAttribute("espOptions", espOptions);
                    request.setAttribute("espFilter", espFilter);
                    request.setAttribute("statusFilter", statusFilter);
                    request.setAttribute("switchSyncErrors", new LinkedHashMap<String, String>());
                    if (request.getAttribute("ERROR") == null) {
                        try {
                            syncSwitchStatuses(dao, pageSwitches, request);
                        } catch (HardwareException ex) {
                            log("ESP32 status synchronization failed", ex);
                            request.setAttribute("ERROR",
                                    "Unable to connect to ESP32 or read its status. Please try again.");
                        }
                    }
                }
            }
        } catch (SQLException ex) {
            log("Error at DeviceControlController (SQL): " + ex.toString());
            request.setAttribute("ERROR", "Device Controller has an error (SQL), please try again!");
        } catch (ClassNotFoundException ex) {
            log("Error at DeviceControlController (class): " + ex.toString());
            request.setAttribute("ERROR", "Device Controller has an error(Class), please try again!");
        } finally {
            request.getRequestDispatcher(url).forward(request, response);
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

    private void syncSwitchStatuses(SwitchDAO dao, List<SwitchDTO> switches,
            HttpServletRequest request) throws SQLException, HardwareException {
        Map<String, String> switchSyncErrors = new LinkedHashMap<>();
        Map<String, List<SwitchDTO>> switchesByDevice = new LinkedHashMap<>();
        for (SwitchDTO sw : switches) {
            switchesByDevice.computeIfAbsent(sw.getDeviceId(), key -> new ArrayList<>()).add(sw);
        }

        if (switchesByDevice.isEmpty()) {
            request.setAttribute("switchSyncErrors", switchSyncErrors);
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(MAX_CONCURRENT_ESP_CHECKS, switchesByDevice.size()));
        try {
            List<String> deviceIds = new ArrayList<>(switchesByDevice.keySet());
            List<Callable<Map<Integer, String>>> checks = new ArrayList<>();
            for (String deviceId : deviceIds) {
                String hostName = switchesByDevice.get(deviceId).get(0).getEspHostName();
                checks.add(() -> new HardwareClient(hostName).getSwitchStatuses());
            }

            List<Future<Map<Integer, String>>> results;
            try {
                results = executor.invokeAll(checks);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new HardwareException("ESP32 status synchronization was interrupted.", ex);
            }

            for (int i = 0; i < deviceIds.size(); i++) {
                String deviceId = deviceIds.get(i);
                List<SwitchDTO> deviceSwitches = switchesByDevice.get(deviceId);
                Map<Integer, String> statuses;
                try {
                    statuses = results.get(i).get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new HardwareException("ESP32 status result retrieval was interrupted.", ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof HardwareException) {
                        throw (HardwareException) cause;
                    }
                    throw new HardwareException("Unable to sync switches for ESP32 " + deviceId + ".",
                            cause == null ? ex : cause);
                }

                for (SwitchDTO sw : deviceSwitches) {
                    String status = statuses.get(sw.getGpioPin());
                    if (!"ON".equals(status) && !"OFF".equals(status)) {
                        throw new HardwareException("ESP32 response did not include a valid state for GPIO "
                                + sw.getGpioPin() + ".");
                    }

                    SwitchStatus actualStatus = SwitchStatus.valueOf(status);
                    SwitchStatus previousStatus = sw.getStatus();
                    sw.setStatus(actualStatus);
                    if (actualStatus != previousStatus
                            && !dao.updateStatus(sw.getSwitchId(), actualStatus)) {
                        switchSyncErrors.put(sw.getSwitchId(), "Unable to save status");
                    }
                }
            }
        } finally {
            executor.shutdownNow();
        }

        request.setAttribute("switchSyncErrors", switchSyncErrors);
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the
    // + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException      if an I/O error occurs
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
