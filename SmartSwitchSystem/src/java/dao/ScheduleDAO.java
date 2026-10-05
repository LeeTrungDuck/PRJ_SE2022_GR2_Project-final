/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.ScheduleDTO;
import enums.ScheduleAction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import utills.DBConnection;

/**
 *
 * @author ADMIN
 */
public class ScheduleDAO {
    private Connection connection;

    private static final String SELECT = "SELECT schedul_id, switch_id, user_id, action, run_time, is_enabled FROM tblSchedule";

    public ScheduleDAO() throws SQLException {
        this.connection = DBConnection.getConnection();
    }

    private ScheduleDTO map(ResultSet rs) throws SQLException {
        ScheduleDTO s = new ScheduleDTO();
        s.setScheduleId(rs.getInt("schedul_id"));
        s.setSwitchId(rs.getString("switch_id"));
        s.setUserId(rs.getString("user_id"));
        s.setAction(ScheduleAction.valueOf(rs.getString("action")));
        Time t = rs.getTime("run_time");
        s.setRunTime(t == null ? null : t.toLocalTime());
        s.setEnabled(rs.getBoolean("is_enabled"));
        return s;
    }

    public ScheduleDTO findById(int id) {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE schedul_id = ?")) {
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

    public List<ScheduleDTO> findByUserId(String userId) {
        return query(SELECT + " WHERE user_id = ?", userId);
    }

    public List<ScheduleDTO> findBySwitchId(String switchId) {
        return query(SELECT + " WHERE switch_id = ?", switchId);
    }

    public List<ScheduleDTO> findAll() {
        return query(SELECT, null);
    }

    private List<ScheduleDTO> query(String sql, String param) {
        List<ScheduleDTO> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (param != null) {
                ps.setString(1, param);
            }
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

    public boolean insert(ScheduleDTO schedule) {
        String sql = "INSERT INTO tblSchedule(switch_id, user_id, action, run_time, is_enabled) VALUES(?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schedule.getSwitchId());
            ps.setString(2, schedule.getUserId());
            ps.setString(3, schedule.getAction().name());
            ps.setTime(4, Time.valueOf(schedule.getRunTime()));
            ps.setBoolean(5, schedule.isEnabled());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(ScheduleDTO schedule) {
        String sql = "UPDATE tblSchedule SET switch_id = ?, user_id = ?, action = ?, run_time = ?, is_enabled = ? WHERE schedul_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, schedule.getSwitchId());
            ps.setString(2, schedule.getUserId());
            ps.setString(3, schedule.getAction().name());
            ps.setTime(4, Time.valueOf(schedule.getRunTime()));
            ps.setBoolean(5, schedule.isEnabled());
            ps.setInt(6, schedule.getScheduleId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM tblSchedule WHERE schedul_id = ?")) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
}
