/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.DevicePermissionDTO;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ADMIN
 */
public class DevicePermissionDAO {
    private Connection connection;

    private static final String SELECT = "SELECT permission_id, user_id, switch_id, canView, canControl, granted_by FROM tblDevice_Permission";

    public DevicePermissionDAO(Connection connection) {
        this.connection = connection;
    }

    private DevicePermissionDTO map(ResultSet rs) throws SQLException {
        DevicePermissionDTO p = new DevicePermissionDTO();
        p.setPermissionId(rs.getInt("permission_id"));
        p.setUserId(rs.getString("user_id"));
        p.setSwitchId(rs.getString("switch_id"));
        p.setCanView(rs.getBoolean("canView"));
        p.setCanControl(rs.getBoolean("canControl"));
        p.setGrantedBy(rs.getString("granted_by"));
        return p;
    }

    public DevicePermissionDTO findById(int id) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE permission_id = ?")) {
            ps.setInt(1, id);
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

    public List<DevicePermissionDTO> findByUserId(String userId) {
        return query(SELECT + " WHERE user_id = ?", userId);
    }

    public List<DevicePermissionDTO> findBySwitchId(String switchId) {
        return query(SELECT + " WHERE switch_id = ?", switchId);
    }

    private List<DevicePermissionDTO> query(String sql, String param) {
        List<DevicePermissionDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
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

    public boolean insert(DevicePermissionDTO permission) {
        String sql = "INSERT INTO tblDevice_Permission(user_id, switch_id, canView, canControl, granted_by) VALUES(?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, permission.getUserId());
            ps.setString(2, permission.getSwitchId());
            ps.setBoolean(3, permission.isCanView());
            ps.setBoolean(4, permission.isCanControl());
            ps.setString(5, permission.getGrantedBy());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(DevicePermissionDTO permission) {
        String sql = "UPDATE tblDevice_Permission SET user_id = ?, switch_id = ?, canView = ?, canControl = ?, granted_by = ? WHERE permission_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, permission.getUserId());
            ps.setString(2, permission.getSwitchId());
            ps.setBoolean(3, permission.isCanView());
            ps.setBoolean(4, permission.isCanControl());
            ps.setString(5, permission.getGrantedBy());
            ps.setInt(6, permission.getPermissionId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tblDevice_Permission WHERE permission_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
