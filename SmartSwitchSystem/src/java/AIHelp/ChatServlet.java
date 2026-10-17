package AIHelp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dao.UserDAO;
import dto.UserDTO;
import java.io.IOException;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/** JSON endpoint used by the floating AI assistant. */
@WebServlet(name = "ChatServlet", urlPatterns = { "/chat" })
public class ChatServlet extends HttpServlet {

    private static final int MAX_QUERY_LENGTH = 500;
    private final Gson gson = new Gson();
    private AIservice aiService;

    @Override
    public void init() throws ServletException {
        aiService = new AIservice();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                "Please send an AI question using POST.");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json; charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        UserDTO user = authenticatedUser(request);
        if (user == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Your session has expired. Please sign in again.");
            return;
        }

        String question = request.getParameter("userQuery");
        if (question == null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Please enter a question.");
            return;
        }
        question = question.trim();
        if (question.isEmpty() || question.length() > MAX_QUERY_LENGTH) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "The question must contain between 1 and 500 characters.");
            return;
        }

        AIQueryPlanTemplateResolver templates = new AIQueryPlanTemplateResolver();
        String smallTalkAnswer = smallTalkAnswer(question);
        if (smallTalkAnswer != null) {
            JsonObject result = new JsonObject();
            result.addProperty("answer", smallTalkAnswer);
            response.getWriter().write(result.toString());
            return;
        }

        try (AIQueryDAO queryDao = new AIQueryDAO()) {
            if (templates.requestsOtherUserData(question)) {
                JsonObject result = new JsonObject();
                result.addProperty("answer",
                        "Vì lý do bảo mật, AI chỉ cung cấp thông tin của tài khoản đang đăng nhập, không tra cứu thông tin người dùng khác.");
                response.getWriter().write(result.toString());
                return;
            }
            if (templates.isMutationRequest(question)) {
                JsonObject result = new JsonObject();
                result.addProperty("answer",
                        "Chat AI chỉ hỗ trợ tra cứu dữ liệu. Hãy dùng màn hình quản trị tương ứng để thay đổi thông tin hoặc phân quyền.");
                response.getWriter().write(result.toString());
                return;
            }
            AIQueryPlan plan = aiService.generateQueryPlan(question,
                    user.getUserId(), user.getRole().name());
            List<Map<String, Object>> rows = queryDao.execute(plan, user.getUserId(), user.getRole());
            String answer;
            if (isCurrentUserProfilePlan(plan)) {
                answer = currentUserProfileAnswer(rows);
            } else if (isSpecificSwitchOnlinePlan(plan)) {
                answer = specificSwitchOnlineAnswer(rows);
            } else if (isOnlineSwitchCountPlan(plan)) {
                answer = onlineSwitchCountAnswer(rows);
            } else if (isOnlineSwitchListPlan(plan)) {
                answer = onlineSwitchListAnswer(rows);
            } else if (isOnlineDeviceListPlan(plan)) {
                answer = onlineDeviceListAnswer(rows);
            } else if (templates.isPermissionListQuestion(question)) {
                answer = permissionListAnswer(rows);
            } else if (templates.isPermissionCountQuestion(question)) {
                answer = permissionCountAnswer(rows);
            } else if (isScheduleCountPlan(plan)) {
                answer = scheduleCountAnswer(rows);
            } else if (templates.isSpecificPermissionQuestion(question)) {
                answer = rows.isEmpty()
                        ? "No. The database does not show an active permission for you on that switch."
                        : "Yes. The database confirms that you can control that switch.";
            } else if (rows.isEmpty()) {
                answer = emptyResultAnswer(plan);
            } else {
                answer = aiService.callGeminiWithContext(gson.toJson(rows), question,
                        user.getUserId(), user.getUserName(), user.getRole().name());
            }

            JsonObject result = new JsonObject();
            result.addProperty("answer", answer);
            response.getWriter().write(result.toString());
        } catch (IllegalArgumentException e) {
            log("AI query plan rejected", e);
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "I could not understand that request. Please ask about available Smart Switch data.");
        } catch (SQLException e) {
            log("AI query database failure", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The Smart Switch data is temporarily unavailable.");
        } catch (Exception e) {
            log("AI assistant request failed", e);
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "The AI assistant is temporarily unavailable. Please try again.");
        }
    }

    private String permissionCountAnswer(List<Map<String, Object>> rows) {
        Object value = rows.isEmpty() ? null : rows.get(0).get("total");
        int count = value instanceof Number ? ((Number) value).intValue()
                : value == null ? 0 : Integer.parseInt(value.toString());
        return "You can control " + count + " active switch" + (count == 1 ? "." : "es.");
    }

    private boolean isCurrentUserProfilePlan(AIQueryPlan plan) {
        if (plan == null || plan.getTables() == null || !plan.getTables().contains("USER")
                || plan.getSelect() == null) {
            return false;
        }
        boolean hasRole = false;
        boolean hasName = false;
        for (String expression : plan.getSelect()) {
            if (expression == null) {
                continue;
            }
            String selected = expression.toUpperCase(Locale.ROOT);
            hasRole |= selected.contains("USER.ROLE");
            hasName |= selected.contains("USER.USER_NAME") || selected.contains("USER.FULL_NAME");
        }
        return hasRole && hasName;
    }

    private String currentUserProfileAnswer(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "I could not find your profile in the database.";
        }
        Map<String, Object> profile = rows.get(0);
        String fullName = resultValue(profile, "full_name");
        String userName = resultValue(profile, "user_name");
        String role = resultValue(profile, "role");
        String name = fullName == null || fullName.trim().isEmpty() ? userName : fullName;
        if (name == null || name.trim().isEmpty()) {
            name = "not available";
        }
        if (role == null || role.trim().isEmpty()) {
            return "Your name is " + name + ".";
        }
        String usernamePart = userName == null || userName.trim().isEmpty()
                ? "" : " (username: " + userName + ")";
        return "Your name is " + name + usernamePart + ", and your role is " + role + ".";
    }

    private String resultValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private boolean isOnlineSwitchCountPlan(AIQueryPlan plan) {
        if (plan == null || plan.getTables() == null || !plan.getTables().contains("DEVICE")
                || !plan.getTables().contains("SWITCH") || plan.getSelect() == null
                || plan.getFilters() == null) {
            return false;
        }
        boolean countsSwitches = false;
        for (String expression : plan.getSelect()) {
            if (expression != null && expression.toUpperCase(Locale.ROOT)
                    .contains("COUNT(SWITCH.SWITCH_ID)")) {
                countsSwitches = true;
                break;
            }
        }
        boolean onlineDeviceFilter = false;
        for (AIFilter filter : plan.getFilters()) {
            if (filter != null && "DEVICE".equalsIgnoreCase(filter.getTable())
                    && "status".equalsIgnoreCase(filter.getColumn())
                    && "=".equals(filter.getOperator())
                    && "ONLINE".equalsIgnoreCase(filter.getValue())) {
                onlineDeviceFilter = true;
                break;
            }
        }
        return countsSwitches && onlineDeviceFilter;
    }

    private String onlineSwitchCountAnswer(List<Map<String, Object>> rows) {
        int count = countFromRows(rows);
        return "There are " + count + " active switches connected to online ESP32 devices.";
    }

    private String specificSwitchOnlineAnswer(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "I could not find an active switch matching that name.";
        }
        Map<String, Object> row = rows.get(0);
        String switchName = resultValue(row, "switch_name");
        String deviceStatus = resultValue(row, "status");
        String label = switchName == null ? "The switch" : switchName;
        if ("ONLINE".equalsIgnoreCase(deviceStatus)) {
            return "Yes. " + label + " is connected to an online ESP32 device.";
        }
        return "No. " + label + " is connected to a device with status "
                + (deviceStatus == null ? "unknown" : deviceStatus) + ".";
    }

    private String onlineSwitchListAnswer(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "No active switches are connected to online ESP32 devices.";
        }
        Set<String> switches = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String switchName = resultValue(row, "switch_name");
            String deviceName = resultValue(row, "device_name");
            String label = switchName == null ? "Unnamed switch" : switchName;
            if (deviceName != null && !deviceName.trim().isEmpty()) {
                label += " (" + deviceName + ")";
            }
            switches.add(label);
        }
        return "Switches connected to online ESP32 devices: " + String.join(", ", switches) + ".";
    }

    private String onlineDeviceListAnswer(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "No active ESP32 devices are online.";
        }
        Set<String> devices = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String name = resultValue(row, "name");
            String hostName = resultValue(row, "host_name");
            String label = name == null ? "Unnamed ESP32" : name;
            if (hostName != null && !hostName.trim().isEmpty()) {
                label += " (" + hostName + ")";
            }
            devices.add(label);
        }
        return "Online ESP32 devices: " + String.join(", ", devices) + ".";
    }

    private boolean isSpecificSwitchOnlinePlan(AIQueryPlan plan) {
        return hasSwitchAndDeviceTables(plan) && hasSelectedColumn(plan, "SWITCH.SWITCH_NAME")
                && hasSelectedColumn(plan, "DEVICE.STATUS") && hasSwitchNameFilter(plan)
                && !hasOnlineDeviceFilter(plan);
    }

    private boolean isOnlineSwitchListPlan(AIQueryPlan plan) {
        return hasSwitchAndDeviceTables(plan) && hasSelectedColumn(plan, "SWITCH.SWITCH_NAME")
                && hasOnlineDeviceFilter(plan) && !hasSwitchCountExpression(plan);
    }

    private boolean isOnlineDeviceListPlan(AIQueryPlan plan) {
        return plan != null && plan.getTables() != null && plan.getTables().contains("DEVICE")
                && !plan.getTables().contains("SWITCH") && hasOnlineDeviceFilter(plan);
    }

    private boolean hasSwitchAndDeviceTables(AIQueryPlan plan) {
        return plan != null && plan.getTables() != null
                && plan.getTables().contains("SWITCH") && plan.getTables().contains("DEVICE");
    }

    private boolean hasSelectedColumn(AIQueryPlan plan, String column) {
        if (plan == null || plan.getSelect() == null) {
            return false;
        }
        for (String expression : plan.getSelect()) {
            if (expression != null && expression.toUpperCase(Locale.ROOT).contains(column)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSwitchNameFilter(AIQueryPlan plan) {
        if (plan == null || plan.getFilters() == null) {
            return false;
        }
        for (AIFilter filter : plan.getFilters()) {
            if (filter != null && "SWITCH".equalsIgnoreCase(filter.getTable())
                    && "switch_name".equalsIgnoreCase(filter.getColumn())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasOnlineDeviceFilter(AIQueryPlan plan) {
        if (plan == null || plan.getFilters() == null) {
            return false;
        }
        for (AIFilter filter : plan.getFilters()) {
            if (filter != null && "DEVICE".equalsIgnoreCase(filter.getTable())
                    && "status".equalsIgnoreCase(filter.getColumn())
                    && "=".equals(filter.getOperator())
                    && "ONLINE".equalsIgnoreCase(filter.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSwitchCountExpression(AIQueryPlan plan) {
        if (plan == null || plan.getSelect() == null) {
            return false;
        }
        for (String expression : plan.getSelect()) {
            if (expression != null && expression.toUpperCase(Locale.ROOT)
                    .contains("COUNT(SWITCH.SWITCH_ID)")) {
                return true;
            }
        }
        return false;
    }

    private int countFromRows(List<Map<String, Object>> rows) {
        Object value = rows.isEmpty() ? null : rows.get(0).get("total");
        return value instanceof Number ? ((Number) value).intValue()
                : value == null ? 0 : Integer.parseInt(value.toString());
    }

    private String permissionListAnswer(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) {
            return "You do not have active permission to control any active switches.";
        }
        Set<String> switches = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("switch_name");
            Object gpio = row.get("gpio_pin");
            String label = name == null ? "Unnamed switch" : name.toString();
            if (gpio != null) {
                label += " (GPIO " + gpio + ")";
            }
            switches.add(label);
        }
        return "You can control these switches: " + String.join(", ", switches) + ".";
    }

    private String emptyResultAnswer(AIQueryPlan plan) {
        if (plan != null && plan.getTables() != null && plan.getTables().contains("USER")) {
            return "Vì lý do bảo mật, AI chỉ cung cấp thông tin của tài khoản đang đăng nhập.";
        }
        if (plan != null && plan.getTables() != null && plan.getTables().contains("PERMISSION")) {
            return "Không. Cơ sở dữ liệu không ghi nhận quyền điều khiển đang hoạt động cho bạn trên switch được hỏi.";
        }
        return "Không có dữ liệu phù hợp với câu hỏi của bạn trong phạm vi quyền hiện tại.";
    }

    private boolean isScheduleCountPlan(AIQueryPlan plan) {
        if (plan == null || plan.getTables() == null || !plan.getTables().contains("SCHEDULE")
                || plan.getSelect() == null) {
            return false;
        }
        for (String expression : plan.getSelect()) {
            if (expression != null && expression.trim().toUpperCase(Locale.ROOT).startsWith("COUNT(")
                    && expression.toUpperCase(Locale.ROOT).contains("SCHEDULE.")) {
                return true;
            }
        }
        return false;
    }

    private String scheduleCountAnswer(List<Map<String, Object>> rows) {
        Object value = rows.isEmpty() ? null : rows.get(0).get("total");
        int count = value instanceof Number ? ((Number) value).intValue()
                : value == null ? 0 : Integer.parseInt(value.toString());
        return "Found " + count + " schedule record" + (count == 1 ? "" : "s") + " matching your query.";
    }

    private String smallTalkAnswer(String question) {
        String text = Normalizer.normalize(question, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[!?.]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.matches("(hello|hi|hey|hello there|hi there|hey there|hello ai|hello assistant|"
                + "xin chao|chao|chao ban|chao tro ly|chao ban tro ly|good morning|good afternoon|good evening)")) {
            return "Hello! I can look up Smart Switch data, including switches, permissions, schedules, and control history.";
        }
        if (text.matches("(thanks|thank you|thank you very much|cam on|cam on ban|cam on tro ly)")) {
            return "You're welcome. Ask me about Smart Switch switches, permissions, schedules, or control history.";
        }
        if (text.matches("(help|what can you do|who are you|ban la ai|ban co the lam gi|tro ly nay lam duoc gi)")) {
            return "I'm the read-only Smart Switch assistant. I can look up switches, permissions, schedules, and control history, but I cannot change data or control hardware.";
        }
        return null;
    }

    private UserDTO authenticatedUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute("LOGIN_USER");
        if (!(value instanceof UserDTO)) {
            return null;
        }
        UserDTO sessionUser = (UserDTO) value;
        try (UserDAO users = new UserDAO()) {
            UserDTO current = users.findById(sessionUser.getUserId());
            if (current == null || !current.isActive() || current.getRole() == null) {
                session.invalidate();
                return null;
            }
            session.setAttribute("LOGIN_USER", current);
            return current;
        } catch (SQLException | ClassNotFoundException e) {
            log("AI assistant authentication check failed", e);
            return null;
        }
    }

    private void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        JsonObject error = new JsonObject();
        error.addProperty("error", message);
        response.getWriter().write(error.toString());
    }
}
