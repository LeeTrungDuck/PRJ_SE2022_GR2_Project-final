package controller;

import dto.UserDTO;
import dto.SwitchDTO;
import dto.DevicePermissionDTO;
import enums.Role;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

/**
 * DTO-to-JSP view maps. No seed/demo state and no database writes here.
 */
final class ManagementViews {

    private final ManagementSupport support = new ManagementSupport();

    ManagementViews() {
    }

    Map<String, Map<String, Object>> permissionMatrix(String viewer, List<SwitchDTO> switches,
            List<DevicePermissionDTO> permissions) {
        Map<String, Map<String, Object>> matrix = new LinkedHashMap<>();
        for (SwitchDTO sw : switches) {
            matrix.put(sw.getSwitchId(), support.row("canView", false, "canControl", false));
        }
        for (DevicePermissionDTO permission : permissions) {
            if (viewer.equals(permission.getUserId()) && matrix.containsKey(permission.getSwitchId())) {
                matrix.put(permission.getSwitchId(), support.row("canView", permission.isActive(), "canControl",
                        permission.isActive() && permission.isCanControl()));
            }
        }
        return matrix;
    }

    Map<String, Object> profile(UserDTO user) {
        return support.row("userId", user.getUserId(), "userName", user.getUserName(), "fullName", user.getFullName(),
                "email", user.getEmail(), "phone", user.getPhone(), "role", user.getRole().name());
    }

    Map<String, Object> base(UserDTO user, HttpServletRequest r) {
        return support.row("profile", profile(user), "form", form(r), "dialog", false, "q", support.input(r, "q", 200));
    }

    String form(HttpServletRequest r) {
        return "POST".equals(r.getMethod()) ? support.param(r, "formName") : support.param(r, "form");
    }

    Map<String, Object> bound(HttpServletRequest r, Map<String, Object> defaults) {
        Map<String, Object> value = support.copyMap(defaults);
        if ("POST".equals(r.getMethod())) {
            for (String key : Arrays.asList("fullName", "userName", "email", "phone", "role")) {
                if (r.getParameter(key) != null) {
                    value.put(key, r.getParameter(key).trim().substring(0,
                            Math.min(254, r.getParameter(key).trim().length())));
                }
            }
        }
        return value;
    }

