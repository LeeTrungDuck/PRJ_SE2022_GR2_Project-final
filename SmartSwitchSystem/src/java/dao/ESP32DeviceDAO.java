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
import java.util.ArrayList;
import java.util.List;
import utills.DBConnection;

/**
 *
 * @author ADMIN
 */
public class ESP32DeviceDAO implements AutoCloseable {
    private final Connection connection;

    private static final String SELECT = "SELECT device_id, name, host_name, status, is_active FROM tblESP32_Device";
    private static final String INSERT = "INSERT INTO tblESP32_Device(device_id, name, host_name, status, is_active) VALUES(?, ?, ?, ?, ?)";
    private static final String UPDATE_DETAILS = "UPDATE tblESP32_Device SET name = ?, host_name = ? WHERE device_id = ? AND is_active = 1";
    private static final String DELETE = "UPDATE tblESP32_Device SET is_active = 0 WHERE device_id = ? AND is_active = 1";
    private static final String UPDATE_STATUS = "UPDATE tblESP32_Device SET status = ? WHERE device_id = ?";

    public ESP32DeviceDAO() throws SQLException, ClassNotFoundException {
        this(DBConnection.getConnection());
    }

    public ESP32DeviceDAO(Connection connection) {
        this.connection = connection;
    }

    private ESP32DeviceDTO map(ResultSet rs) throws SQLException {
        ESP32DeviceDTO d = new ESP32DeviceDTO();
        d.setDeviceId(rs.getString("device_id"));
        d.setName(rs.getString("name"));
        d.setHostName(rs.getString("host_name"));
        d.setStatus(DeviceStatus.valueOf(rs.getString("status")));
        d.setActive(rs.getBoolean("is_active"));
        return d;
    }

    public ESP32DeviceDTO findById(String deviceId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE device_id = ? AND is_active = 1")) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<ESP32DeviceDTO> findAll() throws SQLException {
        List<ESP32DeviceDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE is_active = 1"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public List<ESP32DeviceDTO> findAllForManagement() throws SQLException {
        List<ESP32DeviceDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " ORDER BY device_id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    public boolean insert(ESP32DeviceDTO device) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
            ps.setString(1, device.getDeviceId());
            ps.setString(2, device.getName());
            ps.setString(3, device.getHostName());
            ps.setString(4, device.getStatus().name());
            ps.setBoolean(5, device.isActive());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean update(ESP32DeviceDTO device) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_DETAILS)) {
            ps.setString(1, device.getName());
            ps.setString(2, device.getHostName());
            ps.setString(3, device.getDeviceId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(String deviceId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setString(1, deviceId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateStatus(String deviceId, DeviceStatus status) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE_STATUS)) {
            ps.setString(1, status.name());
            ps.setString(2, deviceId);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
