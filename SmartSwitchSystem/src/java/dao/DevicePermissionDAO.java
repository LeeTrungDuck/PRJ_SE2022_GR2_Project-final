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
import utills.DBConnection;

/**
 *
 * @author ADMIN
 */
public class DevicePermissionDAO {

    private Connection connection;

    private static final String SELECT = "SELECT permission_id, user_id, switch_id, canControl, granted_by, is_active FROM tblDevice_Permission";

    public DevicePermissionDAO() throws SQLException, ClassNotFoundException {
        this.connection = DBConnection.getConnection();
    }

    private DevicePermissionDTO map(ResultSet rs) throws SQLException {
        DevicePermissionDTO p = new DevicePermissionDTO();
        p.setPermissionId(rs.getInt("permission_id"));
        p.setUserId(rs.getString("user_id"));
        p.setSwitchId(rs.getString("switch_id"));
        p.setCanControl(rs.getBoolean("canControl"));
        p.setGrantedBy(rs.getString("granted_by"));
        p.setActive(rs.getBoolean("is_active"));
        return p;
    }

    public DevicePermissionDTO findById(int id) throws SQLException {
        try ( PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE permission_id = ? AND is_active = 1")) {
            ps.setInt(1, id);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        }
        return null;
    }

    public DevicePermissionDTO getUserPermission(String switchID, String userid) throws SQLException {
        if (switchID == null || userid == null) {
            return null;
        }
        String sql = SELECT + " WHERE user_id = ? AND switch_id = ?";
        try ( PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, userid);
            ps.setString(2, switchID);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DevicePermissionDTO(
                            rs.getInt("permission_id"),
                            rs.getString("user_id"),
                            rs.getString("switch_id"),
                            rs.getBoolean("canControl"),
                            rs.getString("granted_by"),
                            rs.getBoolean("is_active")
                    );
                }
            }
        }
        return null;
    }

    public List<DevicePermissionDTO> findByUserId(String userId) throws SQLException {
        return query(SELECT + " WHERE user_id = ? AND is_active = 1", userId);
    }

    public List<DevicePermissionDTO> findBySwitchId(String switchId) throws SQLException {
        return query(SELECT + " WHERE switch_id = ? AND is_active = 1", switchId);
    }

    private List<DevicePermissionDTO> query(String sql, String param) throws SQLException {
        List<DevicePermissionDTO> list = new ArrayList<>();
        try ( PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            try ( ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
        } catch (SQLException e) {
           throw e;
        }
        return list;
    }

    public boolean insert(DevicePermissionDTO permission) throws SQLException {
        String sql = "INSERT INTO tblDevice_Permission(user_id, switch_id, canControl, granted_by, is_active) VALUES(?, ?, ?, ?, ?, ?)";
        try ( PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, permission.getUserId());
            ps.setString(2, permission.getSwitchId());
            ps.setBoolean(4, permission.isCanControl());
            ps.setString(5, permission.getGrantedBy());
            ps.setBoolean(6, permission.isActive());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw e;
        }
    }

    public boolean update(DevicePermissionDTO permission) throws SQLException {
        String sql = "UPDATE tblDevice_Permission SET user_id = ?, switch_id = ?, canControl = ?, granted_by = ?, is_active = ? WHERE permission_id = ?";
        try ( PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, permission.getUserId());
            ps.setString(2, permission.getSwitchId());
            ps.setBoolean(4, permission.isCanControl());
            ps.setString(5, permission.getGrantedBy());
            ps.setBoolean(6, permission.isActive());
            ps.setInt(7, permission.getPermissionId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw e;
        }
    }

    public boolean delete(int id) throws SQLException {
        try ( PreparedStatement ps = connection.prepareStatement("UPDATE tblDevice_Permission SET is_active = 0 WHERE permission_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw e;
        }
    }
}
