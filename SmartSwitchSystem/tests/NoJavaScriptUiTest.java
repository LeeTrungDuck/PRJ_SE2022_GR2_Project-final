import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Run against a disposable Tomcat deployment; changes only the test's own UI session. */
public final class NoJavaScriptUiTest {
    private final String base;
    private String cookie = "", csrf = "";
    private static int checks;

    private NoJavaScriptUiTest(String base) { this.base = base; }
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }
    private static Map<String,String> fields(String... pairs) {
        Map<String,String> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put(pairs[i], pairs[i + 1]);
        return values;
    }
    private static String encode(String text) throws IOException { return URLEncoder.encode(text, "UTF-8"); }
    private static final class Reply {
        int status;
        String body, location, type;
    }
    private Reply request(String path, Map<String,String> data) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(base + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(20000);
        if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
        if (data != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String,String> field : data.entrySet()) parts.add(encode(field.getKey()) + "=" + encode(field.getValue()));
            try (OutputStream out = connection.getOutputStream()) { out.write(String.join("&", parts).getBytes(StandardCharsets.UTF_8)); }
        }
        Reply reply = new Reply();
        reply.status = connection.getResponseCode();
        reply.location = connection.getHeaderField("Location");
        reply.type = connection.getContentType();
        String setCookie = connection.getHeaderField("Set-Cookie");
        if (setCookie != null) cookie = setCookie.split(";", 2)[0];
        InputStream stream = reply.status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (stream != null) try (InputStream in = stream) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) >= 0) bytes.write(buffer, 0, count);
        }
        reply.body = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        connection.disconnect();
        return reply;
    }
    private Reply page(String action, String suffix) throws IOException {
        Reply reply = request("/MainController?action=" + action + suffix, null);
        check(reply.status == 200, action + " HTTP 200: " + reply.status + " " + reply.body.substring(0, Math.min(300, reply.body.length())));
        Matcher token = Pattern.compile("name=\"csrf\" value=\"([^\"]+)\"").matcher(reply.body);
        if (token.find()) csrf = token.group(1);
        return reply;
    }
    private Reply post(String action, String operation, String... values) throws IOException {
        Map<String,String> data = fields(values);
        data.put("action", action); data.put("uiOp", operation); data.put("csrf", csrf);
        return request("/MainController", data);
    }
    private Reply success(String action, String operation, String... values) throws IOException {
        Reply reply = post(action, operation, values);
        check(reply.status == 303, operation + " redirects after POST: " + reply.status + " " + reply.body.substring(0, Math.min(500, reply.body.length())));
        String location = reply.location;
        if (location.startsWith("/SmartSwitchSystem")) location = location.substring("/SmartSwitchSystem".length());
        reply = request(location, null);
        check(reply.status == 200, operation + " renders redirected JSP");
        return reply;
    }
    private static void nativeHtml(String html, String label) {
        check(!Pattern.compile("<script\\b|\\son[a-z]+\\s*=|javascript:", Pattern.CASE_INSENSITIVE).matcher(html).find(), label + " has no JavaScript");
        String text = html.replace("&amp;", "&");
        check(text.contains("ĐIỀU KHIỂN & GIÁM SÁT") && text.contains("CẤU HÌNH & BẢO MẬT") && text.contains("HỆ THỐNG"), label + " keeps sidebar headings");
    }
    private void run() throws Exception {
        String[] actions = {"USER_MANAGER", "PERMISSION_MANAGER", "ACCOUNT_MANAGER", "DEVICE_CONTROL", "DEVICE_MANAGER", "SCHEDULE_MANAGER", "CONTROL_HISTORY"};
        for (String action : actions) nativeHtml(page(action, "").body, action);
        check(!csrf.isEmpty(), "native forms include CSRF token");
        Reply login = request("/web/login.jsp", null);
        check(login.status == 200 && !login.body.contains("<script"), "login works without script");
        check(login.body.contains("name=\"userName\"") && login.body.contains("name=\"passWord\""), "login keeps existing field contract");
        for (String action : actions) {
            String jsp = action.equals("USER_MANAGER") ? "UserManager" : action.equals("PERMISSION_MANAGER") ? "PermissionManager" : action.equals("ACCOUNT_MANAGER") ? "AccountManager" : action.equals("DEVICE_CONTROL") ? "DeviceControl" : action.equals("DEVICE_MANAGER") ? "DeviceManager" : action.equals("SCHEDULE_MANAGER") ? "ScheduleManager" : "ControlHistory";
            check(request("/web/" + jsp + ".jsp", null).status == 302, jsp + " direct access redirects through controller");
        }
        check(request("/MainController?action=DEVICE_CONTROL&uiOp=toggleRelay&id=uv-21", null).status == 405, "GET cannot mutate relay");
        check(request("/MainController", fields("action", "DEVICE_CONTROL", "uiOp", "toggleRelay", "id", "uv-21", "csrf", "wrong")).status == 403, "CSRF failure rejected");
        check(post("USER_MANAGER", "deleteUser", "id", "1").body.contains("SuperAdmin"), "root deletion blocked");
        check(post("USER_MANAGER", "changeRole", "id", "1", "role", "VIEWER").body.contains("SuperAdmin"), "root role protected");
        check(post("USER_MANAGER", "toggleUser", "id", "1").body.contains("SuperAdmin"), "root cannot be locked");
        check(post("USER_MANAGER", "addUser", "formName", "add", "fullName", "Test", "userName", "dung.tran", "email", "dup@example.com", "password", "abcdefgh", "role", "VIEWER").body.contains("Tên đăng nhập đã tồn tại"), "duplicate username validation");
        nativeHtml(page("USER_MANAGER", "&form=add").body, "user add dialog");
        Reply users = success("USER_MANAGER", "addUser", "formName", "add", "fullName", "Kiểm tra <script>alert(1)</script>", "userName", "native.test", "email", "native@example.com", "password", "abcdefgh", "role", "VIEWER");
        check(users.body.contains("native.test") && users.body.contains("&lt;script&gt;"), "new user persists and output is escaped");
        nativeHtml(users.body, "escaped user data");
        check(success("USER_MANAGER", "changeRole", "id", "7", "role", "ADMIN").body.contains("ADMIN"), "role changed");
        success("USER_MANAGER", "toggleUser", "id", "7");
        check(page("USER_MANAGER", "&p=2").body.contains("ĐÃ KHÓA"), "lock state persisted");
        success("USER_MANAGER", "resetPassword", "formName", "password", "id", "7", "password", "abcdefgh");
        success("USER_MANAGER", "deleteUser", "id", "7");
        check(!page("USER_MANAGER", "").body.contains("native.test"), "user deleted");

        success("PERMISSION_MANAGER", "savePermissions", "viewer", "viewer_khu_a", "control_sw-01-16", "1");
        Reply permissions = page("PERMISSION_MANAGER", "&viewer=viewer_khu_a");
        check(Pattern.compile("name=\"view_sw-01-16\"[^>]+checked").matcher(permissions.body).find(), "control implies view");
        success("PERMISSION_MANAGER", "grantView", "viewer", "viewer_khu_a", "control_sw-01-16", "1");
        check(page("PERMISSION_MANAGER", "&viewer=viewer_khu_a").body.contains("Đã cấp: 7/7 switch"), "grant all creates draft");
        success("PERMISSION_MANAGER", "selectViewer:viewer_kho", "viewer", "viewer_khu_a", "view_sw-02-18", "1");
        check(page("PERMISSION_MANAGER", "&viewer=viewer_khu_a").body.contains("Có bản nháp chưa lưu"), "viewer change preserves draft");
        success("PERMISSION_MANAGER", "resetPermissions", "viewer", "viewer_khu_a");
        check(page("PERMISSION_MANAGER", "&viewer=viewer_khu_a").body.contains("Không có thay đổi chưa lưu"), "revert restores saved permissions");
        nativeHtml(page("PERMISSION_MANAGER", "&viewer=viewer_khu_a&form=revoke").body, "revoke dialog");
        success("PERMISSION_MANAGER", "revokePermissions", "viewer", "viewer_khu_a");
        check(page("PERMISSION_MANAGER", "&viewer=viewer_khu_a").body.contains("Đã cấp: 0/7 switch"), "revoke draft");
        check(page("PERMISSION_MANAGER", "&q=Hoa").body.contains("Lê Thị Hoa"), "accent insensitive viewer search");

        success("ACCOUNT_MANAGER", "saveProfile", "fullName", "Tên Phiên Thử", "email", "nguyenvanan.iot@datacenter.vn", "phone", "(+84) 982.019.233");
        check(page("DEVICE_CONTROL", "").body.contains("Tên Phiên Thử"), "sidebar follows profile");
        check(success("ACCOUNT_MANAGER", "saveProfile", "fullName", "Hồ Sơ OTP", "email", "new@example.com", "phone", "0982019233").body.contains("ui-dialog-title"), "email change opens OTP form");
        check(post("ACCOUNT_MANAGER", "confirmOtp", "formName", "otp", "otp", "000000").body.contains("OTP minh họa không đúng"), "bad OTP rejected");
        check(success("ACCOUNT_MANAGER", "confirmOtp", "formName", "otp", "otp", "246810").body.contains("new@example.com"), "OTP commits pending profile");
        check(post("ACCOUNT_MANAGER", "changePassword", "currentPassword", "old", "newPassword", "Strong123!xx", "confirmPassword", "mismatch").body.contains("không khớp"), "password mismatch validation");
        success("ACCOUNT_MANAGER", "changePassword", "currentPassword", "old", "newPassword", "Strong123!xx", "confirmPassword", "Strong123!xx");
        success("ACCOUNT_MANAGER", "saveProfile", "fullName", "Hủy Email", "email", "cancel@example.com", "phone", "0982019233");
        success("ACCOUNT_MANAGER", "cancelOtp", "formName", "otp");
        check(!page("ACCOUNT_MANAGER", "").body.contains("cancel@example.com"), "cancel OTP preserves previous profile");
        String toggled = success("DEVICE_CONTROL", "toggleRelay", "id", "uv-21").body;
        check(toggled.contains("data-on=\"false\""), "relay toggles without script");
        check(page("CONTROL_HISTORY", "&result=DEMO").body.contains("MÔ PHỎNG"), "relay action produces honest demo log");

        for (String form : new String[]{"node", "switch", "switch&id=sw-1", "node&id=ESP32-CORE-A101", "switchSchedule&id=sw-1", "deleteNode&id=ESP32-CORE-A101", "deleteSwitch&id=sw-1", "rebootNode&id=ESP32-CORE-A101"}) nativeHtml(page("DEVICE_MANAGER", "&form=" + form).body, "device " + form);
        check(post("DEVICE_MANAGER", "saveNode", "formName", "node", "nodeId", "TEST", "name", "Test", "ip", "999.1.1.1", "location", "Test").body.contains("IPv4"), "bad IPv4 rejected");
        check(post("DEVICE_MANAGER", "saveNode", "formName", "node", "nodeId", "TEST", "name", "Test", "ip", "192.168.1.150", "location", "Test").body.contains("IP đã được dùng"), "duplicate IP rejected");
        success("DEVICE_MANAGER", "saveNode", "formName", "node", "nodeId", "TEST-NODE", "name", "Node Thử", "ip", "192.168.1.200", "location", "Vị trí thử", "hostname", "test.local");
        check(page("DEVICE_MANAGER", "&q=TEST-NODE&status=offline").body.contains("Node Thử"), "new node offline and searchable");
        success("DEVICE_MANAGER", "saveNode", "formName", "node", "id", "TEST-NODE", "nodeId", "IGNORED-ID", "name", "Node Đã Sửa", "ip", "192.168.1.201", "location", "Vị trí thử", "hostname", "edit.local");
        String editedNode = page("DEVICE_MANAGER", "&q=TEST-NODE").body;
        check(editedNode.contains("192.168.1.201") && editedNode.contains("Node Đã Sửa") && !editedNode.contains("IGNORED-ID"), "node edit keeps fixed ID");
        check(post("DEVICE_MANAGER", "rebootNode", "id", "TEST-NODE").body.contains("OFFLINE"), "offline reboot rejected");
        check(post("DEVICE_MANAGER", "saveSwitch", "formName", "switch", "name", "Duplicate GPIO", "nodeId", "ESP32-CORE-A101", "gpio", "16", "wiring", "NO", "type", "light", "boot", "OFF").body.contains("GPIO đã được gán"), "duplicate GPIO rejected");
        success("DEVICE_MANAGER", "saveSwitch", "formName", "switch", "name", "Switch thử", "nodeId", "TEST-NODE", "gpio", "23", "wiring", "NO", "type", "light", "boot", "OFF", "rating", "10A");
        check(post("DEVICE_MANAGER", "toggleSwitch", "id", "sw-5").body.contains("OFFLINE"), "offline switch toggle rejected");
        success("DEVICE_MANAGER", "saveSwitch", "formName", "switch", "id", "sw-5", "name", "Switch đã sửa", "nodeId", "TEST-NODE", "gpio", "25", "wiring", "NC", "type", "motor", "boot", "LAST", "rating", "20A");
        check(page("DEVICE_MANAGER", "&sq=Switch&parent=TEST-NODE&type=motor").body.contains("GPIO 25"), "switch edit and parent/type filters");
        success("DEVICE_MANAGER", "deleteSwitch", "id", "sw-5");
        check(!page("DEVICE_MANAGER", "").body.contains("Switch đã sửa"), "individual switch deletion");
        success("DEVICE_MANAGER", "saveSwitch", "formName", "switch", "name", "Switch thử", "nodeId", "TEST-NODE", "gpio", "23", "wiring", "NO", "type", "light", "boot", "OFF");
        success("DEVICE_MANAGER", "toggleSwitch", "id", "sw-1");
        success("DEVICE_MANAGER", "testSwitch", "id", "sw-1");
        success("DEVICE_MANAGER", "pingNode", "id", "ESP32-CORE-A101");
        success("DEVICE_MANAGER", "rebootNode", "id", "ESP32-CORE-A101");
        success("DEVICE_MANAGER", "saveSwitchSchedule", "id", "sw-1", "command", "ON", "runAt", "2099-01-01T06:30");
        check(page("DEVICE_MANAGER", "&form=switchSchedule&id=sw-1").body.contains("2099-01-01T06:30"), "switch schedule persists");
        success("DEVICE_MANAGER", "removeSwitchSchedule", "id", "sw-1");
        success("DEVICE_MANAGER", "deleteNode", "id", "TEST-NODE");
        check(!page("DEVICE_MANAGER", "").body.contains("Switch thử"), "node deletion cascades children");

        check(post("SCHEDULE_MANAGER", "saveSchedule", "name", "Invalid", "switchId", "esp01-g16", "time", "06:30", "command", "ON", "mode", "weekly").body.contains("ít nhất một ngày"), "weekly needs repeat days");
        Reply preset = post("SCHEDULE_MANAGER", "repeatWeekend", "name", "Giữ bản nháp", "switchId", "esp01-g16", "time", "06:30", "command", "ON", "mode", "weekly", "date", "2099-01-01");
        check(preset.status == 200 && preset.body.contains("Giữ bản nháp") && Pattern.compile("name=\"days\"[^>]+value=\"6\"[^>]+checked").matcher(preset.body).find(), "weekend preset preserves draft");
        check(post("SCHEDULE_MANAGER", "shift15", "name", "Draft", "switchId", "esp01-g16", "time", "23:50", "command", "ON", "mode", "once", "date", "2099-01-01").body.contains("2099-01-02"), "time add crosses midnight correctly");
        check(post("SCHEDULE_MANAGER", "saveSchedule", "name", "Expired", "switchId", "esp01-g16", "time", "06:30", "command", "ON", "mode", "once", "date", "2020-01-01").body.contains("tương lai"), "past once rejected");
        success("SCHEDULE_MANAGER", "saveSchedule", "name", "Lịch Java", "switchId", "esp01-g16", "time", "06:30", "command", "ON", "mode", "weekly", "days", "1", "date", "2099-01-01");
        check(page("SCHEDULE_MANAGER", "&q=Lich%20Java").body.contains("Lịch Java"), "schedule searchable and persistent");
        nativeHtml(page("SCHEDULE_MANAGER", "&edit=schedule-7").body, "schedule editing");
        success("SCHEDULE_MANAGER", "saveSchedule", "id", "schedule-7", "name", "Lịch Java Đã Sửa", "switchId", "esp03-g22", "time", "07:15", "command", "OFF", "mode", "weekly", "days", "0", "date", "2099-01-01");
        check(page("SCHEDULE_MANAGER", "&edit=schedule-7").body.contains("07:15"), "schedule edit persists");
        success("SCHEDULE_MANAGER", "toggleSchedule", "id", "schedule-7");
        check(page("SCHEDULE_MANAGER", "&q=Lich%20Java&status=paused").body.contains("Lịch Java"), "paused schedule filter");
        success("SCHEDULE_MANAGER", "runSchedule", "id", "schedule-7");
        nativeHtml(page("SCHEDULE_MANAGER", "&form=delete&id=schedule-7").body, "schedule delete dialog");
        success("SCHEDULE_MANAGER", "deleteSchedule", "id", "schedule-7");
        success("SCHEDULE_MANAGER", "saveSchedule", "name", "Một lần Java", "switchId", "esp01-g16", "time", "06:30", "command", "OFF", "mode", "once", "date", "2099-01-01");
        check(page("SCHEDULE_MANAGER", "&q=Mot%20lan%20Java").body.contains("2099-01-01"), "once date persists");

        check(page("CONTROL_HISTORY", "&q=%23LOG-89421").body.contains("#LOG-89421"), "full log identifier search");
        check(!page("CONTROL_HISTORY", "&result=success").body.contains("MÔ PHỎNG"), "demo is not real success");
        String filteredHistory = page("CONTROL_HISTORY", "&device=ESP32-03&command=ON&result=TIMEOUT&sort=asc").body;
        check(filteredHistory.contains("#LOG-89419") && !filteredHistory.contains("#LOG-89421"), "combined history filters");
        Matcher refresh = Pattern.compile("class=\"ch-refresh\" href=\"([^\"]+)\"").matcher(filteredHistory);
        check(refresh.find() && refresh.group(1).contains("device=ESP32-03") && refresh.group(1).contains("result=TIMEOUT"), "refresh preserves filters");
        Reply sorted = page("CONTROL_HISTORY", "&sort=asc");
        check(sorted.body.indexOf("#LOG-89412") < sorted.body.indexOf("#LOG-89421"), "history sort oldest first");
        nativeHtml(page("CONTROL_HISTORY", "&form=trace&id=89421").body, "trace dialog");
        Reply json = request("/MainController?action=CONTROL_HISTORY&uiOp=downloadTrace&id=89421", null);
        check(json.status == 200 && json.type.startsWith("application/json") && json.body.contains("\"event_id\": 89421"), "download trace JSON");
        check(!json.body.toLowerCase(Locale.ROOT).contains("password"), "trace has no password data");
        check(page("DEVICE_MANAGER", "&form=node&id=missing").body.contains("Không tìm thấy bản ghi"), "invalid record recovers safely");
        check(page("PERMISSION_MANAGER", "&viewer=missing").body.contains("Không tìm thấy bản ghi"), "invalid viewer recovers safely");
        NoJavaScriptUiTest second = new NoJavaScriptUiTest(base);
        check(!second.page("ACCOUNT_MANAGER", "").body.contains("Hồ Sơ OTP"), "sessions are isolated");
        success("ACCOUNT_MANAGER", "resetPreview");
        check(!page("ACCOUNT_MANAGER", "").body.contains("Hồ Sơ OTP"), "reset restores seed profile");
    }
    public static void main(String[] args) throws Exception {
        new NoJavaScriptUiTest(args.length == 0 ? "http://127.0.0.1:18080/SmartSwitchSystem" : args[0]).run();
        System.out.println("PASS: " + checks + " HTTP/JSP checks without executing JavaScript.");
    }
}
