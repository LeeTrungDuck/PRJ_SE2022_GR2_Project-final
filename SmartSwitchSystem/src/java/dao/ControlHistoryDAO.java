/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import dto.ControlHistoryDTO;
import enums.Command;
import enums.ControlResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import utills.DBConnection;

/**
 *
 * @author ADMIN
 */
public class ControlHistoryDAO implements AutoCloseable {
    private final Connection connection;

    private static final String SELECT = "SELECT history_id, user_id, switch_id, command, result, control_time FROM tblControl_History";
    private static final String INSERT = "INSERT INTO tblControl_History(user_id, switch_id, command, result, control_time) VALUES(?, ?, ?, ?, ?)";

    public ControlHistoryDAO() throws SQLException, ClassNotFoundException {
        this.connection = DBConnection.getConnection();
    }

    private ControlHistoryDTO map(ResultSet rs) throws SQLException {
        ControlHistoryDTO h = new ControlHistoryDTO();
        h.setHistoryId(rs.getInt("history_id"));
        h.setUserId(rs.getString("user_id"));
        h.setSwitchId(rs.getString("switch_id"));
        h.setCommand(Command.valueOf(rs.getString("command")));
        h.setResult(ControlResult.valueOf(rs.getString("result")));
        Timestamp ts = rs.getTimestamp("control_time");
        h.setControlTime(ts == null ? null : ts.toLocalDateTime());
        return h;
    }

    public ControlHistoryDTO findById(int id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(SELECT + " WHERE history_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<ControlHistoryDTO> findByUserId(String userId) throws SQLException {
        return query(SELECT + " WHERE user_id = ? ORDER BY control_time DESC", userId);
    }

    public List<ControlHistoryDTO> findBySwitchId(String switchId) throws SQLException {
        return query(SELECT + " WHERE switch_id = ? ORDER BY control_time DESC", switchId);
    }

    public List<ControlHistoryDTO> findAll() throws SQLException {
        return query(SELECT + " ORDER BY control_time DESC", null);
    }

    private List<ControlHistoryDTO> query(String sql, String param) throws SQLException {
        List<ControlHistoryDTO> list = new ArrayList<>();
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

    public boolean insert(ControlHistoryDTO history) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(INSERT)) {
            ps.setString(1, history.getUserId());
            ps.setString(2, history.getSwitchId());
            ps.setString(3, history.getCommand().name());
            ps.setString(4, history.getResult().name());
            ps.setTimestamp(5, history.getControlTime() == null
                    ? new Timestamp(System.currentTimeMillis())
                    : Timestamp.valueOf(history.getControlTime()));
            return ps.executeUpdate() > 0;
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
