package ui;

import java.net.URLEncoder;
import java.time.*;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import static ui.UiSupport.*;

/**
 * Form actions for the same UI demonstration previously implemented in
 * JavaScript.
 */
public final class PreviewActions {

    private PreviewActions() {
    }

    public static String handle(String page, PreviewState s, HttpServletRequest r) {
        String op = input(r, "uiOp", 100);
        if ("USER_MANAGER".equals(page)) {
            return user(s, r, op);
        }
        if ("PERMISSION_MANAGER".equals(page)) {
            return permission(s, r, op);
        }
        if ("ACCOUNT_MANAGER".equals(page)) {
            return account(s, r, op);
        }
        if ("DEVICE_CONTROL".equals(page)) {
            require("toggleRelay".equals(op), "Thao tác không hợp lệ.");
            Map<String, Object> item = find(s.relays, "id", param(r, "id"));
            item.put("on", !yes(item.get("on")));
            s.log(str(item.get("node")), str(item.get("name")), (Integer) item.get("gpio"), yes(item.get("on")) ? "ON" : "OFF");
            return done(r, "Đã đổi trạng thái minh họa. Chưa gửi lệnh ESP32.", "");
        }
        if ("DEVICE_MANAGER".equals(page)) {
            return device(s, r, op);
        }
        if ("SCHEDULE_MANAGER".equals(page)) {
            return schedule(s, r, op);
        }
        throw new IllegalArgumentException("Trang này chỉ dùng để xem dữ liệu.");
    }

    private static String done(HttpServletRequest r, String message, String query) {
        r.setAttribute("uiMessage", message);
        return query;
    }

