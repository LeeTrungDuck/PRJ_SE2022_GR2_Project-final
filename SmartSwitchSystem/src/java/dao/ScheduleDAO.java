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
public class ScheduleDAO implements AutoCloseable {
    private final Connection connection;

    private static final String SELECT = "SELECT schedul_id, switch_id, user_id, action, run_time, is_enabled, is_active FROM tblSchedule";
    private static final String INSERT = "INSERT INTO tblSchedule(switch_id, user_id, action, run_time, is_enabled, is_active) VALUES(?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE tblSchedule SET switch_id = ?, user_id = ?, action = ?, run_time = ?, is_enabled = ?, is_active = ? WHERE schedul_id = ? AND is_active = 1";
    private static final String DELETE = "UPDATE tblSchedule SET is_active = 0 WHERE schedul_id = ? AND is_active = 1";

    public ScheduleDAO() throws SQLException, ClassNotFoundException {
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
        s.setActive(rs.getBoolean("is_active"));
        return s;
    }

    public ScheduleDTO findById(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE schedul_id = ? AND is_active = 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<ScheduleDTO> findByUserId(String userId) throws SQLException {
        return query(SELECT + " WHERE user_id = ? AND is_active = 1", userId);
    }

    public List<ScheduleDTO> findBySwitchId(String switchId) throws SQLException {
        return query(SELECT + " WHERE switch_id = ? AND is_active = 1", switchId);
    }

    public List<ScheduleDTO> findAll() throws SQLException {
        return query(SELECT + " WHERE is_active = 1", null);
    }

    private List<ScheduleDTO> query(String sql, String param) throws SQLException {
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
        }
        return list;
    }

    public boolean insert(ScheduleDTO schedule) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
            ps.setString(1, schedule.getSwitchId());
            ps.setString(2, schedule.getUserId());
            ps.setString(3, schedule.getAction().name());
            ps.setTime(4, Time.valueOf(schedule.getRunTime()));
            ps.setBoolean(5, schedule.isEnabled());
            ps.setBoolean(6, schedule.isActive());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean update(ScheduleDTO schedule) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(UPDATE)) {
            ps.setString(1, schedule.getSwitchId());
            ps.setString(2, schedule.getUserId());
            ps.setString(3, schedule.getAction().name());
            ps.setTime(4, Time.valueOf(schedule.getRunTime()));
            ps.setBoolean(5, schedule.isEnabled());
            ps.setBoolean(6, schedule.isActive());
            ps.setInt(7, schedule.getScheduleId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(DELETE)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
