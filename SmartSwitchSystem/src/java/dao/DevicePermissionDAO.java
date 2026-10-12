package dao;

import dto.DevicePermissionDTO;
import dto.SwitchDTO;
import enums.SwitchStatus;
import java.sql.*;
import java.util.*;
import utills.DBConnection;

public class DevicePermissionDAO implements AutoCloseable {
    private final Connection connection;
    private static final String SELECT = "SELECT permission_id,user_id,switch_id,canControl,granted_by,is_active FROM tblDevice_Permission";
    public DevicePermissionDAO() throws SQLException, ClassNotFoundException { this(DBConnection.getConnection()); }
    public DevicePermissionDAO(Connection connection) { this.connection = connection; }
    private DevicePermissionDTO map(ResultSet rs) throws SQLException { return new DevicePermissionDTO(rs.getInt("permission_id"),rs.getString("user_id"),rs.getString("switch_id"),rs.getBoolean("canControl"),rs.getString("granted_by"),rs.getBoolean("is_active")); }
    public DevicePermissionDTO findById(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE permission_id=? AND is_active=1")) {
            ps.setInt(1,id); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }
    public DevicePermissionDTO getUserPermission(String switchId, String userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE user_id=? AND switch_id=?")) {
            ps.setString(1,userId); ps.setString(2,switchId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? map(rs) : null; }
        }
    }
    public List<DevicePermissionDTO> findByUserId(String id) throws SQLException { return query(SELECT + " WHERE user_id=? AND is_active=1",id); }
    public List<DevicePermissionDTO> findBySwitchId(String id) throws SQLException { return query(SELECT + " WHERE switch_id=? AND is_active=1",id); }
    public List<DevicePermissionDTO> findAll() throws SQLException { return query(SELECT,null); }
    private List<DevicePermissionDTO> query(String sql, String id) throws SQLException {
        List<DevicePermissionDTO> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (id != null) ps.setString(1,id);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(map(rs)); }
        }
        return result;
    }
    public boolean insert(DevicePermissionDTO p) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO tblDevice_Permission(user_id,switch_id,canControl,granted_by,is_active) VALUES(?,?,?,?,?)")) {
            bind(ps,p); return ps.executeUpdate() == 1;
        }
    }
    public boolean update(DevicePermissionDTO p) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tblDevice_Permission SET user_id=?,switch_id=?,canControl=?,granted_by=?,is_active=? WHERE permission_id=?")) {
            bind(ps,p); ps.setInt(6,p.getPermissionId()); return ps.executeUpdate() == 1;
        }
    }
    private void bind(PreparedStatement ps, DevicePermissionDTO p) throws SQLException {
        ps.setString(1,p.getUserId()); ps.setString(2,p.getSwitchId()); ps.setBoolean(3,p.isCanControl()); ps.setString(4,p.getGrantedBy()); ps.setBoolean(5,p.isActive());
    }
    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tblDevice_Permission SET is_active=0 WHERE permission_id=?")) { ps.setInt(1,id); return ps.executeUpdate() == 1; }
    }
    public List<SwitchDTO> activeSwitches() throws SQLException {
        List<SwitchDTO> result = new ArrayList<>();
        String sql = "SELECT sw.switch_id,sw.device_id,sw.switch_name,sw.gpio_pin,sw.status,esp.name AS esp_name,esp.host_name AS esp_host_name FROM tblSwitch sw JOIN tblESP32_Device esp ON sw.device_id=esp.device_id WHERE sw.is_active=1 AND esp.is_active=1 ORDER BY sw.device_id,sw.switch_id";
        try (PreparedStatement ps = connection.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SwitchDTO sw = new SwitchDTO(); sw.setSwitchId(rs.getString("switch_id")); sw.setDeviceId(rs.getString("device_id")); sw.setSwitchName(rs.getString("switch_name")); sw.setGpioPin(rs.getInt("gpio_pin")); sw.setStatus(SwitchStatus.valueOf(rs.getString("status"))); sw.setActive(true); sw.setEspActive(true); sw.setEspName(rs.getString("esp_name")); sw.setEspHostName(rs.getString("esp_host_name")); result.add(sw);
            }
        }
        return result;
    }
    /** Atomic grant/update/revoke. Reactivates the existing unique (user,switch) row. */
    public void saveMatrix(String viewer, Map<String,Map<String,Object>> matrix, String grantor) throws SQLException {
        boolean auto = connection.getAutoCommit(); connection.setAutoCommit(false);
        try {
            try (PreparedStatement ps = connection.prepareStatement("SELECT user_id FROM tblUser WITH (UPDLOCK,HOLDLOCK) WHERE user_id=? AND role='VIEWER' AND is_active=1")) {
                ps.setString(1,viewer); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Tài khoản Viewer không còn hoạt động."); }
            }
            for (Map.Entry<String,Map<String,Object>> entry : matrix.entrySet()) {
                String switchId = entry.getKey(); boolean control = Boolean.TRUE.equals(entry.getValue().get("canControl")); boolean active = control || Boolean.TRUE.equals(entry.getValue().get("canView"));
                try (PreparedStatement ps = connection.prepareStatement("SELECT sw.switch_id FROM tblSwitch sw WITH (HOLDLOCK) JOIN tblESP32_Device esp WITH (HOLDLOCK) ON sw.device_id=esp.device_id WHERE sw.switch_id=? AND sw.is_active=1 AND esp.is_active=1")) {
                    ps.setString(1,switchId); try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new IllegalArgumentException("Switch hoặc ESP32 không còn hoạt động. Tải lại trang."); }
                }
                int id = 0;
                try (PreparedStatement ps = connection.prepareStatement(SELECT + " WITH (UPDLOCK,HOLDLOCK) WHERE user_id=? AND switch_id=?")) {
                    ps.setString(1,viewer); ps.setString(2,switchId); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) id = rs.getInt("permission_id"); }
                }
                DevicePermissionDTO p = new DevicePermissionDTO(id,viewer,switchId,control,grantor,active);
                boolean ok = id == 0 ? !active || insert(p) : active ? update(p) : delete(id);
                if (!ok) throw new SQLException("Permission write lost its target");
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(auto); }
    }
    @Override public void close() throws SQLException { connection.close(); }
}