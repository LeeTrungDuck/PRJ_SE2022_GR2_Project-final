package controller;

import dao.*;
import dto.*;
import enums.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;

/** Schema/JDBC and access-state probes; no real database or hardware writes. */
public final class SchemaCompatibilityTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static final class SchemaJdbc {
        final Map<String,Object> row = new LinkedHashMap<>();
        final List<String> sql = new ArrayList<>();
        Map<Integer,Object> parameters;
        int statementsClosed, resultsClosed;
        boolean closed;
        SchemaJdbc(boolean device) {
            if (device) {
                row.put("device_id","ESP1"); row.put("name","Workshop");
                row.put("host_name","esp32.local"); row.put("status","ONLINE");
            } else {
                row.put("permission_id",17); row.put("user_id","U2");
                row.put("switch_id","SW1"); row.put("granted_by","U1");
            }
            row.put("is_active",true);
        }
        void validate(String query) throws SQLException {
            Matcher columns = Pattern.compile("(?i)^SELECT (.*?) FROM |^INSERT INTO \\w+\\((.*?)\\)").matcher(query);
            if (columns.find()) {
                String selected = columns.group(1) == null ? columns.group(2) : columns.group(1);
                for (String column : selected.split(","))
                    if (!row.containsKey(column.trim())) throw new SQLException("Unknown schema column: " + column);
            }
            Matcher assignments = Pattern.compile("(\\w+)\\s*=\\s*\\?").matcher(query);
            while (assignments.find())
                if (!row.containsKey(assignments.group(1))) throw new SQLException("Unknown schema assignment");
        }
        final Connection connection = ManagementControllerTest.proxy(Connection.class,(object,method,args)-> {
            if ("close".equals(method.getName())) { closed=true; return null; }
            if (!"prepareStatement".equals(method.getName())) return ManagementControllerTest.zero(method.getReturnType());
            String query=(String)args[0]; validate(query); sql.add(query);
            Map<Integer,Object> bindings=new LinkedHashMap<>(); parameters=bindings;
            int slots=query.length()-query.replace("?","").length();
            return ManagementControllerTest.proxy(PreparedStatement.class,(statement,operation,values)-> {
                if (operation.getName().startsWith("set")) {
                    check((Integer)values[0]<=slots,"bound parameter exists in schema query");
                    bindings.put((Integer)values[0],values[1]); return null;
                }
                if ("close".equals(operation.getName())) { statementsClosed++; return null; }
                if ("executeUpdate".equals(operation.getName())) {
                    check(bindings.size()==slots,"all schema write parameters are bound"); return 1;
                }
                if ("executeQuery".equals(operation.getName())) {
                    boolean[] visited={false};
                    return ManagementControllerTest.proxy(ResultSet.class,(result,read,fields)-> {
                        if ("next".equals(read.getName())) { boolean first=!visited[0]; visited[0]=true; return first; }
                        if ("close".equals(read.getName())) { resultsClosed++; return null; }
                        if (read.getName().startsWith("get")) {
                            check(row.containsKey(fields[0]),"mapper reads only columns in the current schema");
                            return row.get(fields[0]);
                        }
                        return ManagementControllerTest.zero(read.getReturnType());
                    });
                }
                return ManagementControllerTest.zero(operation.getReturnType());
            });
        });
    }
    private static void jdbcChecks() throws Exception {
        SchemaJdbc permission=new SchemaJdbc(false);
        try (DevicePermissionDAO dao=new DevicePermissionDAO(permission.connection)) {
            DevicePermissionDTO stored=dao.getUserPermission("SW1","U2");
            check(stored.isActive() && "U1".equals(stored.getGrantedBy()),"permission mapper uses one active flag");
            check("U2".equals(permission.parameters.get(1)) && "SW1".equals(permission.parameters.get(2)),"permission lookup binds user and switch");
            permission.row.put("is_active",false);
            check(!dao.findAll().get(0).isActive(),"inactive permission remains readable as revoked");
            DevicePermissionDTO grant=new DevicePermissionDTO(17,"U2","SW1","U1",true);
            dao.insert(grant);
            check("U1".equals(permission.parameters.get(3)) && Boolean.TRUE.equals(permission.parameters.get(4)),"insert binds grantor and active flag");
            dao.update(grant);
            check(Integer.valueOf(17).equals(permission.parameters.get(5)),"update targets the existing permission row");
            dao.delete(17);
            check(permission.sql.get(permission.sql.size()-1).startsWith("UPDATE") && permission.sql.get(permission.sql.size()-1).contains("is_active=0"),"revoke retains the permission row");
        }
        check(permission.closed && permission.resultsClosed==2 && permission.statementsClosed==5,"permission resources close");
        SchemaJdbc device=new SchemaJdbc(true);
        try (ESP32DeviceDAO dao=new ESP32DeviceDAO(device.connection)) {
            ESP32DeviceDTO stored=dao.findAll().get(0);
            check("ESP1".equals(stored.getDeviceId()) && stored.getStatus()==DeviceStatus.ONLINE && stored.isActive(),"device maps current schema columns");
            dao.insert(new ESP32DeviceDTO("ESP2","Lab","lab.local",DeviceStatus.OFFLINE,true));
            check("OFFLINE".equals(device.parameters.get(4)) && Boolean.TRUE.equals(device.parameters.get(5)),"device insert binds status and active flag");
            dao.updateStatus("ESP2",DeviceStatus.ONLINE);
            check("ONLINE".equals(device.parameters.get(1)) && "ESP2".equals(device.parameters.get(2)),"status update does not need a timestamp column");
        }
        check(device.closed && device.resultsClosed==1 && device.statementsClosed==3,"device resources close");
    }
    @SuppressWarnings("unchecked")
    private static boolean displayedAccess(ManagementControllerTest.Request r) {
        List<Map<String,Object>> groups=(List<Map<String,Object>>)r.ui().get("permissionDevices");
        List<Map<String,Object>> switches=(List<Map<String,Object>>)groups.get(0).get("switches");
        check(!switches.get(0).containsKey("canView") && !switches.get(0).containsKey("canControl"),"view model has one permission flag");
        return Boolean.TRUE.equals(switches.get(0).get("active"));
    }
    private static void draftChecks() throws Exception {
        ManagementControllerTest.FakeUsers users=new ManagementControllerTest.FakeUsers();
        ManagementControllerTest.FakePermissions permissions=new ManagementControllerTest.FakePermissions();
        PermissionManagementController controller=new PermissionManagementController() {
            protected UserDAO users(){return users;}
            protected DevicePermissionDAO permissions(){return permissions;}
            public void log(String message,Throwable error){}
        };
        ManagementControllerTest.Request r=new ManagementControllerTest.Request();
        r.session.put("LOGIN_USER",users.values.get("U1"));
        r.session.put("PERMISSION_DRAFTS","legacy draft");
        r.reset("GET","viewer","U2"); controller.doGet(r.request,r.response);
        String csrf=(String)r.ui().get("csrf");
        check(!displayedAccess(r) && !r.session.containsKey("PERMISSION_DRAFTS"),"obsolete drafts are retired without granting access");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","savePermissions","csrf",csrf,"access_SW1","1"); controller.doPost(r.request,r.response);
        check(r.status==303 && permissions.records.get(0).isActive(),"checked access enables the stored permission");
        r.reset("GET","viewer","U2"); controller.doGet(r.request,r.response);
        check(displayedAccess(r) && !Boolean.TRUE.equals(r.ui().get("dirty")),"saved active access renders checked");
        int savedWrites=permissions.writes;
        r.reset("POST","viewer","U2","uiOp","savePermissions","csrf",csrf); controller.doPost(r.request,r.response);
        check(permissions.writes==savedWrites && permissions.records.get(0).isActive()
                && r.attrs.get("uiError").toString().contains("outdated"),"old unchecked forms cannot revoke all access after deployment");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","savePermissions","csrf",csrf,"control_SW1","1"); controller.doPost(r.request,r.response);
        check(permissions.writes==savedWrites && r.attrs.get("uiError").toString().contains("outdated"),"old open forms cannot accidentally revoke access");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","savePermissions","csrf",csrf,"access_EVIL","1"); controller.doPost(r.request,r.response);
        check(permissions.writes==savedWrites && r.attrs.get("uiError")!=null,"unknown switch is rejected");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","revokePermissions","csrf",csrf); controller.doPost(r.request,r.response);
        r.reset("GET","viewer","U2"); controller.doGet(r.request,r.response);
        check(!displayedAccess(r) && Boolean.TRUE.equals(r.ui().get("dirty")) && permissions.records.get(0).isActive(),"revoke remains a draft until saved");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","resetPermissions","csrf",csrf); controller.doPost(r.request,r.response);
        r.reset("GET","viewer","U2"); controller.doGet(r.request,r.response);
        check(displayedAccess(r) && !Boolean.TRUE.equals(r.ui().get("dirty")) && permissions.writes==savedWrites,"reset restores saved access without a database write");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","selectViewer:U3","csrf",csrf); controller.doPost(r.request,r.response);
        check(r.status==303 && r.location.endsWith("viewer=U3"),"account selection redirects normally");
        r.reset("GET","viewer","U2"); controller.doGet(r.request,r.response);
        check(!displayedAccess(r) && Boolean.TRUE.equals(r.ui().get("dirty")),"unsaved unchecked access survives account switching");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","savePermissions","csrf",csrf); controller.doPost(r.request,r.response);
        check(!permissions.records.get(0).isActive(),"saving an unchecked box soft-revokes access");
        r.reset("GET","viewer","U2"); controller.doGet(r.request,r.response);
        check(!displayedAccess(r) && !Boolean.TRUE.equals(r.ui().get("dirty")),"revoked permission reloads unchecked");
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","savePermissions","csrf",csrf,"access_SW1","1"); controller.doPost(r.request,r.response);
        check(permissions.records.get(0).isActive(),"re-grant enables access again");
        int beforeDenied=permissions.writes;
        r.session.put("LOGIN_USER",users.values.get("U2"));
        r.reset("POST","permissionFormat","active-v1","viewer","U2","uiOp","savePermissions","csrf",csrf,"access_SW1","1"); controller.doPost(r.request,r.response);
        check(r.status==403 && permissions.writes==beforeDenied,"Viewer cannot grant access to themselves");
    }
    private static void controlGuardChecks() throws Exception {
        DevicePermissionDTO[] stored={null}; int[] switchLookups={0};
        SwitchStageController controller=new SwitchStageController() {
            protected DevicePermissionDAO permissions() {
                return new DevicePermissionDAO(new ManagementControllerTest.SqlCapture().connection) {
                    public DevicePermissionDTO getUserPermission(String sw,String user){return stored[0];}
                    public void close(){}
                };
            }
            protected SwitchDAO switches() throws SQLException {
                switchLookups[0]++; throw new SQLException("Test stops before database/hardware access");
            }
            public void log(String message){}
            public void log(String message,Throwable error){}
        };
        ManagementControllerTest.Request r=new ManagementControllerTest.Request();
        r.session.put("LOGIN_USER",ManagementControllerTest.FakeUsers.user("U2",Role.VIEWER));
        r.reset("POST","id","SW1"); controller.doPost(r.request,r.response);
        check(switchLookups[0]==0 && r.session.containsKey("DEVICE_CONTROL_FLASH_ERROR"),"missing permission is denied before hardware lookup");
        stored[0]=new DevicePermissionDTO(17,"U2","SW1","U1",false);
        r.reset("POST","id","SW1"); controller.doPost(r.request,r.response);
        check(switchLookups[0]==0 && r.session.containsKey("DEVICE_CONTROL_FLASH_ERROR"),"inactive permission is denied before hardware lookup");
        stored[0].setActive(true);
        r.reset("POST","id","SW1"); controller.doPost(r.request,r.response);
        check(switchLookups[0]==1,"active permission alone passes the control permission guard");
    }
    public static void main(String[] args) throws Exception {
        jdbcChecks(); draftChecks(); controlGuardChecks();
        System.out.println("PASS: "+checks+" schema/JDBC/combined-access/draft/control-guard checks. No real database or hardware writes.");
    }
}
