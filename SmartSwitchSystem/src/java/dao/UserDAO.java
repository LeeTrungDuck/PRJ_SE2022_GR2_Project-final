/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.UserDTO;
import enums.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import utills.DBConnection;

/**
 *
 * @author ADMIN
 */
public class UserDAO {

    private Connection connection;

    private static final String SELECT = "SELECT user_id, user_name, password, full_name, role, is_active FROM tblUser";

    public UserDAO() throws SQLException, ClassNotFoundException {
        this.connection = DBConnection.getConnection();
    }

    private UserDTO map(ResultSet rs) throws SQLException {
        UserDTO u = new UserDTO();
        u.setUserId(rs.getString("user_id"));
        u.setUserName(rs.getString("user_name"));
        u.setPassword(rs.getString("password"));
        u.setFullName(rs.getString("full_name"));
        u.setRole(Role.valueOf(rs.getString("role")));
        u.setActive(rs.getBoolean("is_active"));
        return u;
    }

    public UserDTO findById(String userId) {
        try ( PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE user_id = ?")) {
            ps.setString(1, userId);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public UserDTO findByUserName(String userName) {
        try ( PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE user_name = ?")) {
            ps.setString(1, userName);
            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return map(rs);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<UserDTO> findAll() {
        List<UserDTO> list = new ArrayList<>();
        try ( PreparedStatement ps = connection.prepareStatement(SELECT);  ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean insert(UserDTO user) {
        String sql = "INSERT INTO tblUser(user_id, user_name, password, full_name, role, is_active) VALUES(?, ?, ?, ?, ?, ?)";
        try ( PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUserName());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getFullName());
            ps.setString(5, user.getRole().name());
            ps.setBoolean(6, user.isActive());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(UserDTO user) {
        String sql = "UPDATE tblUser SET user_name = ?, password = ?, full_name = ?, role = ?, is_active = ? WHERE user_id = ?";
        try ( PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getUserName());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getRole().name());
            ps.setBoolean(5, user.isActive());
            ps.setString(6, user.getUserId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(String userId) {
        try ( PreparedStatement ps = connection.prepareStatement("DELETE FROM tblUser WHERE user_id = ?")) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public UserDTO checkLogin(String userName, String passWord) {
        String sql = "SELECT user_id, user_name, password, full_name, role, is_active, email, phone_number "
                + "FROM [SmartSwitchSystem].[dbo].[tblUser] WHERE user_name = ? AND password = ?";
        UserDTO dto = null;

        // Sử dụng try-with-resources để tự động đóng Connection và PreparedStatement
        try ( Connection cn = DBConnection.getConnection();  PreparedStatement ps = cn.prepareStatement(sql)) {

            ps.setString(1, userName);
            ps.setString(2, passWord);

            try ( ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto = new UserDTO(
                            rs.getString("user_id"),
                            rs.getString("user_name"),
                            rs.getString("password"),
                            rs.getString("full_name"),
                            Role.valueOf(rs.getString("role")),
                            rs.getBoolean("is_active"),
                            rs.getString("email"),
                            rs.getString("phone_number")
                    );
                }
            }
        } catch (ClassNotFoundException | SQLException ex) {
            Logger.getLogger(UserDAO.class.getName()).log(Level.SEVERE, null, ex);
        }
        return dto;
    }
}
