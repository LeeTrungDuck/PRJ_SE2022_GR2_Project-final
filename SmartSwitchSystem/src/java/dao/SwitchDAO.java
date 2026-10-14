/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.SwitchDTO;
import enums.SwitchStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import utills.DBConnection;

/**
 *
 * @author ADMIN
 */
public class SwitchDAO implements AutoCloseable {

    private final Connection connection;

    private static final String SELECT = " SELECT sw.switch_id, sw.device_id, sw.switch_name, sw.gpio_pin, sw.status, sw.is_active, esp.name as esp_name, esp.host_name as esp_host_name, esp.is_active as is_active_esp "
            + " FROM tblSwitch sw  join tblESP32_Device esp on sw.device_id = esp.device_id ";
    private static final String INSERT = "INSERT INTO tblSwitch(switch_id, device_id, switch_name, gpio_pin, status, is_active) VALUES(?, ?, ?, ?, ?, ?)";
    private static final String UPDATE_DETAILS = "UPDATE tblSwitch SET device_id = ?, switch_name = ?, gpio_pin = ? WHERE switch_id = ? AND is_active = 1";
    private static final String UPDATE_STATUS = "UPDATE tblSwitch SET status = ? WHERE switch_id = ?";
    private static final String DELETE = "UPDATE tblSwitch SET is_active = 0 WHERE switch_id = ? AND is_active = 1";

    public SwitchDAO() throws SQLException, ClassNotFoundException {
        this.connection = DBConnection.getConnection();
    }

    private SwitchDTO map(ResultSet rs) throws SQLException {
        SwitchDTO s = new SwitchDTO();
        s.setSwitchId(rs.getString("switch_id"));
        s.setDeviceId(rs.getString("device_id"));
        s.setSwitchName(rs.getString("switch_name"));
        s.setGpioPin(rs.getInt("gpio_pin"));
        s.setStatus(SwitchStatus.valueOf(rs.getString("status")));
        s.setActive(rs.getBoolean("is_active"));
        s.setEspName(rs.getString("esp_name"));
        s.setEspHostName(rs.getString("esp_host_name"));
        s.setEspActive(rs.getBoolean("is_active_esp"));
        return s;
    }

    public List<SwitchDTO> getPermittedSwitchesForUser(String userId) throws SQLException, ClassNotFoundException {
        List<SwitchDTO> list = new ArrayList<>();
        String sql = "SELECT sw.switch_id, sw.device_id, sw.switch_name, sw.gpio_pin, sw.status, sw.is_active, "
                + "esp.name AS esp_name, esp.host_name AS esp_host_name,esp.is_active as is_active_esp "
                + "FROM tblSwitch sw "
                + "JOIN tblESP32_Device esp ON sw.device_id = esp.device_id "
                + "JOIN tblDevice_Permission p ON p.switch_id = sw.switch_id "
                + "WHERE p.user_id = ? "
//                + "AND ISNULL(p.is_active, 0) = 1 "
                + "AND ISNULL(esp.is_active, 0) = 1 "
                + "AND ISNULL(sw.is_active, 0) = 1 "
                + "ORDER BY sw.device_id, sw.switch_name";

        try (Connection conn = DBConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SwitchDTO s = new SwitchDTO();
                    s.setSwitchId(rs.getString("switch_id"));
                    s.setDeviceId(rs.getString("device_id"));
                    s.setSwitchName(rs.getString("switch_name"));
                    s.setGpioPin(rs.getInt("gpio_pin"));
                    s.setStatus(SwitchStatus.valueOf(rs.getString("status")));
                    s.setActive(rs.getBoolean("is_active"));
                    s.setEspName(rs.getString("esp_name"));
                    s.setEspHostName(rs.getString("esp_host_name"));
                    s.setEspActive(rs.getBoolean("is_active_esp"));
                    list.add(s);
                }
            }
        }
        return list;
    }

    public List<SwitchDTO> getSwitchesForViewer(String userId) throws SQLException, ClassNotFoundException {
        return getPermittedSwitchesForUser(userId);
    }

    public SwitchDTO findById(String switchId) throws SQLException {
        try (PreparedStatement ps = connection
                .prepareStatement(SELECT + " WHERE sw.switch_id = ? AND sw.is_active = 1 AND esp.is_active = 1")) {
            ps.setString(1, switchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public List<SwitchDTO> findByDeviceId(String deviceId) throws SQLException {
        List<SwitchDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection
                .prepareStatement(SELECT + " WHERE sw.device_id = ? AND sw.is_active = 1 AND esp.is_active = 1")) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        }
        return list;
    }

    public List<SwitchDTO> findAll() throws SQLException {
        List<SwitchDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection
                .prepareStatement(SELECT + " WHERE sw.is_active = 1 AND esp.is_active = 1");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<SwitchDTO> findAllForManagement() throws SQLException {
        List<SwitchDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " ORDER BY sw.device_id, sw.switch_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public SwitchDTO findByIdForManagement(String switchId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE sw.switch_id = ?")) {
            ps.setString(1, switchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public boolean insert(SwitchDTO sw) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
            ps.setString(1, sw.getSwitchId());
            ps.setString(2, sw.getDeviceId());
            ps.setString(3, sw.getSwitchName());
            ps.setInt(4, sw.getGpioPin());
            ps.setString(5, sw.getStatus().name());
            ps.setBoolean(6, sw.isActive());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean update(SwitchDTO sw) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_DETAILS)) {
            ps.setString(1, sw.getDeviceId());
            ps.setString(2, sw.getSwitchName());
            ps.setInt(3, sw.getGpioPin());
            ps.setString(4, sw.getSwitchId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(String switchId, SwitchStatus status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setString(2, switchId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(String switchId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setString(1, switchId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean activate(String switchId) throws SQLException {
        String sql = "UPDATE sw SET is_active = 1 FROM tblSwitch sw "
                + "JOIN tblESP32_Device esp ON esp.device_id = sw.device_id "
                + "WHERE sw.switch_id = ? AND sw.is_active = 0 AND esp.is_active = 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, switchId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
