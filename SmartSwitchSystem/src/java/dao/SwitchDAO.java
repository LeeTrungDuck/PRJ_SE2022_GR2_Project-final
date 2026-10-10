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
public class SwitchDAO {
    private Connection connection;

    private static final String SELECT = "SELECT switch_id, device_id, switch_name, gpio_pin, status, is_active FROM tblSwitch";

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
        return s;
    }

    public SwitchDTO findById(String switchId) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE switch_id = ? AND is_active = 1")) {
            ps.setString(1, switchId);
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

    public List<SwitchDTO> findByDeviceId(String deviceId) {
        List<SwitchDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE device_id = ? AND is_active = 1")) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<SwitchDTO> findAll() {
        List<SwitchDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE is_active = 1"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insert(SwitchDTO sw) {
        String sql = "INSERT INTO tblSwitch(switch_id, device_id, switch_name, gpio_pin, status, is_active) VALUES(?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sw.getSwitchId());
            ps.setString(2, sw.getDeviceId());
            ps.setString(3, sw.getSwitchName());
            ps.setInt(4, sw.getGpioPin());
            ps.setString(5, sw.getStatus().name());
            ps.setBoolean(6, sw.isActive());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(SwitchDTO sw) {
        String sql = "UPDATE tblSwitch SET device_id = ?, switch_name = ?, gpio_pin = ?, status = ?, is_active = ? WHERE switch_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sw.getDeviceId());
            ps.setString(2, sw.getSwitchName());
            ps.setInt(3, sw.getGpioPin());
            ps.setString(4, sw.getStatus().name());
            ps.setBoolean(5, sw.isActive());
            ps.setString(6, sw.getSwitchId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(String switchId) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE tblSwitch SET is_active = 0 WHERE switch_id = ?")) {
            ps.setString(1, switchId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
