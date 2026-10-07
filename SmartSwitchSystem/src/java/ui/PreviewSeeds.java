package ui;
import java.util.*;
/** Reference data only; no database or hardware connection. */
public final class PreviewSeeds {
    private PreviewSeeds() {
    }
    public static Map<String,Object> row(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i=0;i<pairs.length;i+=2) result.put((String)pairs[i],pairs[i+1]);
        return result;
    }
    public static List<Object> list(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }
    public static List<Object> users() {
        return list(row("id", 1, "fullName", "Nguyễn Văn An", "userName", "an.nguyen", "email", "an.nguyen@esp32hub.io", "role", "ADMIN", "active",
        true, "createdAt", "2023-11-01", "superAdmin", true), row("id", 2, "fullName", "Trần Đình Dũng", "userName", "dung.tran", "email", "dung.tran@esp32hub.io",
        "role", "ADMIN", "active", true, "createdAt", "2024-01-10"), row("id", 3, "fullName", "Lê Quang Huy", "userName", "huy.le", "email",
        "huy.le@iot-plant.vn", "role", "OPERATION", "active", true, "createdAt", "2024-02-14"), row("id", 4, "fullName", "Phạm Thị Mai", "userName",
        "mai.pham", "email", "mai.pham@factory-net.org", "role", "OPERATION", "active", true, "createdAt", "2024-03-01"), row("id", 5, "fullName",
        "Hoàng Văn Bách", "userName", "bach.hoang", "email", "bach.hoang@monitor.local", "role", "VIEWER", "active", true, "createdAt", "2024-03-12"),
        row("id", 6, "fullName", "Vũ Đức Trọng", "userName", "trong.vu", "email", "trong.vu@thirdparty.com", "role", "VIEWER", "active", false,
        "createdAt", "2023-12-19"));
    }
    public static List<Object> nodes() {
        return list(row("id", "ESP32-CORE-A101", "name", "Node Tủ Điện 01", "ip", "192.168.1.150", "hostname", "esp32-rack-a101.local", "location",
        "Xưởng A MSB", "online", true, "latency", 12), row("id", "ESP32-OFFICE-02", "name", "Node Chiếu Sáng Văn Phòng", "ip", "192.168.1.155",
        "hostname", "esp32-lighting.local", "location", "Văn Phòng", "online", true, "latency", 18), row("id", "ESP32-PUMP-EXT04", "name", "Node Bơm Thủy Canh Nông Trại",
        "ip", "192.168.1.188", "hostname", "esp32-farm-hydro.local", "location", "Trạm Thủy Canh", "online", false, "latency", null, "offlineReason",
        "Timeout"));
    }
    public static List<Object> switches() {
        return list(row("key", "sw-1", "name", "Đèn Chiếu Xưởng Zone A", "nodeId", "ESP32-CORE-A101", "gpio", 16, "wiring", "NO", "rating",
        "10A / 250VAC", "type", "light", "boot", "OFF", "on", true, "schedule", null), row("key", "sw-2", "name", "Quạt Thông Gió CN 1", "nodeId",
        "ESP32-CORE-A101", "gpio", 17, "wiring", "NO", "rating", "30A High Inrush", "type", "fan", "boot", "ON", "on", true, "schedule", null),
        row("key", "sw-3", "name", "Bơm Nước Áp Lực 02 HP", "nodeId", "ESP32-CORE-A101", "gpio", 21, "wiring", "NC", "rating", "Thường Đóng",
        "type", "motor", "boot", "OFF", "on", false, "schedule", null), row("key", "sw-4", "name", "Ổ Cắm Máy In 3D & CNC", "nodeId", "ESP32-OFFICE-02",
        "gpio", 22, "wiring", "NO", "rating", "16A Opto", "type", "other", "boot", "OFF", "on", false, "schedule", null));
    }
    public static List<Object> permissionDevices() {
        return list(row("id", "esp32-01", "name", "Nhóm ESP32-01", "ip", "192.168.1.101", "location", "Khu vực Phòng Khách", "mac", "24:6F:28:AB:10:01",
        "switches", list(row("id", "sw-01-16", "name", "Đèn Chiếu Sáng Chính", "gpio", 16, "description", "Tải công suất: 65W • Relay Ch.1",
        "icon", "fa-lightbulb"), row("id", "sw-01-17", "name", "Quạt Thông Gió", "gpio", 17, "description", "Tải động cơ: 45W • Relay Ch.2",
        "icon", "fa-fan"), row("id", "sw-01-23", "name", "Ổ Cắm Phụ Tải", "gpio", 23, "description", "Phụ tải thiết bị • Relay Ch.3", "icon",
        "fa-plug"))), row("id", "esp32-02", "name", "Nhóm ESP32-02", "ip", "192.168.1.102", "location", "Phòng Thí Nghiệm", "mac", "24:6F:28:AB:10:02",
        "switches", list(row("id", "sw-02-18", "name", "Máy Bơm Làm Mát", "gpio", 18, "description", "Bơm tuần hoàn dung môi • Relay Ch.1",
        "icon", "fa-droplet"), row("id", "sw-02-19", "name", "Còi Báo Động Khẩn Cấp", "gpio", 19, "description", "Cảnh báo rò rỉ khí độc • Relay Ch.2",
        "icon", "fa-bell"))), row("id", "esp32-03", "name", "Nhóm ESP32-03", "ip", "192.168.1.103", "location", "Kho Thiết Bị Dự Phòng", "mac",
        "24:6F:28:AB:10:03", "switches", list(row("id", "sw-03-21", "name", "Đèn Khử Trùng UV", "gpio", 21, "description", "Hệ khử khuẩn buồng chứa • Relay Ch.1",
        "icon", "fa-spray-can-sparkles"), row("id", "sw-03-22", "name", "Van Cấp Nước Bồn Dự Bị", "gpio", 22, "description", "Solenoid 24V DC • Relay Ch.2",
        "icon", "fa-faucet"))));
    }
    public static List<Object> viewers() {
        return list(row("id", "viewer_khu_a", "name", "Nguyễn Văn Bình", "description", "Phụ trách khu nhà A", "initials", "NB", "grants", row("sw-01-16",
        list(true, true), "sw-01-17", list(true, false), "sw-02-18", list(true, false))), row("id", "viewer_khach", "name", "Trần Văn Hùng",
        "description", "Quan sát viên khách sạn", "initials", "TH", "grants", row("sw-01-16", list(true, false))), row("id", "viewer_giamsat_lab",
        "name", "Phan Minh Tuấn", "description", "Nghiên cứu viên Lab", "initials", "PM", "grants", row()), row("id", "viewer_kho", "name",
        "Lê Thị Hoa", "description", "Thủ kho thiết bị", "initials", "LH", "grants", row("sw-03-21", list(true, true), "sw-03-22", list(true,
        false))));
    }
    public static List<Object> scheduleRelays() {
        return list(row("id", "esp01-g16", "name", "Quạt hút thông gió", "node", "ESP32-01", "location", "Xưởng A", "gpio", 16), row("id", "esp01-g17",
        "name", "Đèn trần Xưởng A", "node", "ESP32-01", "location", "Xưởng A", "gpio", 17), row("id", "esp03-g22", "name", "Van cấp nước TĐ",
        "node", "ESP32-03", "location", "Kho Thiết Bị", "gpio", 22), row("id", "esp03-g21", "name", "Đèn UV khử khuẩn", "node", "ESP32-03",
        "location", "Phòng Thí Nghiệm", "gpio", 21), row("id", "esp02-g18", "name", "Bơm áp lực", "node", "ESP32-02", "location", "Vườn Ươm",
        "gpio", 18));
    }
    public static List<Object> schedules() {
        return list(row("id", "schedule-1", "name", "Tưới nước ca sáng tự động", "switchId", "esp03-g22", "time", "06:30", "action", "ON", "days",
        list(1, 2, 3, 4, 5), "mode", "weekly", "enabled", true), row("id", "schedule-2", "name", "Bật đèn chiếu sáng tăng ca", "switchId", "esp01-g17",
        "time", "18:00", "action", "ON", "days", list(1, 2, 3, 4, 5, 6), "mode", "weekly", "enabled", true), row("id", "schedule-3", "name",
        "Tắt quạt thông gió hết ca", "switchId", "esp01-g16", "time", "22:00", "action", "OFF", "days", list(1, 2, 3, 4, 5), "mode", "weekly",
        "enabled", true), row("id", "schedule-4", "name", "Khử khuẩn phòng thí nghiệm ca đêm", "switchId", "esp03-g21", "time", "01:00", "action",
        "ON", "days", list(1, 3, 5), "mode", "weekly", "enabled", true), row("id", "schedule-5", "name", "Tưới phun sương vườn ươm", "switchId",
        "esp02-g18", "time", "16:45", "action", "ON", "days", list(1, 2, 3, 4, 5), "mode", "weekly", "enabled", false), row("id", "schedule-6",
        "name", "Chiếu sáng cảnh quan cuối tuần", "switchId", "esp01-g17", "time", "19:30", "action", "ON", "days", list(6, 0), "mode", "weekly",
        "enabled", false));
    }
    public static List<Object> history() {
        return list(row("id", 89421, "timestamp", "2025-05-12T14:20:01+07:00", "actor", "admin", "source", "WEB_UI", "device", "ESP32-01", "ip",
        "192.168.1.150", "switchName", "Đèn chiếu sáng chính", "gpio", 16, "relay", 1, "command", "ON", "result", "200_OK", "rtt", 41), row("id",
        89420, "timestamp", "2025-05-12T14:18:22+07:00", "actor", "operator", "source", "SERVLET_API", "device", "ESP32-02", "ip", "192.168.1.151",
        "switchName", "Máy bơm tưới tự động", "gpio", 4, "relay", 2, "command", "OFF", "result", "200_OK", "rtt", 38), row("id", 89419, "timestamp",
        "2025-05-12T14:15:40+07:00", "actor", "scheduler", "source", "SCHEDULER", "device", "ESP32-03", "ip", "192.168.1.152", "switchName",
        "Quạt hút thông gió", "gpio", 23, "relay", 4, "command", "ON", "result", "TIMEOUT", "rtt", 5000), row("id", 89418, "timestamp", "2025-05-12T14:10:12+07:00",
        "actor", "viewer", "source", "WEB_UI", "device", "ESP32-01", "ip", "192.168.1.150", "switchName", "Đèn cảnh báo an ninh", "gpio", 17,
        "relay", 2, "command", "OFF", "result", "200_OK", "rtt", 45), row("id", 89417, "timestamp", "2025-05-12T14:02:19+07:00", "actor", "admin",
        "source", "WEB_UI", "device", "ESP32-01", "ip", "192.168.1.150", "switchName", "Đèn chiếu sáng chính", "gpio", 16, "relay", 1, "command",
        "OFF", "result", "200_OK", "rtt", 39), row("id", 89416, "timestamp", "2025-05-12T13:45:00+07:00", "actor", "scheduler", "source", "SCHEDULER",
        "device", "ESP32-02", "ip", "192.168.1.151", "switchName", "Máy bơm tưới tự động", "gpio", 4, "relay", 2, "command", "ON", "result",
        "200_OK", "rtt", 42), row("id", 89415, "timestamp", "2025-05-12T13:30:11+07:00", "actor", "operator", "source", "SERVLET_API", "device",
        "ESP32-01", "ip", "192.168.1.150", "switchName", "Còi báo động xưởng 1", "gpio", 18, "relay", 3, "command", "OFF", "result", "200_OK",
        "rtt", 35), row("id", 89414, "timestamp", "2025-05-12T13:12:05+07:00", "actor", "admin", "source", "WEB_UI", "device", "ESP32-03", "ip",
        "192.168.1.152", "switchName", "Cửa cuốn kho hàng", "gpio", 25, "relay", 1, "command", "ON", "result", "NODE_OFFLINE", "rtt", null),
        row("id", 89413, "timestamp", "2025-05-12T12:58:34+07:00", "actor", "operator", "source", "SERVLET_API", "device", "ESP32-01", "ip",
        "192.168.1.150", "switchName", "Quạt hút nhiệt tủ điện", "gpio", 19, "relay", 4, "command", "ON", "result", "200_OK", "rtt", 33), row("id",
        89412, "timestamp", "2025-05-12T12:40:50+07:00", "actor", "admin", "source", "WEB_UI", "device", "ESP32-02", "ip", "192.168.1.151",
        "switchName", "Van điện từ xả tràn", "gpio", 5, "relay", 3, "command", "OFF", "result", "200_OK", "rtt", 46));
    }
    public static Map<String,Object> actors() {
        return row("admin", row("username", "admin", "initials", "NA", "role", "ADMINISTRATOR", "style", "admin"), "operator", row("username",
        "operator_01", "initials", "QT", "role", "OPERATOR", "style", "operator"), "scheduler", row("username", "System-Schedule", "initials",
        "SYS", "role", "CRON_JOB", "style", "scheduler"), "viewer", row("username", "viewer_02", "initials", "LH", "role", "VIEWER", "style",
        "viewer"));
    }
}
