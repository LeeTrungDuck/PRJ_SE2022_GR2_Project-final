package controller;

import dao.UserDAO;
import dao.DevicePermissionDAO;
import dto.UserDTO;
import dto.DevicePermissionDTO;
import dto.SwitchDTO;
import enums.Role;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import static ui.UiSupport.*;

@WebServlet(name = "PermissionManagementController", urlPatterns = {"/PermissionManagementController"})
public class PermissionManagementController extends ManagementController {

    protected String page() {
        return "PERMISSION_MANAGER";
    }

    protected String jsp() {
        return "PermissionManager";
    }

    protected boolean allowed(Role role) {
        return role == Role.ADMIN || role == Role.OPERATOR;
    }

    protected DevicePermissionDAO permissions() throws SQLException, ClassNotFoundException {
        return new DevicePermissionDAO();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Map<String, Object>>> drafts(HttpServletRequest r) {
        HttpSession session = r.getSession();
        if (session.getAttribute("PERMISSION_DRAFTS") == null) {
            session.setAttribute("PERMISSION_DRAFTS", new LinkedHashMap<String, Map<String, Map<String, Object>>>());
        }
        return (Map<String, Map<String, Map<String, Object>>>) session.getAttribute("PERMISSION_DRAFTS");
    }

    static List<UserDTO> viewers(UserDAO dao) throws SQLException {
        List<UserDTO> viewers = new ArrayList<>();
        for (UserDTO user : dao.findAll()) {
            if (user.isActive() && user.getRole() == Role.VIEWER) {
                viewers.add(user);
            }
        }
        return viewers;
    }

    static Map<String, Map<String, Object>> saved(String viewer, List<SwitchDTO> switches, List<DevicePermissionDTO> permissions) {
        Map<String, Map<String, Object>> matrix = new LinkedHashMap<>();
        for (SwitchDTO sw : switches) {
            matrix.put(sw.getSwitchId(), row("canView", false, "canControl", false));
        }
        for (DevicePermissionDTO p : permissions) {
            if (viewer.equals(p.getUserId()) && matrix.containsKey(p.getSwitchId())) {
                matrix.put(p.getSwitchId(), row("canView", p.isActive(), "canControl", p.isActive() && p.isCanControl()));
            }
        }
        return matrix;
    }

    private Map<String, Map<String, Object>> current(String viewer, Map<String, Map<String, Object>> saved, HttpServletRequest r) {
        Map<String, Map<String, Object>> draft = drafts(r).get(viewer);
        if (draft == null) {
            draft = new LinkedHashMap<>();
        }
        draft.keySet().retainAll(saved.keySet());
        for (String key : saved.keySet()) {
            if (!draft.containsKey(key)) {
                draft.put(key, copyMap(saved.get(key)));
            }
        }
        return draft;
    }

    protected Map<String, Object> view(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException, ClassNotFoundException {
        try ( DevicePermissionDAO permissions = permissions()) {
            List<UserDTO> viewers = viewers(dao);
            List<SwitchDTO> switches = permissions.activeSwitches();
            List<DevicePermissionDTO> records = permissions.findAll();
            String id = param(r, "viewer");
            if (id.isEmpty() && !viewers.isEmpty()) {
                id = viewers.get(0).getUserId();
            }
            Map<String, Map<String, Map<String, Object>>> snapshots = new LinkedHashMap<>();
            synchronized (r.getSession()) {
                for (UserDTO user : viewers) {
                    snapshots.put(user.getUserId(), new LinkedHashMap<>(current(user.getUserId(), saved(user.getUserId(), switches, records), r)));
                }
            }
            return ManagementViews.permissions(actor, r, viewers, switches, records, snapshots, id);
        }
    }

    protected String change(UserDAO dao, UserDTO actor, HttpServletRequest r) throws SQLException, ClassNotFoundException {
        String id = required(r, "viewer", 20, "Viewer ID");
        UserDTO viewer = dao.findById(id);
        require(viewer != null && viewer.isActive() && viewer.getRole() == Role.VIEWER, "Chỉ được cấp quyền cho tài khoản Viewer đang hoạt động.");
        id = viewer.getUserId();
        String op = param(r, "uiOp");
        String target = id;
        try ( DevicePermissionDAO permissions = permissions()) {
            List<SwitchDTO> switches = permissions.activeSwitches();
            Map<String, Map<String, Object>> stored = saved(id, switches, permissions.findAll());
            Map<String, Map<String, Object>> matrix = new LinkedHashMap<>();
            synchronized (r.getSession()) {
                for (Map.Entry<String, Map<String, Object>> entry : current(id, stored, r).entrySet()) {
                    matrix.put(entry.getKey(), copyMap(entry.getValue()));
                }
            }
            if (!Arrays.asList("resetPermissions", "revokePermissions").contains(op)) {
                for (String key : r.getParameterMap().keySet()) {
                    if (key.startsWith("view_") || key.startsWith("control_")) {
                        require(matrix.containsKey(key.substring(key.indexOf('_') + 1)), "Switch không hợp lệ.");
                    }
                }
                for (String key : matrix.keySet()) {
                    boolean control = r.getParameter("control_" + key) != null;
                    matrix.put(key, row("canView", control || r.getParameter("view_" + key) != null, "canControl", control));
                }
            }
            if ("savePermissions".equals(op)) {
                permissions.saveMatrix(id, matrix, actor.getUserId());
                message(r, "Đã lưu quyền vào cơ sở dữ liệu. Bỏ quyền xem sẽ xóa mềm permission.");
            } else if ("grantView".equals(op)) {
                for (Map<String, Object> grant : matrix.values()) {
                    grant.put("canView", true);
                }
            } else if ("resetPermissions".equals(op)) {
                matrix = stored;
            } else if ("revokePermissions".equals(op)) {
                for (Map<String, Object> grant : matrix.values()) {
                    grant.put("canView", false);
                    grant.put("canControl", false);
                }
            } else if (op.startsWith("selectViewer:")) {
                target = op.substring(13);
                UserDTO next = dao.findById(target);
                require(next != null && next.isActive() && next.getRole() == Role.VIEWER, "Viewer không hợp lệ.");
                target = next.getUserId();
            } else {
                require("confirmRevoke".equals(op), "Thao tác phân quyền không hợp lệ.");
            }
            synchronized (r.getSession()) {
                if ("savePermissions".equals(op) || "resetPermissions".equals(op) || matrix.equals(stored)) {
                    drafts(r).remove(id);
                } else {
                    drafts(r).put(id, matrix);
                }
            }
            if (!"savePermissions".equals(op)) {
                message(r, "Đã cập nhật bản nháp. Bấm Lưu Phân Quyền để ghi vào cơ sở dữ liệu.");
            }
        }
        try {
            return "&viewer=" + URLEncoder.encode(target, "UTF-8") + ("confirmRevoke".equals(op) ? "&form=revoke" : "");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
