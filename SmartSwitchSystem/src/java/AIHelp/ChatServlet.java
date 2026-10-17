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
        String builtInAnswer = builtInAnswer(question);
        if (builtInAnswer != null) {
            JsonObject result = new JsonObject();
            result.addProperty("answer", builtInAnswer);
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
                answer = currentUserProfileAnswer(rows, question);
            } else if (isSpecificSwitchOnlinePlan(plan)) {
                answer = specificSwitchOnlineAnswer(rows);
            } else if (isOnlineSwitchCountPlan(plan)) {
                answer = onlineSwitchCountAnswer(rows);
            } else if (isOnlineSwitchListPlan(plan)) {
                answer = onlineSwitchListAnswer(rows);
            } else if (isOnlineDeviceListPlan(plan)) {
                answer = onlineDeviceListAnswer(rows);
            } else if (templates.isPermissionListQuestion(question)) {
                answer = permissionListAnswer(rows, question);
            } else if (templates.isPermissionCountQuestion(question)) {
                answer = permissionCountAnswer(rows, question);
            } else if (isScheduleCountPlan(plan)) {
                answer = scheduleCountAnswer(rows, question);
            } else if (templates.isScheduleTodayQuestion(question)) {
                answer = scheduleListAnswer(rows, question);
            } else if (templates.isSpecificPermissionQuestion(question)) {
                answer = isVietnameseQuestion(question)
                        ? rows.isEmpty()
                                ? "Cơ sở dữ liệu không ghi nhận quyền điều khiển đang hoạt động của bạn trên switch đó."
                                : "Có. Cơ sở dữ liệu xác nhận bạn có quyền điều khiển switch đó."
                        : rows.isEmpty()
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
                    isVietnameseQuestion(question)
                            ? "Tôi chưa hiểu yêu cầu. Hãy hỏi về dữ liệu Smart Switch mà bạn được phép xem."
                            : "I could not understand that request. Please ask about available Smart Switch data.");
        } catch (SQLException e) {
            log("AI query database failure", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    isVietnameseQuestion(question)
                            ? "Dữ liệu Smart Switch tạm thời không khả dụng. Vui lòng thử lại sau."
                            : "Smart Switch data is temporarily unavailable. Please try again later.");
        } catch (GeminiClient.ConfigurationException e) {
            log("AI assistant configuration failure", e);
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    isVietnameseQuestion(question)
                            ? "Dịch vụ AI chưa được cấu hình. Vui lòng liên hệ quản trị viên."
                            : "The AI service is not configured. Please contact an administrator.");
        } catch (Exception e) {
            log("AI assistant request failed", e);
            writeError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    isVietnameseQuestion(question)
                            ? "Trợ lý AI tạm thời không phản hồi. Vui lòng thử lại hoặc báo quản trị viên nếu lỗi tiếp diễn."
                            : "The AI assistant is temporarily unavailable. Please retry or contact an administrator if the problem continues.");
        }
    }

    private String permissionCountAnswer(List<Map<String, Object>> rows, String question) {
        Object value = rows.isEmpty() ? null : rows.get(0).get("total");
        int count = value instanceof Number ? ((Number) value).intValue()
                : value == null ? 0 : Integer.parseInt(value.toString());
        return isVietnameseQuestion(question)
                ? "Theo dữ liệu hiện tại, bạn có quyền điều khiển " + count + " switch đang hoạt động."
                : "You can control " + count + " active switch" + (count == 1 ? "." : "es.");
    }

    private boolean isCurrentUserProfilePlan(AIQueryPlan plan) {
        if (plan == null || plan.getTables() == null || plan.getTables().size() != 1
                || !plan.getTables().contains("USER")
                || plan.getSelect() == null) {
            return false;
        }
        for (String expression : plan.getSelect()) {
            if (expression != null && expression.toUpperCase(Locale.ROOT).startsWith("USER.")) {
                return true;
            }
        }
        return false;
    }

    private String currentUserProfileAnswer(List<Map<String, Object>> rows, String question) {
        if (rows.isEmpty()) {
            return isVietnameseQuestion(question)
                    ? "Không tìm thấy hồ sơ của bạn trong cơ sở dữ liệu."
                    : "I could not find your profile in the database.";
        }
        Map<String, Object> profile = rows.get(0);
        String userId = resultValue(profile, "user_id");
        String fullName = resultValue(profile, "full_name");
        String userName = resultValue(profile, "user_name");
        String role = resultValue(profile, "role");
        String email = resultValue(profile, "email");
        String phone = resultValue(profile, "phone_number");
        String normalizedQuestion = normalizeQuestion(question);
        boolean asksForId = normalizedQuestion.contains("user id")
                || normalizedQuestion.contains("user_id");
        boolean asksForEmail = normalizedQuestion.contains("email")
                || normalizedQuestion.contains("emal") || normalizedQuestion.contains("mail");
        boolean asksForPhone = normalizedQuestion.contains("sdt")
                || normalizedQuestion.contains("phone")
                || normalizedQuestion.contains("so dien thoai");
        boolean asksForAccount = normalizedQuestion.contains("tai khoan")
                || normalizedQuestion.contains("account");
        boolean asksForUsername = normalizedQuestion.contains("username")
                || normalizedQuestion.contains("user name")
                || normalizedQuestion.contains("ten dang nhap");
        boolean asksForRole = normalizedQuestion.contains("vai tro")
                || normalizedQuestion.contains("role");
        boolean asksForActive = normalizedQuestion.contains("hoat dong")
                || normalizedQuestion.contains("active");

        if (isVietnameseQuestion(question)) {
            List<String> details = new java.util.ArrayList<>();
            if (asksForId) {
                details.add("User ID: " + valueOrUnavailable(userId, true));
            }
            if (asksForEmail) {
                details.add("email: " + valueOrUnavailable(email, true));
            }
            if (asksForPhone) {
                details.add("số điện thoại: " + valueOrUnavailable(phone, true));
            }
            if (!details.isEmpty()) {
                return "Thông tin tài khoản của bạn — " + String.join(", ", details) + ".";
            }
            if (asksForActive) {
                String active = resultValue(profile, "is_active");
                return active == null
                        ? "Trạng thái hoạt động của tài khoản chưa có trong dữ liệu."
                        : ("true".equalsIgnoreCase(active) || "1".equals(active))
                                ? "Tài khoản của bạn đang hoạt động."
                                : "Tài khoản của bạn hiện đã bị vô hiệu hóa.";
            }
            if (asksForAccount) {
                return "Tài khoản của bạn — User ID: " + valueOrUnavailable(userId, true)
                        + ", tên đăng nhập: " + valueOrUnavailable(userName, true) + ".";
            }
            if (asksForUsername) {
                return "Tên đăng nhập của bạn: " + valueOrUnavailable(userName, true) + ".";
            }
            if (asksForRole) {
                return "Vai trò của bạn: " + valueOrUnavailable(role, true) + ".";
            }
            String name = fullName == null || fullName.trim().isEmpty() ? userName : fullName;
            if (name == null || name.trim().isEmpty()) {
                name = "chưa có";
            }
            String usernamePart = userName == null || userName.trim().isEmpty()
                    ? "" : " (tên đăng nhập: " + userName + ")";
            return role == null || role.trim().isEmpty()
                    ? "Tên của bạn là " + name + usernamePart + "."
                    : "Tên của bạn là " + name + usernamePart + "; vai trò: " + role + ".";
        }

        String name = fullName == null || fullName.trim().isEmpty() ? userName : fullName;
        if (name == null || name.trim().isEmpty()) {
            name = "not available";
        }
        if (asksForId) {
            return "Your user ID is " + valueOrUnavailable(userId, false) + ".";
        }
        if (asksForEmail || asksForPhone) {
            List<String> details = new java.util.ArrayList<>();
            if (asksForEmail) {
                details.add("email: " + valueOrUnavailable(email, false));
            }
            if (asksForPhone) {
                details.add("phone: " + valueOrUnavailable(phone, false));
            }
            return "Your account details — " + String.join(", ", details) + ".";
        }
        if (asksForActive) {
            String active = resultValue(profile, "is_active");
            return active == null ? "Your account status is not available."
                    : ("true".equalsIgnoreCase(active) || "1".equals(active))
                            ? "Your account is active."
                            : "Your account is inactive.";
        }
        if (asksForAccount) {
            return "Your account — user ID: " + valueOrUnavailable(userId, false)
                    + ", username: " + valueOrUnavailable(userName, false) + ".";
        }
        if (asksForUsername) {
            return "Your username is " + valueOrUnavailable(userName, false) + ".";
        }
        if (asksForRole) {
            return "Your role is " + valueOrUnavailable(role, false) + ".";
        }
        if (role == null || role.trim().isEmpty()) {
            return "Your name is " + name + ".";
        }
        String usernamePart = userName == null || userName.trim().isEmpty()
                ? "" : " (username: " + userName + ")";
        return "Your name is " + name + usernamePart + ", and your role is " + role + ".";
    }

    private String valueOrUnavailable(String value, boolean vietnamese) {
        return value == null || value.trim().isEmpty()
                ? vietnamese ? "chưa cập nhật" : "not available"
                : value;
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

    private String permissionListAnswer(List<Map<String, Object>> rows, String question) {
        boolean vietnamese = isVietnameseQuestion(question);
        if (rows.isEmpty()) {
            return vietnamese
                    ? "Bạn chưa được cấp quyền điều khiển switch đang hoạt động nào."
                    : "You do not have active permission to control any active switches.";
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
        return vietnamese
                ? "Bạn có thể điều khiển các switch sau: " + String.join(", ", switches) + "."
                : "You can control these switches: " + String.join(", ", switches) + ".";
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

    private String scheduleCountAnswer(List<Map<String, Object>> rows, String question) {
        Object value = rows.isEmpty() ? null : rows.get(0).get("total");
        int count = value instanceof Number ? ((Number) value).intValue()
                : value == null ? 0 : Integer.parseInt(value.toString());
        return isVietnameseQuestion(question)
                ? "Tìm thấy " + count + " lịch đang hoạt động phù hợp với switch được hỏi."
                : "Found " + count + " schedule record" + (count == 1 ? "" : "s") + " matching your query.";
    }

    String builtInAnswer(String question) {
        String text = normalizeQuestion(question);
        boolean vietnamese = isVietnameseQuestion(question);
        if (text.matches("(hello|hi|hey|hello there|hi there|hey there|hello ai|hello assistant|"
                + "xin chao|chao|chao ban|chao tro ly|chao ban tro ly|good morning|good afternoon|good evening)")) {
            return vietnamese
                    ? "Chào bạn! Tôi có thể tra cứu switch, quyền điều khiển, lịch hẹn giờ và lịch sử điều khiển trong Smart Switch."
                    : "Hello! I can look up Smart Switch data, including switches, permissions, schedules, and control history.";
        }
        if (text.matches("(thanks|thank you|thank you very much|cam on|cam on ban|cam on tro ly)")) {
            return vietnamese
                    ? "Rất vui được hỗ trợ. Bạn có thể hỏi về switch, quyền điều khiển, lịch hẹn giờ hoặc lịch sử điều khiển."
                    : "You're welcome. Ask me about Smart Switch switches, permissions, schedules, or control history.";
        }
        if ((text.contains("trang web") || text.contains("website") || text.contains("this site"))
                && (text.contains("gi") || text.contains("what") || text.contains("la gi"))) {
            return vietnamese
                    ? "Đây là hệ thống Smart Switch dùng để theo dõi và điều khiển các switch/đèn được cấp quyền, quản lý lịch hẹn giờ và xem lịch sử. Chat AI chỉ tra cứu dữ liệu, không tự bật/tắt thiết bị."
                    : "This is the Smart Switch system for viewing and controlling permitted switches, managing schedules, and checking control history. The AI chat is read-only and cannot turn devices on or off.";
        }
        if (text.contains("tao tai khoan") || text.contains("dang ky")
                || text.contains("create account") || text.contains("sign up")) {
            return vietnamese
                    ? "Hệ thống không có chức năng tự đăng ký. Bạn cần nhờ quản trị viên tạo tài khoản trong mục Quản lý người dùng; sau đó đăng nhập bằng thông tin được cấp."
                    : "Self-registration is not available. Ask an administrator to create your account in User Management, then sign in with the credentials they provide.";
        }
        if (text.contains("hen gio nhu the nao") || text.contains("lam sao de hen gio")
                || text.contains("how to schedule") || text.contains("how do i schedule")
                || text.contains("create schedule") || text.contains("tao lich")
                || text.contains("lap lich")) {
            return vietnamese
                    ? "Mở mục Lịch Hẹn Giờ, chọn switch, chọn lệnh ON hoặc OFF, đặt giờ chạy và bật Enabled, rồi nhấn Add Schedule. Lịch lưu giờ trong ngày chứ không lưu một ngày cụ thể; AI chỉ hướng dẫn, không tạo lịch thay bạn."
                    : "Open Schedules, choose a switch, select ON or OFF, set the run time, enable the schedule, and select Add Schedule. Schedules store a time of day rather than a specific date; chat can explain but cannot create one for you.";
        }
        if (text.matches("(help|what can you do|who are you|ban la ai|ban co the lam gi|tro ly nay lam duoc gi)")) {
            return vietnamese
                    ? "Tôi là trợ lý chỉ đọc của Smart Switch. Tôi có thể tra cứu switch, quyền, lịch hẹn giờ và lịch sử; tôi không thể thay đổi dữ liệu hoặc điều khiển phần cứng."
                    : "I'm the read-only Smart Switch assistant. I can look up switches, permissions, schedules, and control history, but I cannot change data or control hardware.";
        }
        return null;
    }

    private String scheduleListAnswer(List<Map<String, Object>> rows, String question) {
        boolean vietnamese = isVietnameseQuestion(question);
        if (rows.isEmpty()) {
            return vietnamese
                    ? "Không có lịch nào đang hoạt động và được bật. Cơ sở dữ liệu lưu giờ trong ngày, không lưu ngày riêng để lọc chính xác lịch chỉ của hôm nay."
                    : "There are no active, enabled schedules. The database stores a time of day, not a specific date, so it cannot filter schedules for today only.";
        }
        List<String> schedules = new java.util.ArrayList<>();
        for (Map<String, Object> row : rows) {
            String switchName = resultValue(row, "switch_name");
            String action = resultValue(row, "action");
            String runTime = resultValue(row, "run_time");
            schedules.add((switchName == null ? "Switch" : switchName) + ": "
                    + (action == null ? "?" : action) + " @ "
                    + (runTime == null ? "unknown time" : runTime));
        }
        String list = String.join("; ", schedules);
        return vietnamese
                ? "Các lịch đang bật: " + list
                        + ". Lưu ý: dữ liệu chỉ lưu giờ trong ngày, không lưu ngày riêng, nên không thể xác định lịch chỉ dành cho hôm nay."
                : "Enabled schedules: " + list
                        + ". Note: the data stores a time of day, not a specific date, so it cannot identify schedules for today only.";
    }

    private boolean isVietnameseQuestion(String question) {
        if (question == null) {
            return false;
        }
        String withoutDiacritics = Normalizer.normalize(question, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
        if (!question.equals(withoutDiacritics)) {
            return true;
        }
        String text = normalizeQuestion(question);
        return text.contains("toi") || text.contains("cua") || text.contains("lich")
                || text.contains("hen gio") || text.contains("bao nhieu")
                || text.contains("tai khoan") || text.contains("sdt")
                || text.contains("huong dan") || text.contains("la gi")
                || text.contains("hom nay") || text.contains("switch nao")
                || text.contains("vai tro") || text.contains("ten dang nhap")
                || text.contains("trang web") || text.contains("chao");
    }

    private String normalizeQuestion(String question) {
        return Normalizer.normalize(question == null ? "" : question, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9_ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
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