    String email(HttpServletRequest r) {
        String email = support.required(r, "email", 50, "email");
        support.require(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Email không hợp lệ.");
        return email;
    }

    String phone(HttpServletRequest r) {
        String phone = support.required(r, "phone", 10, "số điện thoại");
        support.require(phone.matches("[0-9]{10}"), "Số điện thoại cần đúng 10 chữ số theo cấu trúc CSDL hiện tại.");
        return phone;
    }

    private Map<String, Object> user(UserDTO user, UserDTO actor) {
        String role = user.getRole().name();
        return support.row("id", user.getUserId(), "userName", user.getUserName(), "fullName", user.getFullName(),
                "email", user.getEmail(), "phone", user.getPhone(), "role", role, "active", user.isActive(), "initials",
                support.initials(user.getFullName()), "roleClass", role.toLowerCase(Locale.ROOT), "roleLabel", role,
                "superAdmin", user.getUserId().equals(actor.getUserId()));
    }

    Map<String, Object> users(List<UserDTO> users, UserDTO actor, HttpServletRequest r) {
        Map<String, Object> ui = base(actor, r);
        List<Map<String, Object>> list = new ArrayList<>();
        int count = Math.max(1, (users.size() + 5) / 6), page = 1;
        try {
            page = Math.max(1, Math.min(count, Integer.parseInt(support.param(r, "p"))));
        } catch (NumberFormatException ignored) {
        }
        for (int i = (page - 1) * 6; i < Math.min(page * 6, users.size()); i++) {
            list.add(user(users.get(i), actor));
        }
        ui.putAll(support.row("users", list, "pageNumber", page, "pageCount", count));
        String form = support.str(ui.get("form"));
        if ("add".equals(form)) {
            ui.putAll(support.row("dialog", true, "dialogTitle", "Thêm Người Dùng Mới", "target", support.row(), "edit",
                    bound(r, support.row("role", "VIEWER")), "postOperation", "addUser"));
        } else if (Arrays.asList("edit", "password", "lock", "delete").contains(form)) {
            UserDTO target = null;
            for (UserDTO user : users) {
                if (user.getUserId().equals(support.param(r, "id"))) {
                    target = user;
                }
            }
            support.require(target != null, "Không tìm thấy tài khoản.");
            support.require(!target.getUserId().equals(actor.getUserId()),
                    "Sửa tài khoản của bạn tại Thông Tin Tài Khoản.");
            String op = "edit".equals(form) ? "updateUser"
                    : "password".equals(form) ? "resetPassword" : "lock".equals(form) ? "toggleUser" : "deleteUser";
            ui.putAll(support.row("dialog", true, "target", user(target, actor), "edit", bound(r, user(target, actor)),
                    "dialogTitle",
                    "edit".equals(form) ? "Chỉnh Sửa Người Dùng"
                    : "password".equals(form) ? "Đặt Lại Mật Khẩu"
                    : "lock".equals(form) ? "Đổi Trạng Thái Tài Khoản?" : "Xóa Mềm Tài Khoản?",
                    "postOperation", op));
        }
        return ui;
    }

    Map<String, Object> permissions(UserDTO actor, HttpServletRequest r, List<UserDTO> viewers,
            List<SwitchDTO> switches, List<DevicePermissionDTO> records,
            Map<String, Map<String, Map<String, Object>>> drafts, String selected) {
        Map<String, Object> ui = base(actor, r);
        UserDTO active = null;
        List<Map<String, Object>> people = new ArrayList<>();
        for (UserDTO viewer : viewers) {
            int count = 0;
            for (Map<String, Object> grant : drafts.get(viewer.getUserId()).values()) {
                if (support.yes(grant.get("canView"))) {
                    count++;
                }
            }
            if (selected.equals(viewer.getUserId())) {
                active = viewer;
            }
            if (support.normalize(viewer.getFullName() + " " + viewer.getUserName() + " " + viewer.getUserId())
                    .contains(support.normalize(ui.get("q")))) {
                people.add(support.row("id", viewer.getUserId(), "name", viewer.getFullName(), "initials",
                        support.initials(viewer.getFullName()), "grantCount", count, "selected",
                        selected.equals(viewer.getUserId()), "role", viewer.getRole().name()));
            }
        }
        if (!selected.isEmpty()) {
            support.require(active != null, "Không tìm thấy tài khoản đang hoạt động.");
        }
        Map<String, Map<String, Object>> matrix = active == null ? new LinkedHashMap<String, Map<String, Object>>()
                : drafts.get(selected);
        Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
        int granted = 0;
        for (SwitchDTO sw : switches) {
            if (!groups.containsKey(sw.getDeviceId())) {
                groups.put(sw.getDeviceId(), support.row("name", sw.getEspName(), "ip", sw.getEspHostName(), "location",
                        sw.getDeviceId(), "switches", new ArrayList<Object>()));
            }
            Map<String, Object> grant = matrix.get(sw.getSwitchId());
            if (grant == null) {
                grant = support.row("canView", false, "canControl", false);
            }
            if (support.yes(grant.get("canView"))) {
                granted++;
            }
            Map<String, Object> item = support.row("id", sw.getSwitchId(), "name", sw.getSwitchName(), "gpio",
                    sw.getGpioPin(), "description", "Switch ID: " + sw.getSwitchId(), "icon", "fa-plug");
            item.putAll(grant);
            support.list(groups.get(sw.getDeviceId()).get("switches")).add(item);
        }
        ui.putAll(support.row("viewers", people, "viewerTotal", viewers.size(), "activeViewer",
                active == null ? support.row()
                        : support.row("id", active.getUserId(), "name", active.getFullName(), "description",
                                "@" + active.getUserName()),
                "permissionDevices", new ArrayList<>(groups.values()), "permissionCount", granted, "permissionTotal",
                switches.size(), "dirty",
                active != null && !matrix.equals(permissionMatrix(selected, switches, records))));
        if ("revoke".equals(ui.get("form")) && active != null) {
            ui.putAll(support.row("dialog", true, "dialogTitle", "Hủy Toàn Bộ Quyền?"));
        }
        return ui;
    }
}
