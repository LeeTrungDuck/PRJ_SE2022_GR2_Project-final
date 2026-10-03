/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.ESP32DeviceDTO;
import enums.DeviceStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ADMIN
 */
public class ESP32DeviceDAO {
    private Connection connection;

    private static final String SELECT = "SELECT device_id, name, host_name, status, last_seen, is_active FROM tblESP32_Device";

    public ESP32DeviceDAO(Connection connection) {
        this.connection = connection;
    }

    private ESP32DeviceDTO map(ResultSet rs) throws SQLException {
        ESP32DeviceDTO d = new ESP32DeviceDTO();
        d.setDeviceId(rs.getString("device_id"));
        d.setName(rs.getString("name"));
        d.setHostName(rs.getString("host_name"));
        d.setStatus(DeviceStatus.valueOf(rs.getString("status")));
        Timestamp ts = rs.getTimestamp("last_seen");
        d.setLastSeen(ts == null ? null : ts.toLocalDateTime());
        d.setActive(rs.getBoolean("is_active"));
        return d;
    }

    public ESP32DeviceDTO findById(String deviceId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE device_id = ? AND is_active = 1")) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<ESP32DeviceDTO> findAll() {
        List<ESP32DeviceDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE is_active = 1"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insert(ESP32DeviceDTO device) {
        String sql = "INSERT INTO tblESP32_Device(device_id, name, host_name, status, last_seen, is_active) VALUES(?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, device.getDeviceId());
            ps.setString(2, device.getName());
            ps.setString(3, device.getHostName());
            ps.setString(4, device.getStatus().name());
            ps.setTimestamp(5, device.getLastSeen() == null ? null : Timestamp.valueOf(device.getLastSeen()));
            ps.setBoolean(6, device.isActive());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(ESP32DeviceDTO device) {
        String sql = "UPDATE tblESP32_Device SET name = ?, host_name = ?, status = ?, last_seen = ?, is_active = ? WHERE device_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, device.getName());
            ps.setString(2, device.getHostName());
            ps.setString(3, device.getStatus().name());
            ps.setTimestamp(4, device.getLastSeen() == null ? null : Timestamp.valueOf(device.getLastSeen()));
            ps.setBoolean(5, device.isActive());
            ps.setString(6, device.getDeviceId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(String deviceId) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tblESP32_Device SET is_active = 0 WHERE device_id = ?")) {
            ps.setString(1, deviceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateStatus(String deviceId, DeviceStatus status) {
        String sql = "UPDATE tblESP32_Device SET status = ?, last_seen = GETDATE() WHERE device_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setString(2, deviceId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
