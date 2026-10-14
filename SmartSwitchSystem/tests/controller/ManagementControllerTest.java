package controller;

import dao.*;
import dto.*;
import enums.Role;
import enums.SwitchStatus;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.*;
import utills.PasswordUtil;
import utills.URLMap;

/** No production database writes: controller probes use fakes; SQL probes capture JDBC contracts. */
public final class ManagementControllerTest {
    static int checks;
    static void check(boolean ok,String label) { if(!ok) throw new AssertionError(label); checks++; }
    static Object zero(Class<?> type) { if(type==boolean.class)return false;if(type==int.class)return 0;if(type==long.class)return 0L;return null; }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,InvocationHandler handler) { return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler); }
    static Map<String,String[]> fields(String... pairs) { Map<String,String[]> map=new LinkedHashMap<>(); for(int i=0;i<pairs.length;i+=2) map.put(pairs[i],new String[]{pairs[i+1]}); return map; }
    static final class Request {
        final Map<String,Object> session=new HashMap<>(),attrs=new HashMap<>(); Map<String,String[]> params=new HashMap<>(); String method="GET",forward,location; int status=200, forwards;
        final HttpSession httpSession=proxy(HttpSession.class,(o,m,a)-> {
            switch(m.getName()) { case "getAttribute":return session.get(a[0]); case "setAttribute":session.put((String)a[0],a[1]);return null; case "removeAttribute":session.remove(a[0]);return null;case "invalidate":session.clear();return null; }
            return zero(m.getReturnType());
        });
        final HttpServletRequest request=proxy(HttpServletRequest.class,(o,m,a)-> {
            switch(m.getName()) {
                case "getMethod":return method; case "getContextPath":return "/SmartSwitchSystem";
                case "getSession":return httpSession; case "getParameter":String[] value=params.get(a[0]);return value==null?null:value[0];
                case "getParameterMap":return params; case "getParameterValues":return params.get(a[0]);case "getAttribute":return attrs.get(a[0]);
                case "setAttribute":attrs.put((String)a[0],a[1]);return null;case "removeAttribute":attrs.remove(a[0]);return null;
                case "getRequestDispatcher":String path=(String)a[0];return proxy(RequestDispatcher.class,(d,dm,da)-> { forward=path;forwards++;return null; });
            }
            return zero(m.getReturnType());
        });
        final HttpServletResponse response=proxy(HttpServletResponse.class,(o,m,a)-> {
            switch(m.getName()) { case "sendError":status=(Integer)a[0];return null;case "sendRedirect":status=302;location=(String)a[0];return null;case "setStatus":status=(Integer)a[0];return null;case "setHeader":if("Location".equals(a[0]))location=(String)a[1];return null;case "encodeRedirectURL":return a[0]; }
            return zero(m.getReturnType());
        });
        void reset(String method,String... fields) { this.method=method;params=fields(fields);status=200;forwards=0;forward=null;location=null;attrs.clear(); }
        @SuppressWarnings("unchecked") Map<String,Object> ui() { return (Map<String,Object>)attrs.get("ui"); }
    }
    static final class FakeUsers extends UserDAO {
        final Map<String,UserDTO> values=new LinkedHashMap<>();int writes;SQLException readFailure,writeFailure;
        FakeUsers() { super(proxy(Connection.class,(o,m,a)->zero(m.getReturnType()))); values.put("U1",user("U1",Role.ADMIN)); values.put("U2",user("U2",Role.VIEWER));values.put("U3",user("U3",Role.OPERATOR)); }
        static UserDTO user(String id,Role role) { return new UserDTO(id,id,"oldPassword1!","Tên "+id,role,true,id+"@example.com","090000000"+id.substring(1)); }
        static UserDTO copy(UserDTO u) { return u==null?null:new UserDTO(u.getUserId(),u.getUserName(),u.getPassword(),u.getFullName(),u.getRole(),u.isActive(),u.getEmail(),u.getPhone()); }
        public UserDTO findById(String id) throws SQLException { if(readFailure!=null)throw readFailure;return copy(values.get(id)); }
        public UserDTO findByUserName(String name) { for(UserDTO user:values.values())if(user.getUserName().equals(name))return user;return null; }
        public List<UserDTO> findAll() { List<UserDTO> list=new ArrayList<>();for(UserDTO u:values.values())list.add(copy(u));return list; }
        public boolean insert(UserDTO user) throws SQLException { if(writeFailure!=null)throw writeFailure;values.put(user.getUserId(),user);writes++;return true; }
        public boolean update(UserDTO user) { values.put(user.getUserId(),user);writes++;return true; }
        public boolean updateDetails(UserDTO user) { return update(user); }
        public boolean updateRole(UserDTO user) { return update(user); }
        public boolean updateActive(UserDTO user) { return update(user); }
        public boolean resetPassword(String id,String hash) { values.get(id).setPassword(hash);writes++;return true; }
        public boolean delete(String id) { values.get(id).setActive(false);writes++;return true; }
        public boolean updateProfile(String id,String name,String email,String phone) { UserDTO user=values.get(id);user.setFullName(name);user.setEmail(email);user.setPhone(phone);writes++;return true; }
        public boolean updatePassword(String id,String old,String hash) { UserDTO user=values.get(id);if(!user.getPassword().equals(old))return false;user.setPassword(hash);writes++;return true; }
        public void close() { }
    }
    static final class FakePermissions extends DevicePermissionDAO {
        List<DevicePermissionDTO> records=new ArrayList<>();int writes;SQLException readFailure,writeFailure;
        FakePermissions() { super(proxy(Connection.class,(o,m,a)->zero(m.getReturnType()))); }
        public List<SwitchDTO> activeSwitches() { SwitchDTO sw=new SwitchDTO();sw.setSwitchId("SW1");sw.setDeviceId("ESP1");sw.setSwitchName("Đèn");sw.setEspName("Node");sw.setEspHostName("test.local");sw.setActive(true);sw.setEspActive(true);sw.setGpioPin(2);sw.setStatus(SwitchStatus.OFF);return Arrays.asList(sw); }
        public List<DevicePermissionDTO> findAll() { return records; }
        public void saveMatrix(String viewer,Map<String,Map<String,Object>> matrix,String grantor) { records.clear();for(String id:matrix.keySet()) { Map<String,Object> grant=matrix.get(id);records.add(new DevicePermissionDTO(1,viewer,id,grantor,Boolean.TRUE.equals(grant.get("active")))); } writes++; }
        public void close() { }
    }
    static final class SqlCapture {
        final List<String> statements=new ArrayList<>();boolean committed,rolledBack,auto=true;boolean failInsert;int existingPermissionId;
        final Connection connection=proxy(Connection.class,(o,m,a)-> {
            switch(m.getName()) {
                case "getAutoCommit":return auto;case "setAutoCommit":auto=(Boolean)a[0];return null;case "commit":committed=true;return null;case "rollback":rolledBack=true;return null;
                case "prepareStatement":
                    String sql=(String)a[0];statements.add(sql);Map<Integer,Object> params=new HashMap<>();int slots=sql.length()-sql.replace("?","").length();
                    return proxy(PreparedStatement.class,(ps,pm,pa)-> {
                        if(pm.getName().startsWith("set")) { check((Integer)pa[0]<=slots,"SQL parameter index in bounds");params.put((Integer)pa[0],pa[1]);return null; }
                        if("executeUpdate".equals(pm.getName())) { check(params.size()==slots,"all SQL placeholders bound");if(failInsert&&sql.startsWith("INSERT"))throw new SQLException("simulated failure");return 1; }
                        if("executeQuery".equals(pm.getName())) {
                            Map<String,Object> row=new HashMap<>();row.put("user_id",params.getOrDefault(1,"U1"));row.put("user_name","user");row.put("password","old");row.put("full_name","User");row.put("role","VIEWER");row.put("is_active",true);row.put("permission_id",0);
                            if(sql.contains("WHERE role='ADMIN'"))row.put("user_id","U1");
                            row.put("permission_id",existingPermissionId);
                            boolean empty=sql.contains("FROM tblDevice_Permission")&&existingPermissionId==0;int[] position={0};
                            return proxy(ResultSet.class,(rs,rm,ra)-> {
                                switch(rm.getName()) {case "next":return !empty&&position[0]++==0;case "getString":return row.get(ra[0]);case "getBoolean":return row.getOrDefault(ra[0],false);case "getInt":return row.getOrDefault(ra[0],0); }
                                return zero(rm.getReturnType());
                            });
                        }
                        return zero(pm.getReturnType());
                    });
            }
            return zero(m.getReturnType());
        });
    }
    static void run() throws Exception {
        FakeUsers users=new FakeUsers();FakePermissions permissions=new FakePermissions();Request r=new Request();
        UserManagerController manager=new UserManagerController(){public void log(String text,Throwable error){}protected UserDAO users(){return users;}};
        AccountManagementController account=new AccountManagementController(){public void log(String text,Throwable error){}protected UserDAO users(){return users;}};
        PermissionManagementController permission=new PermissionManagementController(){public void log(String text,Throwable error){}protected UserDAO users(){return users;}protected DevicePermissionDAO permissions(){return permissions;}};
        manager.doGet(r.request,r.response);check("/web/login.jsp".equals(r.forward)&&r.forwards==1&&r.attrs.get("ERROR")!=null,"anonymous request forwards once with login message");
        r.session.put("LOGIN_USER",users.values.get("U2"));r.reset("GET");manager.doGet(r.request,r.response);check(r.status==403,"Viewer cannot manage Users");
        r.session.put("LOGIN_USER",users.values.get("U3"));r.reset("GET");manager.doGet(r.request,r.response);check(r.status==403,"Operator cannot manage Users");
        r.session.put("LOGIN_USER",users.values.get("U1"));r.reset("GET");manager.doGet(r.request,r.response);check("/web/UserManager.jsp".equals(r.forward)&&r.ui()!=null,"Admin user list renders DTO view");
        String csrf=(String)r.ui().get("csrf");check(!r.ui().toString().contains("oldPassword"),"view does not expose passwords");
        r.reset("GET","uiOp","deleteUser","id","U2");manager.doGet(r.request,r.response);check(r.status==405&&users.writes==0,"GET cannot delete");
        r.reset("POST","uiOp","deleteUser","id","U2","csrf","wrong");manager.doPost(r.request,r.response);check(r.status==403&&users.writes==0,"CSRF blocks writes");
        r.reset("POST","uiOp","deleteUser","id","U1","csrf",csrf);manager.doPost(r.request,r.response);check(r.attrs.get("uiError")!=null&&users.writes==0,"self deletion rejected");
        r.reset("POST","uiOp","addUser","formName","add","csrf",csrf,"fullName","Test","userName","native","password","Password12!","role","OPERATOR","email","new@example.com","phone","0900000004");manager.doPost(r.request,r.response);check(r.status==303&&users.values.size()==4,"create User redirects");
        UserDTO created=null;for(UserDTO user:users.values.values())if("native".equals(user.getUserName()))created=user;
        check(created!=null&&created.getUserId().startsWith("U")&&created.getUserId().length()<=20&&created.getPassword().startsWith("P1$"),"new user valid ID/hashed password");
        r.reset("POST","uiOp","deleteUser","id",created.getUserId(),"csrf",csrf);manager.doPost(r.request,r.response);check(!created.isActive()&&users.values.containsKey(created.getUserId()),"User remains after soft deletion");
        r.reset("GET","form","edit","id","U2");manager.doGet(r.request,r.response);check(Boolean.TRUE.equals(r.ui().get("dialog")),"edit dialog server state");
        r.reset("POST","uiOp","updateUser","id","U2","csrf",csrf,"fullName","Edited Viewer","email","edited@example.com","phone","0900000002","role","VIEWER","userName","ignored");manager.doPost(r.request,r.response);check(r.status==303&&"Edited Viewer".equals(users.values.get("U2").getFullName())&&"U2".equals(users.values.get("U2").getUserName()),"update User details keeps username fixed");
        r.reset("POST","uiOp","changeRole","id","U2","csrf",csrf,"role","OPERATOR");manager.doPost(r.request,r.response);check(r.status==303&&users.values.get("U2").getRole()==Role.OPERATOR,"change User role");
        r.reset("POST","uiOp","changeRole","id","U2","csrf",csrf,"role","VIEWER");manager.doPost(r.request,r.response);check(r.status==303&&users.values.get("U2").getRole()==Role.VIEWER,"restore Viewer role");
        r.reset("POST","uiOp","toggleUser","id","U2","csrf",csrf);manager.doPost(r.request,r.response);check(r.status==303&&!users.values.get("U2").isActive(),"deactivate User");
        r.reset("POST","uiOp","resetPassword","id","U2","csrf",csrf,"password","NewViewer123!");manager.doPost(r.request,r.response);check(r.status==303&&!users.values.get("U2").isActive()&&users.values.get("U2").getPassword().startsWith("P1$"),"reset password preserves inactive state");
        r.reset("POST","uiOp","toggleUser","id","U2","csrf",csrf);manager.doPost(r.request,r.response);check(r.status==303&&users.values.get("U2").isActive(),"reactivate User");
        r.reset("POST","uiOp","saveProfile","csrf",csrf,"id","U2","role","VIEWER","fullName","Own Profile","email","own@example.com","phone","0900000001");account.doPost(r.request,r.response);check(r.status==303&&"Own Profile".equals(users.values.get("U1").getFullName())&&users.values.get("U1").getRole()==Role.ADMIN&&!"Own Profile".equals(users.values.get("U2").getFullName()),"profile ignores spoofed id and role");
        r.reset("POST","uiOp","changePassword","csrf",csrf,"currentPassword","bad","newPassword","NewPassword1!","confirmPassword","NewPassword1!");account.doPost(r.request,r.response);check(r.attrs.get("uiError")!=null,"wrong current password denied");
        r.reset("POST","uiOp","changePassword","csrf",csrf,"currentPassword","oldPassword1!","newPassword","NewPassword1!","confirmPassword","mismatch");account.doPost(r.request,r.response);check(r.attrs.get("uiError")!=null,"password mismatch denied");
        r.reset("POST","uiOp","changePassword","csrf",csrf,"currentPassword","oldPassword1!","newPassword","NewPassword1!","confirmPassword","NewPassword1!");account.doPost(r.request,r.response);check(r.status==303&&PasswordUtil.verify("NewPassword1!",users.values.get("U1").getPassword()),"own password update persists hash");
        r.session.put("LOGIN_USER",users.values.get("U2"));r.reset("GET");permission.doGet(r.request,r.response);check(r.status==403,"Viewer cannot grant permissions");
        r.session.put("LOGIN_USER",users.values.get("U3"));r.reset("GET");permission.doGet(r.request,r.response);check(r.ui()!=null&&"/web/PermissionManager.jsp".equals(r.forward),"Operator reads real permission data");
        r.reset("POST","permissionFormat","active-v1","uiOp","savePermissions","csrf",csrf,"viewer",created.getUserId(),"access_SW1","1");permission.doPost(r.request,r.response);check(r.attrs.get("uiError")!=null&&permissions.writes==0,"inactive accounts cannot be grant targets");
        r.reset("POST","permissionFormat","active-v1","uiOp","savePermissions","csrf",csrf,"viewer","U2","access_EVIL","1");permission.doPost(r.request,r.response);check(r.attrs.get("uiError")!=null&&permissions.writes==0,"unknown switch cannot be granted");
        r.reset("POST","permissionFormat","active-v1","uiOp","savePermissions","csrf",csrf,"viewer","U2","access_SW1","1");permission.doPost(r.request,r.response);check(r.status==303&&permissions.records.get(0).isActive(),"active permission grants combined view/control access");
        r.reset("POST","permissionFormat","active-v1","uiOp","savePermissions","csrf",csrf,"viewer","U2");permission.doPost(r.request,r.response);check(r.status==303&&!permissions.records.get(0).isActive(),"unchecking rights soft revokes");
        users.values.get("U3").setActive(false);r.reset("GET");permission.doGet(r.request,r.response);check("/web/login.jsp".equals(r.forward)&&r.session.isEmpty()&&r.forwards==1,"inactive sessions invalidated and forwarded once");
        SqlCapture sql=new SqlCapture();DevicePermissionDAO dao=new DevicePermissionDAO(sql.connection);DevicePermissionDTO p=new DevicePermissionDTO(1,"U2","SW1","U1",true);
        dao.insert(p);dao.update(p);dao.delete(1);check(sql.statements.get(0).contains("VALUES(?,?,?,?)")&&sql.statements.get(2).startsWith("UPDATE")&&!sql.statements.get(2).contains("DELETE FROM"),"permission SQL binds fixed and delete is UPDATE");
        SqlCapture userSql=new SqlCapture();new UserDAO(userSql.connection).delete("U2");check(userSql.committed&&userSql.auto&&userSql.statements.stream().noneMatch(x->x.startsWith("DELETE"))&&userSql.statements.stream().anyMatch(x->x.contains("UPDATE tblUser SET is_active=0")),"User soft delete commits and keeps history");
        SqlCapture lastAdmin=new SqlCapture();try{new UserDAO(lastAdmin.connection).delete("U1");throw new AssertionError("Last Admin should be protected");}catch(IllegalArgumentException expected){check(lastAdmin.rolledBack&&!lastAdmin.committed&&lastAdmin.statements.stream().noneMatch(x->x.startsWith("UPDATE")),"last Admin protection happens before mutation");}
        SqlCapture roleSql=new SqlCapture();new UserDAO(roleSql.connection).updateRole(FakeUsers.user("U2",Role.OPERATOR));check(roleSql.statements.stream().anyMatch(x->x.startsWith("UPDATE tblUser SET role=?"))&&roleSql.statements.stream().filter(x->x.startsWith("UPDATE tblUser")).noneMatch(x->x.contains("password")||x.contains("full_name")),"role change never overwrites password or profile");
        SqlCapture rollback=new SqlCapture();rollback.failInsert=true;Map<String,Map<String,Object>> matrix=new LinkedHashMap<>();Map<String,Object> grant=new HashMap<>();grant.put("active",true);matrix.put("SW1",grant);
        try{new DevicePermissionDAO(rollback.connection).saveMatrix("U2",matrix,"U1");throw new AssertionError("Expected SQL failure");}catch(SQLException expected){check(rollback.rolledBack&&!rollback.committed&&rollback.auto,"permission matrix SQL failure rolls back");}
        SqlCapture reactivated=new SqlCapture();reactivated.existingPermissionId=7;new DevicePermissionDAO(reactivated.connection).saveMatrix("U2",matrix,"U1");check(reactivated.committed&&reactivated.statements.stream().anyMatch(x->x.startsWith("UPDATE tblDevice_Permission SET user_id"))&&reactivated.statements.stream().noneMatch(x->x.startsWith("INSERT")),"re-grant updates existing permission rather than violating unique user/switch");
        grant.put("active",false);SqlCapture revoke=new SqlCapture();revoke.existingPermissionId=7;new DevicePermissionDAO(revoke.connection).saveMatrix("U2",matrix,"U1");check(revoke.committed&&revoke.statements.stream().anyMatch(x->x.startsWith("UPDATE tblDevice_Permission SET is_active=0"))&&revoke.statements.stream().noneMatch(x->x.startsWith("DELETE")),"matrix revoke performs soft deletion");
        String hash=PasswordUtil.hash("Password12!");check(hash.length()<=50&&PasswordUtil.verify("Password12!",hash)&&!PasswordUtil.verify("wrong",hash)&&!PasswordUtil.verify("abc","P1$bad"),"password hash fits schema and verifies safely");
        check("/UserManagerController".equals(new URLMap().getUrl("USER_MANAGER"))&&"/PermissionManagementController".equals(new URLMap().getUrl("PERMISSION_MANAGER"))&&"/AccountManagementController".equals(new URLMap().getUrl("ACCOUNT_MANAGER")),"JSP action keys map exactly to servlet URL patterns");
        URLMap routes=new URLMap();
        check("/DeviceManagementController".equals(routes.getUrl("DEVICE_MANAGER"))
                &&"/ScheduleManagementController".equals(routes.getUrl("SCHEDULE_MANAGER"))
                &&"/ControlHistoryController".equals(routes.getUrl("CONTROL_HISTORY")),
                "device, schedule and history actions route to their MVC controllers");
        MainController main=new MainController();
        r.reset("GET");main.doGet(r.request,r.response);
        check("/web/login.jsp".equals(r.forward),"missing MainController action safely opens login");
        r.reset("GET","action","UNKNOWN");main.doGet(r.request,r.response);
        check("/web/login.jsp".equals(r.forward),"unknown MainController action safely opens login");
        r.reset("GET","action","SCHEDULE_MANAGER");main.doGet(r.request,r.response);
        check("/ScheduleManagementController".equals(r.forward),"MainController forwards schedule action to its servlet");
    }
    public static void main(String[] args) throws Exception { run();System.out.println("PASS: "+checks+" controller/JDBC/password checks. No real database writes."); }
}
