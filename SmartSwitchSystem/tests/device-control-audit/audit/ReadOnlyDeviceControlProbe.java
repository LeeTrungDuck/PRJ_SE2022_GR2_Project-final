package audit;

import dao.*;
import dto.*;
import java.sql.*;
import java.util.*;
import utills.DBConnection;

/** Run with PRODUCTION classes and the SQL driver, never the fixture classes. SELECT only. */
public final class ReadOnlyDeviceControlProbe {
    public static void main(String[] args) throws Exception {
        DriverManager.setLoginTimeout(10);
        List<SwitchDTO> switches;
        try(SwitchDAO dao=new SwitchDAO()) {
            switches=dao.findAll();Map<String,Integer> states=new LinkedHashMap<>();Set<String> ids=new HashSet<>();
            boolean fieldsValid=true;
            for(SwitchDTO sw:switches) {
                ids.add(sw.getSwitchId());states.put(sw.getStatus().name(),states.getOrDefault(sw.getStatus().name(),0)+1);
                fieldsValid &= sw.getSwitchName()!=null && sw.getDeviceId()!=null && sw.getEspName()!=null && sw.isActive() && sw.isEspActive();
            }
            System.out.println("LIVE_READ_SWITCHES count="+switches.size()+"; states="+states+"; unique_ids="+ids.size()+"; fields_and_active_flags_valid="+fieldsValid);
            if(!switches.isEmpty())System.out.println("LIVE_READ_BY_ID found="+(dao.findById(switches.get(0).getSwitchId())!=null));
        }
        try(DevicePermissionDAO dao=new DevicePermissionDAO()) {
            List<DevicePermissionDTO> p=dao.findAll();int active=0;for(DevicePermissionDTO value:p)if(value.isActive())active++;
            System.out.println("LIVE_READ_PERMISSIONS total="+p.size()+"; active="+active);
        }
        try(ControlHistoryDAO dao=new ControlHistoryDAO()) {System.out.println("LIVE_READ_HISTORY latest_count="+dao.findLatest50().size());}
        try(Connection c=DBConnection.getConnection();PreparedStatement ps=c.prepareStatement("SELECT user_id FROM tblUser WHERE role='VIEWER' AND is_active=1");ResultSet rs=ps.executeQuery()) {
            if(rs.next())try(SwitchDAO dao=new SwitchDAO()){System.out.println("LIVE_READ_VIEWER permitted_count="+dao.getPermittedSwitchesForUser(rs.getString("user_id")).size());}
            else System.out.println("LIVE_READ_VIEWER no active Viewer to probe");
        }
        System.out.println("READ ONLY: no INSERT, UPDATE, DELETE, schema changes or hardware requests.");
    }
}