    private static String encoded(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String user(PreviewState s, HttpServletRequest r, String op) {
        if ("addUser".equals(op)) {
            require(s.users.size() < 100, "Bản xem UI giới hạn 100 tài khoản.");
            String name = required(r, "fullName", 100, "họ tên"), username = required(r, "userName", 64, "tên đăng nhập"), email = email(r), role = choice(r, "role",
                    "ADMIN", "OPERATION", "VIEWER");
            require(username.matches("[A-Za-z0-9_.-]{3,64}"), "Tên đăng nhập cần 3–64 ký tự chữ, số, dấu chấm, gạch ngang hoặc gạch dưới.");
            password(r, "password", 8, false);
            for (Map<String, Object> user : s.users) {
                require(!str(user.get("userName")).equalsIgnoreCase(username), "Tên đăng nhập đã tồn tại.");
                require(!str(user.get("email")).equalsIgnoreCase(email), "Email đã tồn tại.");
            }
            s.users.add(row("id", s.nextUser++, "fullName", name, "userName", username, "email", email, "role", role, "active", true, "createdAt", LocalDate.now(ZONE).toString(),
                    "superAdmin", false));
            return done(r, "Đã tạo tài khoản minh họa.", "&p=" + ((s.users.size() + 5) / 6));
        }
        Map<String, Object> user = find(s.users, "id", param(r, "id"));
        require(!yes(user.get("superAdmin")), "Tài khoản SuperAdmin được bảo vệ.");
        if ("changeRole".equals(op)) {
            user.put("role", choice(r, "role", "ADMIN", "OPERATION", "VIEWER"));
        } else if ("toggleUser".equals(op)) {
            user.put("active", !yes(user.get("active")));
        } else if ("deleteUser".equals(op)) {
            s.users.remove(user);
        } else if ("resetPassword".equals(op)) {
            password(r, "password", 8, false);
        } else {
            throw new IllegalArgumentException("Thao tác tài khoản không hợp lệ.");
        }
        return done(r, "Đã xử lý thay đổi trong bản xem UI; chưa cập nhật database.", "");
    }

    private static String permission(PreviewState s, HttpServletRequest r, String op) {
        String id = param(r, "viewer");
        find(s.viewers, "id", id);
        Map<String, Object> draft = copyMap(map(s.draftPermissions.get(id)));
        if (!"resetPermissions".equals(op) && !"revokePermissions".equals(op)) {
            for (String key : draft.keySet()) {
                boolean control = r.getParameter("control_" + key) != null, view = r.getParameter("view_" + key) != null;
                draft.put(key, row("canView", view || control, "canControl", control));
            }
        }
        if ("savePermissions".equals(op)) {
            s.savedPermissions.put(id, copy(draft));
        } else if ("grantView".equals(op)) {
            for (Object grant : draft.values()) {
                map(grant).put("canView", true);
            }
        } else if ("resetPermissions".equals(op)) {
            draft = copyMap(map(s.savedPermissions.get(id)));
        } else if ("revokePermissions".equals(op)) {
            for (Object grant : draft.values()) {
                map(grant).put("canView", false);
                map(grant).put("canControl", false);
            }
        } else if (op.startsWith("selectViewer:")) {
            String target = op.substring(13);
            find(s.viewers, "id", target);
            s.draftPermissions.put(id, draft);
            return done(r, "Đã giữ bản nháp phân quyền.", "&viewer=" + encoded(target));
        } else if (!"confirmRevoke".equals(op)) {
            throw new IllegalArgumentException("Thao tác phân quyền không hợp lệ.");
        }
        s.draftPermissions.put(id, draft);
        return done(r, "savePermissions".equals(op) ? "Đã lưu quyền minh họa trong session." : "resetPermissions".equals(op) ? "Đã khôi phục quyền đã lưu." : "Đã cập nhật bản nháp. Bấm Lưu Phân Quyền để áp dụng.",
                "&viewer=" + encoded(id) + ("confirmRevoke".equals(op) ? "&form=revoke" : ""));
    }

    private static String account(PreviewState s, HttpServletRequest r, String op) {
        if ("saveProfile".equals(op)) {
            String name = required(r, "fullName", 100, "họ tên"), email = email(r), phone = required(r, "phone", 30, "số điện thoại"), digits = phone.replaceAll("\\D",
                    "");
            require(phone.matches("[+\\d\\s().-]+") && digits.length() >= 9 && digits.length() <= 15, "Số điện thoại cần từ 9 đến 15 chữ số.");
            Map<String, Object> profile = row("fullName", name, "email", email, "phone", phone);
            if (!email.equals(s.profile.get("email"))) {
                s.pendingProfile = profile;
                return done(r, "Nhập OTP minh họa 246810 để xác nhận email.", "&form=otp");
            }
            s.profile.putAll(profile);
            s.pendingProfile = null;
            return done(r, "Đã lưu hồ sơ minh họa trong session.", "");
        }
        if ("confirmOtp".equals(op)) {
            require(s.pendingProfile != null, "Không có email chờ xác nhận.");
            require("246810".equals(param(r, "otp")), "OTP minh họa không đúng. Dùng mã 246810.");
            s.profile.putAll(s.pendingProfile);
            s.pendingProfile = null;
            return done(r, "Đã xác nhận email và lưu hồ sơ minh họa.", "");
        }
        if ("cancelOtp".equals(op)) {
            s.pendingProfile = null;
            return done(r, "Đã hủy thay đổi email.", "");
        }
        require("changePassword".equals(op), "Thao tác hồ sơ không hợp lệ.");
        password(r, "currentPassword", 1, false);
        password(r, "newPassword", 10, true);
        require(Objects.equals(r.getParameter("newPassword"), r.getParameter("confirmPassword")), "Mật khẩu xác nhận không khớp.");
        require(!Objects.equals(r.getParameter("currentPassword"), r.getParameter("newPassword")), "Mật khẩu mới cần khác mật khẩu hiện tại.");
        return done(r, "Mật khẩu đáp ứng yêu cầu. Bản minh họa không lưu mật khẩu hoặc đổi thông tin đăng nhập thật.", "");
    }

    private static Map<String, Object> node(PreviewState s, HttpServletRequest r) {
        return find(s.nodes, "id", param(r, "id"));
    }

    private static Map<String, Object> relay(PreviewState s, HttpServletRequest r) {
        return find(s.switches, "key", param(r, "id"));
    }

    private static String device(PreviewState s, HttpServletRequest r, String op) {
        if ("saveNode".equals(op)) {
            String editing = param(r, "id"), id = editing.isEmpty() ? required(r, "nodeId", 64, "Device ID") : str(node(s, r).get("id"));
            require(id.matches("[A-Za-z0-9_-]{1,64}"), "Device ID chỉ chứa chữ, số, gạch ngang hoặc gạch dưới.");
            String name = required(r, "name", 100, "tên thiết bị"), ip = ipv4(required(r, "ip", 15, "IPv4")), location = required(r, "location", 100, "vị trí"),
                    host = input(r, "hostname", 253);
            require(host.isEmpty() || host.matches("[A-Za-z0-9.-]+"), "Hostname không hợp lệ.");
            for (Map<String, Object> item : s.nodes) {
                if (!str(item.get("id")).equals(editing)) {
                    require(!str(item.get("id")).equalsIgnoreCase(id), "Device ID đã tồn tại.");
                    require(!ip.equals(item.get("ip")), "IP đã được dùng bởi ESP32 khác.");
                }
            }
            Map<String, Object> item;
            if (editing.isEmpty()) {
                require(s.nodes.size() < 100, "Bản xem UI giới hạn 100 thiết bị.");
                item = row("id", id, "online", false, "latency", null, "offlineReason", "Chưa ghép nối");
                s.nodes.add(item);
            } else {
                item = node(s, r);
            }
            item.putAll(row("name", name, "ip", ip, "location", location, "hostname", host));
            return done(r, "Đã lưu thiết bị minh họa. Thiết bị mới mặc định OFFLINE.", "");
        }
        if ("deleteNode".equals(op)) {
            Map<String, Object> item = node(s, r);
            String id = str(item.get("id"));
            s.switches.removeIf(sw -> id.equals(sw.get("nodeId")));
            s.nodes.remove(item);
            return done(r, "Đã xóa thiết bị và các switch con trong session.", "");
        }
        if ("rebootNode".equals(op)) {
            Map<String, Object> item = node(s, r);
            require(yes(item.get("online")), "Không thể reboot thiết bị OFFLINE.");
            for (Map<String, Object> sw : s.switches) {
                if (item.get("id").equals(sw.get("nodeId")) && !"LAST".equals(sw.get("boot"))) {
                    sw.put("on", "ON".equals(sw.get("boot")));
                }
            }
            return done(r, "Đã mô phỏng reboot theo trạng thái khởi động; chưa gửi lệnh thiết bị.", "");
        }
        if ("pingNode".equals(op)) {
            Map<String, Object> item = node(s, r);
            return done(r, yes(item.get("online")) ? "Trạng thái mẫu: ONLINE (" + item.get("latency") + "ms). Không ping thiết bị thật." : "Trạng thái mẫu: OFFLINE. Không ping thiết bị thật.",
                    "");
        }
        if ("saveSwitch".equals(op)) {
            String editing = param(r, "id"), parent = required(r, "nodeId", 64, "ESP32 cha");
            find(s.nodes, "id", parent);
            int gpio = number(param(r, "gpio").replaceFirst("(?i)^GPIO\\s*", ""), 0, 39, "GPIO");
            String name = required(r, "name", 100, "tên switch"), wiring = choice(r, "wiring", "NO", "NC"), type = choice(r, "type", "light", "fan", "motor", "other"),
                    boot = choice(r, "boot", "OFF", "ON", "LAST"), rating = input(r, "rating", 100);
            for (Map<String, Object> sw : s.switches) {
                if (!str(sw.get("key")).equals(editing)) {
                    require(!(parent.equals(sw.get("nodeId")) && Objects.equals(gpio,
                            sw.get("gpio"))), "GPIO đã được gán trên ESP32 này.");
                }
            }
            Map<String, Object> item;
            if (editing.isEmpty()) {
                require(s.switches.size() < 200, "Bản xem UI giới hạn 200 switch.");
                item = row("key", "sw-" + s.nextSwitch++, "on", false, "schedule", null);
                s.switches.add(item);
            } else {
                item = relay(s, r);
            }
            item.putAll(row("name", name, "nodeId", parent, "gpio", gpio, "wiring", wiring, "type", type, "boot", boot, "rating", rating));
            return done(r, "Đã lưu cấu hình switch minh họa.", "");
        }
        Map<String, Object> sw = relay(s, r);
        if ("deleteSwitch".equals(op)) {
            s.switches.remove(sw);
            return done(r, "Đã xóa switch minh họa.", "");
        }
        if ("saveSwitchSchedule".equals(op)) {
            String when = param(r, "runAt");
            future(when);
            sw.put("schedule", row("runAt", when, "action", choice(r, "command", "ON", "OFF")));
            return done(r, "Đã lưu hẹn giờ minh họa; không tự gửi lệnh thiết bị.", "");
        }
        if ("removeSwitchSchedule".equals(op)) {
            sw.put("schedule", null);
            return done(r, "Đã bỏ hẹn giờ switch.", "");
        }
        Map<String, Object> parent = find(s.nodes, "id", str(sw.get("nodeId")));
        require(yes(parent.get("online")), "Thiết bị cha OFFLINE, không thể điều khiển.");
        if ("toggleSwitch".equals(op)) {
            sw.put("on", !yes(sw.get("on")));
            s.log(str(parent.get("id")), str(sw.get("name")), (Integer) sw.get("gpio"), yes(sw.get("on")) ? "ON" : "OFF");
            return done(r, "Đã đổi trạng thái minh họa.", "");
        }
        require("testSwitch".equals(op), "Thao tác switch không hợp lệ.");
        return done(r, "Đã kiểm tra thao tác relay trong bản xem UI. Trạng thái được giữ nguyên; chưa phát xung phần cứng.", "");
    }

    public static Map<String, Object> scheduleDraft(HttpServletRequest r) {
        List<Object> days = new ArrayList<>();
        String[] values = r.getParameterValues("days");
        if (values != null) {
            require(values.length <= 7, "Ngày lặp không hợp lệ.");
            for (String value : values) {
                int day = number(value, 0, 6, "Ngày lặp");
                if (!days.contains(day)) {
                    days.add(day);
                }
            }
        }
        return row("id", input(r, "id", 64), "name", input(r, "name", 120), "switchId", input(r, "switchId", 64), "time", input(r, "time", 5), "action", input(r,
                "command", 3), "mode", input(r, "mode", 10), "date", input(r, "date", 10), "days", days);
    }

    private static String schedule(PreviewState s, HttpServletRequest r, String op) {
        if ("saveSchedule".equals(op)) {
            Map<String, Object> data = scheduleDraft(r);
            String name = str(data.get("name"));
            require(!name.isEmpty(), "Nhập tên lịch hẹn giờ.");
            find(s.scheduleRelays, "id", str(data.get("switchId")));
            time(str(data.get("time")));
            require(Arrays.asList("ON", "OFF").contains(data.get("action")), "Lệnh không hợp lệ.");
            String mode = str(data.get("mode"));
            require(Arrays.asList("weekly", "once").contains(mode), "Kiểu lặp không hợp lệ.");
            if ("once".equals(mode)) {
                future(str(data.get("date")) + "T" + data.get("time"));
                data.put("days", new ArrayList<Object>());
            } else {
                require(!list(data.get("days")).isEmpty(), "Chọn ít nhất một ngày lặp.");
            }
            String id = str(data.get("id"));
            if (id.isEmpty()) {
                require(s.schedules.size() < 200, "Bản xem UI giới hạn 200 lịch.");
                data.put("id", "schedule-" + s.nextSchedule++);
                data.put("enabled", true);
                s.schedules.add(data);
            } else {
                Map<String, Object> old = find(s.schedules, "id", id);
                data.put("enabled", old.get("enabled"));
                old.clear();
                old.putAll(data);
            }
            return done(r, "Đã lưu lịch minh họa trong session; chưa kết nối RTC/MQTT.", "");
        }
        Map<String, Object> schedule = find(s.schedules, "id", param(r, "id"));
        if ("deleteSchedule".equals(op)) {
            s.schedules.remove(schedule);
            return done(r, "Đã xóa lịch minh họa.", "");
        }
        if ("toggleSchedule".equals(op)) {
            require(next(schedule, LocalDateTime.now(ZONE)) != null, "Lịch đã qua giờ. Sửa ngày chạy để bật lại.");
            schedule.put("enabled", !yes(schedule.get("enabled")));
            return done(r, "Đã đổi trạng thái lịch minh họa.", "");
        }
        require("runSchedule".equals(op), "Thao tác lịch không hợp lệ.");
        schedule.put("lastDemo", LocalDateTime.now(ZONE).toString());
        return done(r, "Đã chạy thử UI lệnh " + schedule.get("action") + ". Không gửi lệnh ESP32.", "");
    }
}
