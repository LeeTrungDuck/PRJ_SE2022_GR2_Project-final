package audit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dto.UserDTO;
import enums.Role;
import java.io.IOException;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

/** Local-only fixture for the REAL controllers, DAOs and JSPs. No SQL driver is packaged. */
@WebServlet(urlPatterns = "/__audit", loadOnStartup = 1)
public class DeviceControlFixture extends HttpServlet {
    private static final List<Map<String,Object>> switches = new CopyOnWriteArrayList<>();
    private static final List<Map<String,Object>> history = new CopyOnWriteArrayList<>();
    private static final Map<Integer,String> hardware = new ConcurrentHashMap<>();
    private static final AtomicInteger reads = new AtomicInteger(), commands = new AtomicInteger(), writes = new AtomicInteger();
    private static volatile String mode = "normal", host;
    private static volatile CountDownLatch simultaneousReads;
    private HttpServer server;
    private ExecutorService executor;

    @Override public void init() throws javax.servlet.ServletException {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.createContext("/", this::handleHardware);
            server.start();
            host = "http://127.0.0.1:" + server.getAddress().getPort();
            reset("normal");
        } catch(IOException e) { throw new javax.servlet.ServletException(e); }
    }
    @Override public void destroy() { if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow(); }
    private static Map<String,Object> row(Object... fields) {
        Map<String,Object> r = new LinkedHashMap<>();
        for(int i=0;i<fields.length;i+=2)r.put((String)fields[i],fields[i+1]);
        return r;
    }
    private static void reset(String nextMode) {
        mode=nextMode;switches.clear();history.clear();hardware.clear();reads.set(0);commands.set(0);writes.set(0);
        simultaneousReads="double".equals(mode)?new CountDownLatch(2):null;
        int count="empty".equals(mode)?0:47;
        for(int i=1;i<=count;i++) {
            String state=i%2==1?"ON":"OFF";
            switches.add(row("switch_id","SW"+i,"device_id",i<=23?"ESP1":"ESP2",
                "switch_name","Audit switch "+i,"gpio_pin",i,"status",state,"is_active",i!=46,
                "esp_name",i<=23?"Audit room A":"Audit room B","esp_host_name",host,"is_active_esp",i!=47));
            hardware.put(i,state);
        }
        if("mismatch".equals(mode))hardware.put(1,"OFF");
        if("double".equals(mode)) { hardware.put(1,"OFF");switches.get(0).put("status","OFF"); }
    }
    private void handleHardware(HttpExchange x) throws IOException {
        String path=x.getRequestURI().getPath(), query=x.getRequestURI().getQuery();
        int gpio=query!=null&&query.startsWith("gpio=")?Integer.parseInt(query.substring(5)):1;
        if("offline".equals(mode)) { respond(x,503,"{\"error\":\"offline\"}");return; }
        if("/switch/status".equals(path)) {
            reads.incrementAndGet();String current=hardware.get(gpio);
            if(simultaneousReads!=null) {
                simultaneousReads.countDown();
                try { simultaneousReads.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            respond(x,200,"invalid".equals(mode)?"{\"gpio\":"+gpio+",\"status\":\"UNKNOWN\"}":
                "{\"gpio\":"+gpio+",\"status\":\""+current.toLowerCase(Locale.ROOT)+"\"}");
        } else if("/switch/on".equals(path)||"/switch/off".equals(path)) {
            commands.incrementAndGet();
            if(!"falseAck".equals(mode))hardware.put(gpio,path.endsWith("/on")?"ON":"OFF");
            respond(x,200,"falseAck".equals(mode)?"{\"ok\":false}":"{\"ok\":true}");
        } else if("/device/info".equals(path)) {
            reads.incrementAndGet();StringBuilder body=new StringBuilder("{\"switches\":[");
            for(int i=1;i<=45;i++) { if(i>1)body.append(',');body.append("{\"gpio\":").append(i).append(",\"status\":\"").append(hardware.get(i).toLowerCase(Locale.ROOT)).append("\"}"); }
            respond(x,200,body.append("]}").toString());
        } else if("/device/status".equals(path))respond(x,200,"{\"status\":\"online\"}");
        else respond(x,404,"{}");
    }
    private static void respond(HttpExchange x,int status,String body) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status,bytes.length);
        try {x.getResponseBody().write(bytes);}finally{x.close();}
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res) throws IOException {
        res.setContentType("text/plain;charset=UTF-8");
        if("reset".equals(req.getParameter("op"))) {
            reset(req.getParameter("mode")==null?"normal":req.getParameter("mode"));
            HttpSession old=req.getSession(false);if(old!=null)old.invalidate();
            if(!"anonymous".equals(req.getParameter("role"))) {
                Role role=Role.valueOf(req.getParameter("role")==null?"ADMIN":req.getParameter("role"));
                req.getSession().setAttribute("LOGIN_USER",new UserDTO(role==Role.VIEWER?"U2":"U1","audit","",
                    "Audit "+role,role,true,"audit@example.invalid",""));
            }
        }
        if("true".equals(req.getParameter("view"))) {
            res.sendRedirect(req.getContextPath()+"/MainController?action=DEVICE_CONTROL");return;
        }
        String state=switches.isEmpty()?"NONE":String.valueOf(switches.get(0).get("status"));
        res.getWriter().print("mode="+mode+";reads="+reads+";commands="+commands+";writes="+writes+
            ";history="+history.size()+";db1="+state+";hw1="+hardware.get(1));
    }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler h) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);
    }
    private static Object base(Method m) throws SQLException {
        if("close".equals(m.getName())||"setAutoCommit".equals(m.getName())||"commit".equals(m.getName())||"rollback".equals(m.getName()))return null;
        if("isClosed".equals(m.getName()))return false;
        if("getAutoCommit".equals(m.getName()))return true;
        if("toString".equals(m.getName()))return "Isolated Device Control JDBC fixture";
        throw new SQLException("Audit fixture does not implement: "+m.getName());
    }
    public static Connection connection() {
        return proxy(Connection.class,(o,m,a)->"prepareStatement".equals(m.getName())?statement((String)a[0]):base(m));
    }
    private static PreparedStatement statement(String sql) {
        Map<Integer,Object> params=new HashMap<>();
        return proxy(PreparedStatement.class,(o,m,a)-> {
            String name=m.getName();
            if(name.startsWith("set")&&a!=null&&a.length==2) {params.put((Integer)a[0],a[1]);return null;}
            if("executeQuery".equals(name))return result(select(sql,params));
            if("executeUpdate".equals(name)) {
                if(sql.startsWith("UPDATE tblSwitch SET status")) {
                    for(Map<String,Object> sw:switches)if(sw.get("switch_id").equals(params.get(2))) {
                        sw.put("status",params.get(1));writes.incrementAndGet();return 1;
                    }
                    return 0;
                }
                if(sql.startsWith("INSERT INTO tblControl_History")) {
                    history.add(row("history_id",history.size()+1,"user_id",params.get(1),"switch_id",params.get(2),
                        "command",params.get(3),"result",params.get(4),"control_time",params.get(5)));return 1;
                }
                throw new SQLException("Unexpected audit write: "+sql);
            }
            return base(m);
        });
    }
    private static List<Map<String,Object>> select(String sql,Map<Integer,Object> p) throws SQLException {
        String q=sql.toLowerCase(Locale.ROOT);List<Map<String,Object>> rows=new ArrayList<>();
        if(q.contains("from tblswitch sw")) {
            for(Map<String,Object> sw:switches) {
                if(!Boolean.TRUE.equals(sw.get("is_active"))||!Boolean.TRUE.equals(sw.get("is_active_esp")))continue;
                if(q.contains("where sw.switch_id = ?")&&!sw.get("switch_id").equals(p.get(1)))continue;
                if(q.contains("where sw.device_id = ?")&&!sw.get("device_id").equals(p.get(1)))continue;
                if(q.contains("join tbldevice_permission")&&!"SW1".equals(sw.get("switch_id"))&&!"SW3".equals(sw.get("switch_id")))continue;
                rows.add(new LinkedHashMap<>(sw));
            }
        } else if(q.contains("from tbldevice_permission")) {
            String id=String.valueOf(p.get(2)),user=String.valueOf(p.get(1));
            boolean permitted="U1".equals(user)||("U2".equals(user)&&("SW1".equals(id)||"SW3".equals(id)));
            if(permitted||("U2".equals(user)&&"SW2".equals(id)))rows.add(row("permission_id",1,"user_id",user,"switch_id",id,"granted_by","U1","is_active",permitted));
        } else if(q.contains("from tbluser")) {
            for(String id:new String[]{"U1","U2"})if(!q.contains("where")||id.equals(p.get(1)))
                rows.add(row("user_id",id,"user_name","audit"+id,"password","","full_name","Audit "+id,"role",id.equals("U1")?"ADMIN":"VIEWER","is_active",true,"email","audit@example.invalid","phone_number",""));
        } else if(q.contains("from tblcontrol_history")) {
            for(Map<String,Object> h:history)rows.add(new LinkedHashMap<>(h));
            Collections.reverse(rows);
        } else throw new SQLException("Unexpected audit query: "+sql);
        return rows;
    }
    private static ResultSet result(List<Map<String,Object>> rows) {
        int[] at={-1};
        return proxy(ResultSet.class,(o,m,a)-> {
            if("next".equals(m.getName()))return ++at[0]<rows.size();
            if(m.getName().startsWith("get")&&a!=null) {
                Object value=rows.get(at[0]).get(a[0]);
                if("getString".equals(m.getName()))return value==null?null:String.valueOf(value);
                if("getInt".equals(m.getName()))return value==null?0:((Number)value).intValue();
                if("getBoolean".equals(m.getName()))return Boolean.TRUE.equals(value);
                if("getTimestamp".equals(m.getName()))return value;
            }
            return base(m);
        });
    }
}
