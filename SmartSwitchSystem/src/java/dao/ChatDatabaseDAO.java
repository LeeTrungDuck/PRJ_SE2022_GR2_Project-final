package dao;

import dto.ChatDatabaseSnapshotDTO;
import AIChat.ChatDataKind;
import enums.Role;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import utills.DBConnection;

/** SELECT-only chat boundary. Question text and model output never become SQL. */
public class ChatDatabaseDAO implements AutoCloseable {
    private final Connection connection;
    private final String switchFrom=" FROM tblSwitch sw JOIN tblESP32_Device esp ON esp.device_id=sw.device_id ";
    private final String activeSwitch="sw.is_active=1 AND esp.is_active=1";
    private final String permitted=" AND EXISTS (SELECT 1 FROM tblDevice_Permission p WHERE p.switch_id=sw.switch_id AND p.user_id=? AND p.is_active=1) ";
    private final String permittedDevice=" AND EXISTS (SELECT 1 FROM tblSwitch sw JOIN tblDevice_Permission p ON p.switch_id=sw.switch_id WHERE sw.device_id=esp.device_id AND sw.is_active=1 AND p.user_id=? AND p.is_active=1) ";
    private final String actorGuard=" AND EXISTS (SELECT 1 FROM tblUser auth WHERE auth.user_id=? AND auth.role=? AND auth.is_active=1) ";

