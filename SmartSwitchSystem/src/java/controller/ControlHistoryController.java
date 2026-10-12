package controller;

import dao.ControlHistoryDAO;
import dao.UserDAO;
import dto.ControlHistoryDTO;
import dto.UserDTO;
import enums.Role;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import static controller.ManagementController.*;

public class ControlHistoryController extends HttpServlet {

    private static final String PAGE = "CONTROL_HISTORY";

    private static final String JSP = "/web/ControlHistory.jsp";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        HttpSession session = request.getSession(false);
        UserDTO login = session == null ? null : (UserDTO) session.getAttribute("LOGIN_USER");
        if (login == null) {
            response.sendRedirect(request.getContextPath() + "/web/login.jsp");
            return;
        }

        try (UserDAO users = new UserDAO()) {
            UserDTO actor = users.findById(login.getUserId());
            if (actor == null || !actor.isActive()) {
                session.invalidate();
                response.sendRedirect(request.getContextPath() + "/web/login.jsp");
                return;
            }
            session.setAttribute("LOGIN_USER", actor);
            Map<String, Object> model;
            try {
                model = view(users, actor, request);
            } catch (IllegalArgumentException e) {
                log("Control history filter rejected", e);
                request.setAttribute("uiError", e.getMessage());
                model = view(users, actor, withoutParameters(request));
            }
            for (Map.Entry<String, Object> entry : model.entrySet()) {
                request.setAttribute(entry.getKey(), entry.getValue());
            }
            request.setAttribute("activePage", PAGE);
            request.getRequestDispatcher(JSP).forward(request, response);
        } catch (SQLException | ClassNotFoundException e) {
            log("Control history database access failed", e);
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            request.setAttribute("ERROR", "Không đọc được lịch sử điều khiển. Vui lòng thử lại.");
            request.getRequestDispatcher("/web/errorPage.jsp").forward(request, response);
        } catch (RuntimeException e) {
            log("Unexpected control history error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            request.setAttribute("ERROR", "Không thể xem lịch sử điều khiển. Vui lòng thử lại.");
            request.getRequestDispatcher("/web/errorPage.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Lịch sử chỉ hỗ trợ tra cứu bằng GET.");
    }

    private HttpServletRequest withoutParameters(HttpServletRequest request) {
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                return null;
            }
        };
    }

    private Map<String, Object> view(UserDAO users, UserDTO actor, HttpServletRequest request)
            throws SQLException, ClassNotFoundException {
        String query = input(request, "q", 100).toLowerCase(Locale.ROOT);
        String switchFilter = input(request, "switchId", 20);
        String commandFilter = input(request, "command", 10);
        String resultFilter = input(request, "result", 10);
        require(commandFilter.isEmpty() || "all".equals(commandFilter)
                || "ON".equals(commandFilter) || "OFF".equals(commandFilter),
                "Lệnh lọc không hợp lệ.");
        require(resultFilter.isEmpty() || "all".equals(resultFilter)
                || "ON".equals(resultFilter) || "OFF".equals(resultFilter) || "ERROR".equals(resultFilter),
                "Kết quả lọc không hợp lệ.");

        List<ControlHistoryDTO> records;
        try (ControlHistoryDAO dao = new ControlHistoryDAO()) {
            records = actor.getRole() == Role.VIEWER
                    ? dao.findByUserId(actor.getUserId()) : dao.findAll();
        }
        List<ControlHistoryDTO> filtered = new ArrayList<>();
        for (ControlHistoryDTO record : records) {
            if (!switchFilter.isEmpty() && !"all".equals(switchFilter)
                    && !switchFilter.equals(record.getSwitchId())) {
                continue;
            }
            if (!commandFilter.isEmpty() && !"all".equals(commandFilter)
                    && !commandFilter.equals(record.getCommand().name())) {
                continue;
            }
            if (!resultFilter.isEmpty() && !"all".equals(resultFilter)
                    && !resultFilter.equals(record.getResult().name())) {
                continue;
            }
            String text = (record.getHistoryId() + " " + record.getUserId() + " "
                    + record.getSwitchId() + " " + record.getCommand() + " "
                    + record.getResult() + " " + record.getControlTime()).toLowerCase(Locale.ROOT);
            if (query.isEmpty() || text.contains(query)) {
                filtered.add(record);
            }
        }
        return row("history", filtered, "q", query, "switchId", switchFilter,
                "command", commandFilter, "result", resultFilter);
    }

}