    public ChatDatabaseDAO()throws SQLException,ClassNotFoundException {this(DBConnection.getConnection());}
    public ChatDatabaseDAO(Connection connection){this.connection=Objects.requireNonNull(connection);}
    private boolean viewer(String userId,Role role) {
        if(userId==null||userId.trim().isEmpty()||userId.length()>128||role==null)throw new IllegalArgumentException("Invalid chat identity.");
        if(role!=Role.VIEWER&&role!=Role.ADMIN&&role!=Role.OPERATOR)throw new IllegalArgumentException("Invalid chat role.");
        return role==Role.VIEWER;
    }
    private PreparedStatement statement(String sql,String userId,Role role)throws SQLException {
        return statement(sql,userId,role,role==Role.VIEWER);
    }
    private PreparedStatement statement(String sql,String userId,Role role,boolean permissionRequired)throws SQLException {
        PreparedStatement ps=connection.prepareStatement(sql);
        try {ps.setQueryTimeout(3);int at=1;if(permissionRequired)ps.setString(at++,userId);ps.setString(at++,userId);ps.setString(at,role.name());return ps;}
        catch(SQLException|RuntimeException e){ps.close();throw e;}
    }
    /** Hashes operational visibility and personal control grants, never profile data. */
    public String scope(String userId,Role role)throws SQLException {
        boolean restricted=viewer(userId,role);
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            digest(hash,userId);digest(hash,role.name());
            String devices="SELECT esp.device_id FROM tblESP32_Device esp WHERE esp.is_active=1"+(restricted?permittedDevice:"")+actorGuard+" ORDER BY esp.device_id";
            try(PreparedStatement ps=statement(devices,userId,role);ResultSet rs=ps.executeQuery()) {
                while(rs.next()){digest(hash,"D");digest(hash,rs.getString("device_id"));}
            }
            String switches="SELECT sw.switch_id"+switchFrom+" WHERE "+activeSwitch+(restricted?permitted:"")+actorGuard+" ORDER BY sw.switch_id";
            try(PreparedStatement ps=statement(switches,userId,role);ResultSet rs=ps.executeQuery()) {
                while(rs.next()){digest(hash,"S");digest(hash,rs.getString("switch_id"));}
            }
            // Administrative visibility alone does not authorize a control action.
            String controls="SELECT sw.switch_id"+switchFrom+" WHERE "+activeSwitch+permitted+actorGuard+" ORDER BY sw.switch_id";
            try(PreparedStatement ps=statement(controls,userId,role,true);ResultSet rs=ps.executeQuery()) {
                while(rs.next()){digest(hash,"C");digest(hash,rs.getString("switch_id"));}
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash.digest());
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("Required digest is unavailable.",e);}
    }
    private void digest(MessageDigest hash,String value){byte[] bytes=value.getBytes(StandardCharsets.UTF_8);hash.update(ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);}

    public ChatDatabaseSnapshotDTO read(String userId,Role role,Set<ChatDataKind> requested)throws SQLException {
        boolean restricted=viewer(userId,role);String before=scope(userId,role);
        Map<ChatDataKind,List<Map<String,Object>>> records=new EnumMap<>(ChatDataKind.class);
        Map<ChatDataKind,Map<String,Long>> totals=new EnumMap<>(ChatDataKind.class);
        for(ChatDataKind kind:ChatDataKind.values()) {
            if(!requested.contains(kind))continue;
            boolean permissionRequired=restricted||kind==ChatDataKind.CONTROLLABLE_SWITCHES;
            String columns,from,where,order;
            if(kind==ChatDataKind.DEVICES) {
                columns="esp.device_id,esp.name AS device_name,esp.status AS device_state, SUM(CASE WHEN esp.status='ONLINE' THEN CAST(1 AS bigint) ELSE 0 END) OVER() AS online_count, SUM(CASE WHEN esp.status='OFFLINE' THEN CAST(1 AS bigint) ELSE 0 END) OVER() AS offline_count, SUM(CASE WHEN esp.status='ERROR' THEN CAST(1 AS bigint) ELSE 0 END) OVER() AS error_count,";
                from=" FROM tblESP32_Device esp ";where="esp.is_active=1"+(restricted?permittedDevice:"");order="esp.device_id";
            } else {
                columns="sw.switch_id,sw.switch_name,esp.device_id,esp.name AS device_name,";
                from=switchFrom;where=activeSwitch+(permissionRequired?permitted:"");order="sw.device_id,sw.switch_id";
                if(kind==ChatDataKind.SWITCHES||kind==ChatDataKind.CONTROLLABLE_SWITCHES)columns+="sw.gpio_pin,sw.status AS switch_state,SUM(CASE WHEN sw.status='ON' THEN CAST(1 AS bigint) ELSE 0 END) OVER() AS on_count,SUM(CASE WHEN sw.status='OFF' THEN CAST(1 AS bigint) ELSE 0 END) OVER() AS off_count,";
                if(kind==ChatDataKind.SCHEDULES){columns+="sched.action,sched.run_time,sched.is_enabled,SUM(CASE WHEN sched.is_enabled=1 THEN CAST(1 AS bigint) ELSE 0 END) OVER() AS enabled_count,";from=" FROM tblSchedule sched JOIN tblSwitch sw ON sw.switch_id=sched.switch_id JOIN tblESP32_Device esp ON esp.device_id=sw.device_id ";where+=" AND sched.is_active=1";order="sched.run_time,sched.schedul_id";}
                if(kind==ChatDataKind.HISTORY){columns+="h.command,h.result,h.control_time,";from=" FROM tblControl_History h JOIN tblSwitch sw ON sw.switch_id=h.switch_id JOIN tblESP32_Device esp ON esp.device_id=sw.device_id ";order="h.control_time DESC,h.history_id DESC";}
            }
            // TOP and all identifiers are application constants. No SELECT *, owner IDs, contacts or network columns.
            String sql="SELECT TOP (12) "+columns+"COUNT_BIG(*) OVER() AS total_count"+from+" WHERE "+where+actorGuard+" ORDER BY "+order;
            List<Map<String,Object>> rows=new ArrayList<>();Map<String,Long> counts=new LinkedHashMap<>();counts.put("total",0L);
            try(PreparedStatement ps=statement(sql,userId,role,permissionRequired);ResultSet rs=ps.executeQuery()) {
                while(rs.next()) {
                    if(rows.size()>=12)break;
                    counts.put("total",rs.getLong("total_count"));
                    Map<String,Object> row=new LinkedHashMap<>();
                    row.put("deviceId",text(rs.getString("device_id"),64));row.put("deviceName",text(rs.getString("device_name"),120));
                    if(kind==ChatDataKind.DEVICES){row.put("state",state(rs.getString("device_state"),"ONLINE","OFFLINE","ERROR"));counts.put("online",rs.getLong("online_count"));counts.put("offline",rs.getLong("offline_count"));counts.put("error",rs.getLong("error_count"));}
                    else {
                        row.put("switchId",text(rs.getString("switch_id"),64));row.put("switchName",text(rs.getString("switch_name"),120));
                        if(kind==ChatDataKind.SWITCHES||kind==ChatDataKind.CONTROLLABLE_SWITCHES){row.put("gpio",rs.getInt("gpio_pin"));row.put("state",state(rs.getString("switch_state"),"ON","OFF"));counts.put("on",rs.getLong("on_count"));counts.put("off",rs.getLong("off_count"));}
                        if(kind==ChatDataKind.SCHEDULES){row.put("command",state(rs.getString("action"),"ON","OFF"));Time time=rs.getTime("run_time");row.put("runTime",time==null?"UNKNOWN":time.toLocalTime().toString());row.put("enabled",rs.getBoolean("is_enabled"));counts.put("enabled",rs.getLong("enabled_count"));}
                        if(kind==ChatDataKind.HISTORY){row.put("command",state(rs.getString("command"),"ON","OFF"));row.put("result",state(rs.getString("result"),"ON","OFF","ERROR"));Timestamp time=rs.getTimestamp("control_time");row.put("controlTime",time==null?"UNKNOWN":time.toLocalDateTime().toString());}
                    }
                    rows.add(row);
                }
            }
            records.put(kind,rows);totals.put(kind,counts);
        }
        if(!before.equals(scope(userId,role)))throw new SQLException("Chat visibility changed during read.","40001");
        return new ChatDatabaseSnapshotDTO(before,ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString(),records,totals);
    }
    private String text(String value,int max){return value==null?"":value.substring(0,Math.min(value.length(),max));}
    private String state(String value,String... allowed){return Arrays.asList(allowed).contains(value)?value:"UNKNOWN";}
    @Override public void close()throws SQLException{connection.close();}
}
