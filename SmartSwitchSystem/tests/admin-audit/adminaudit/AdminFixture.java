package adminaudit;

import com.sun.net.httpserver.*;
import dto.UserDTO;
import enums.Role;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import utills.PasswordUtil;

/** Isolated JDBC data and loopback HTTP ESP32 for the production controllers/DAOs/JSPs. */
@WebServlet(urlPatterns="/__admin",loadOnStartup=1)
public class AdminFixture extends HttpServlet {
    private static final Map<String,List<Map<String,Object>>> db=new LinkedHashMap<>();
    private static final Map<Integer,String> hardware=new ConcurrentHashMap<>();
    private static final AtomicInteger commands=new AtomicInteger(),reads=new AtomicInteger(),writes=new AtomicInteger();
    private static String host,passwordHash,mode="normal";
    private HttpServer server;private ExecutorService executor;
    @Override public void init()throws javax.servlet.ServletException {
        try {
            passwordHash=PasswordUtil.hash("Password12!");
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            executor=Executors.newCachedThreadPool();server.setExecutor(executor);
            server.createContext("/",this::hardwareRequest);server.start();
            host="http://127.0.0.1:"+server.getAddress().getPort();reset("normal");
        }catch(IOException e){throw new javax.servlet.ServletException(e);}
    }
    @Override public void destroy(){if(server!=null)server.stop(0);if(executor!=null)executor.shutdownNow();}
    private static Map<String,Object> row(Object... fields){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<fields.length;i+=2)r.put((String)fields[i],fields[i+1]);return r;}
    private static void add(String table,Object...fields){db.get(table).add(row(fields));}
    private static synchronized void reset(String nextMode){
        mode=nextMode;db.clear();hardware.clear();commands.set(0);reads.set(0);writes.set(0);
        for(String t:new String[]{"tbluser","tblesp32_device","tblswitch","tbldevice_permission","tblschedule","tblcontrol_history"})db.put(t,new ArrayList<>());
        String[] roles={"ADMIN","VIEWER","OPERATOR","ADMIN"};
        for(int i=1;i<=4;i++)add("tbluser","user_id","U"+i,"user_name",i==1?"auditroot":"audit"+i,"password",passwordHash,"full_name",i==1?"Original Admin":"Audit User "+i,"role",roles[i-1],"is_active",true,"email","audit"+i+"@example.invalid","phone_number","090000000"+i);
        add("tblesp32_device","device_id","ESP1","name","Audit room A","host_name",host,"status","ONLINE","is_active",true);
        add("tblesp32_device","device_id","ESP2","name","Audit room B","host_name","192.0.2.10","status","OFFLINE","is_active",true);
        add("tblesp32_device","device_id","ESP3","name","Inactive ESP32","host_name","192.0.2.11","status","OFFLINE","is_active",false);
        for(int i=1;i<=5;i++){
            int gpio=i==1?2:i==2?5:i==3?18:i==4?19:21;
            add("tblswitch","switch_id","SW"+i,"device_id",i<=2?"ESP1":i<=4?"ESP2":"ESP3","switch_name","Admin audit switch "+i,"gpio_pin",gpio,"status","OFF","is_active",i!=4);
            hardware.put(gpio,"OFF");
        }
        add("tbldevice_permission","permission_id",1,"user_id","U1","switch_id","SW1","granted_by","U1","is_active",true);
        add("tbldevice_permission","permission_id",2,"user_id","U2","switch_id","SW1","granted_by","U1","is_active",true);
        add("tblschedule","schedul_id",1,"switch_id","SW1","user_id","U1","action","ON","run_time",Time.valueOf("06:30:00"),"is_enabled",true,"is_active",true);
    }
    private void hardwareRequest(HttpExchange x)throws IOException{
        String p=x.getRequestURI().getPath(),q=x.getRequestURI().getQuery();int gpio=q!=null?Integer.parseInt(q.substring(q.indexOf('=')+1)):2;
        String body="{}";int status=200;
        if(p.endsWith("/switch/status")){reads.incrementAndGet();body="{\"gpio\":"+gpio+",\"status\":\""+hardware.getOrDefault(gpio,"OFF").toLowerCase(Locale.ROOT)+"\"}";}
        else if(p.endsWith("/switch/on")||p.endsWith("/switch/off")){
            commands.incrementAndGet();if(!"falseAck".equals(mode))hardware.put(gpio,p.endsWith("/on")?"ON":"OFF");body="{\"ok\":"+(!"falseAck".equals(mode))+"}";
        }else if(p.endsWith("/device/status"))body="{\"status\":\"online\"}";
        else status=404;
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status,bytes.length);try{x.getResponseBody().write(bytes);}finally{x.close();}
    }
    private static Map<String,Object> find(String table,String key,Object value){for(Map<String,Object> r:db.get(table))if(String.valueOf(r.get(key)).equals(String.valueOf(value)))return r;return null;}
    @Override protected synchronized void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
        res.setContentType("text/plain;charset=UTF-8");String op=req.getParameter("op");Map<String,Object> out=new LinkedHashMap<>();
        if("reset".equals(op)){
            reset(req.getParameter("mode")==null?"normal":req.getParameter("mode"));HttpSession old=req.getSession(false);if(old!=null)old.invalidate();
            String actor=req.getParameter("actor")==null?"U1":req.getParameter("actor");Map<String,Object> u=find("tbluser","user_id",actor);
            req.getSession().setAttribute("LOGIN_USER",new UserDTO(actor,(String)u.get("user_name"),"",(String)u.get("full_name"),Role.valueOf((String)u.get("role")),true,(String)u.get("email"),(String)u.get("phone_number")));
        }
        if("revokeChat".equals(op)){Map<String,Object> grant=find("tbldevice_permission","permission_id",2);if(grant!=null)grant.put("is_active",false);}
        if(req.getParameter("view")!=null){res.sendRedirect(req.getContextPath()+"/MainController?action="+req.getParameter("view"));return;}
        if("row".equals(op)){
            Map<String,Object> r=find(req.getParameter("table"),req.getParameter("key"),req.getParameter("value"));out.put("found",r!=null);
            if(r!=null){out.putAll(r);Object secret=out.remove("password");if(secret!=null)out.put("password_hashed",String.valueOf(secret).startsWith("P1$"));}
        }else{
            for(String t:db.keySet())out.put(t,db.get(t).size());out.put("commands",commands.get());out.put("reads",reads.get());out.put("writes",writes.get());out.put("hw2",hardware.get(2));
        }
        boolean first=true;for(Map.Entry<String,Object> e:out.entrySet()){
            if(!first)res.getWriter().print('&');first=false;res.getWriter().print(URLEncoder.encode(e.getKey(),"UTF-8")+"="+URLEncoder.encode(String.valueOf(e.getValue()),"UTF-8"));
        }
    }
    @SuppressWarnings("unchecked")private static<T>T proxy(Class<T> t,InvocationHandler h){return(T)java.lang.reflect.Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h);}
    private static Map<String,List<Map<String,Object>>> snapshot(){Map<String,List<Map<String,Object>>> s=new LinkedHashMap<>();for(String t:db.keySet()){List<Map<String,Object>> l=new ArrayList<>();for(Map<String,Object> r:db.get(t))l.add(new LinkedHashMap<>(r));s.put(t,l);}return s;}
    public static Connection connection(){
        boolean[] auto={true};Object[] backup={null};
        return proxy(Connection.class,(o,m,a)->{
            switch(m.getName()){
                case"prepareStatement":return statement((String)a[0]);case"close":return null;case"getAutoCommit":return auto[0];
                case"setAutoCommit":if(!(Boolean)a[0]&&auto[0])backup[0]=snapshot();auto[0]=(Boolean)a[0];return null;
                case"commit":backup[0]=null;return null;
                case"rollback":if(backup[0]!=null){synchronized(AdminFixture.class){db.clear();db.putAll((Map<String,List<Map<String,Object>>>)backup[0]);}}return null;
                case"isClosed":return false;case"toString":return"Admin audit JDBC fixture";
            }throw new SQLException("Unsupported connection operation: "+m.getName());
        });
    }
    private static PreparedStatement statement(String sql){Map<Integer,Object> p=new HashMap<>();return proxy(PreparedStatement.class,(o,m,a)->{
        String n=m.getName();if(n.startsWith("set")&&a!=null&&a.length==2){p.put((Integer)a[0],a[1]);return null;}
        if("executeQuery".equals(n))return result(select(sql,p));if("executeUpdate".equals(n))return mutate(sql,p);if("close".equals(n)||"setQueryTimeout".equals(n))return null;
        throw new SQLException("Unsupported statement operation: "+n);
    });}
    private static boolean matches(String q,Map<String,Object> r,Map<Integer,Object> p){
        Matcher m=Pattern.compile("(?:[a-z]+\\.)?(user_id|user_name|switch_id|device_id|permission_id|schedul_id)\\s*=\\s*\\?").matcher(q);
        while(m.find()){int slot=1;for(int i=0;i<m.start();i++)if(q.charAt(i)=='?')slot++;if(!String.valueOf(r.get(m.group(1))).equals(String.valueOf(p.get(slot))))return false;}
        if(q.contains("role='admin'")&&!"ADMIN".equals(r.get("role")))return false;
        if(Pattern.compile("(?<![\\w.])is_active\\s*=\\s*1").matcher(q).find()&&!Boolean.TRUE.equals(r.get("is_active")))return false;
        return true;
    }
    private static synchronized List<Map<String,Object>> select(String sql,Map<Integer,Object> p)throws SQLException{
        String q=sql.toLowerCase(Locale.ROOT);Matcher from=Pattern.compile("\\bfrom\\s+(\\w+)").matcher(q);if(!from.find()||!db.containsKey(from.group(1)))throw new SQLException("Unsupported query: "+sql);
        String table=from.group(1);List<Map<String,Object>> out=new ArrayList<>();
        if(q.contains("count_big(*) over() as total_count")||q.startsWith("select esp.device_id from")||q.startsWith("select sw.switch_id from"))return chatSelect(q,table,p);
        for(Map<String,Object> stored:db.get(table)){
            Map<String,Object> r=new LinkedHashMap<>(stored);
            if("tblswitch".equals(table)){
                Map<String,Object> esp=find("tblesp32_device","device_id",r.get("device_id"));
                r.put("esp_name",esp.get("name"));r.put("esp_host_name",esp.get("host_name"));r.put("is_active_esp",esp.get("is_active"));
                if(Pattern.compile("sw\\.is_active\\s*=\\s*1").matcher(q).find()&&!Boolean.TRUE.equals(r.get("is_active")))continue;
                if(Pattern.compile("esp\\.is_active\\s*=\\s*1").matcher(q).find()&&!Boolean.TRUE.equals(esp.get("is_active")))continue;
                if(q.contains("join tbldevice_permission")){
                    boolean permitted=false;for(Map<String,Object> grant:db.get("tbldevice_permission"))if(grant.get("switch_id").equals(r.get("switch_id"))&&grant.get("user_id").equals(p.get(1))&&Boolean.TRUE.equals(grant.get("is_active")))permitted=true;
                    if(!permitted||!Boolean.TRUE.equals(r.get("is_active"))||!Boolean.TRUE.equals(esp.get("is_active")))continue;
                    out.add(r);continue;
                }
            }
            if(matches(q,r,p))out.add(r);
        }
        return out;
    }
    /** Dedicated SELECT-only projection fixture for the chat DAO's bounded, scoped queries. */
    private static List<Map<String,Object>> chatSelect(String q,String table,Map<Integer,Object> p) {
        List<Map<String,Object>> rows=new ArrayList<>();
        if(q.contains("auth.user_id=?")) {
            int at=q.contains("p.user_id=?")?2:1;Map<String,Object> actor=find("tbluser","user_id",p.get(at));
            if(actor==null||!Boolean.TRUE.equals(actor.get("is_active"))||!Objects.equals(actor.get("role"),p.get(at+1)))return rows;
        }
        for(Map<String,Object> raw:db.get(table)) {
            Map<String,Object> sw="tblswitch".equals(table)?raw:find("tblswitch","switch_id",raw.get("switch_id"));
            Map<String,Object> esp="tblesp32_device".equals(table)?raw:sw==null?null:find("tblesp32_device","device_id",sw.get("device_id"));
            if(esp==null||!Boolean.TRUE.equals(esp.get("is_active")))continue;
            if(!"tblesp32_device".equals(table)&&(sw==null||!Boolean.TRUE.equals(sw.get("is_active"))))continue;
            if("tblschedule".equals(table)&&!Boolean.TRUE.equals(raw.get("is_active")))continue;
            if(q.contains("p.user_id=?")) {
                boolean permitted=false;
                for(Map<String,Object> grant:db.get("tbldevice_permission")) {
                    Map<String,Object> grantedSwitch=find("tblswitch","switch_id",grant.get("switch_id"));
                    if(grantedSwitch!=null&&Boolean.TRUE.equals(grantedSwitch.get("is_active"))&&Boolean.TRUE.equals(grant.get("is_active"))&&grant.get("user_id").equals(p.get(1))
                            &&("tblesp32_device".equals(table)?esp.get("device_id").equals(grantedSwitch.get("device_id")):sw.get("switch_id").equals(grantedSwitch.get("switch_id"))))permitted=true;
                }
                if(!permitted)continue;
            }
            Map<String,Object> row=new LinkedHashMap<>(raw);row.put("device_id",esp.get("device_id"));row.put("device_name",esp.get("name"));row.put("device_state",esp.get("status"));
            if(sw!=null){row.put("switch_id",sw.get("switch_id"));row.put("switch_name",sw.get("switch_name"));row.put("gpio_pin",sw.get("gpio_pin"));row.put("switch_state",sw.get("status"));}
            rows.add(row);
        }
        String sort="tblesp32_device".equals(table)?"device_id":"tblschedule".equals(table)?"run_time":"tblcontrol_history".equals(table)?"control_time":"switch_id";
        rows.sort((a,b)->String.valueOf(a.get(sort)).compareTo(String.valueOf(b.get(sort))));if("tblcontrol_history".equals(table))Collections.reverse(rows);
        long online=rows.stream().filter(r->"ONLINE".equals(r.get("device_state"))).count(),on=rows.stream().filter(r->"ON".equals(r.get("switch_state"))).count(),enabled=rows.stream().filter(r->Boolean.TRUE.equals(r.get("is_enabled"))).count();
        long offline=rows.stream().filter(r->"OFFLINE".equals(r.get("device_state"))).count(),error=rows.stream().filter(r->"ERROR".equals(r.get("device_state"))).count(),off=rows.stream().filter(r->"OFF".equals(r.get("switch_state"))).count();
        for(Map<String,Object> row:rows){row.put("total_count",(long)rows.size());row.put("online_count",online);row.put("offline_count",offline);row.put("error_count",error);row.put("on_count",on);row.put("off_count",off);row.put("enabled_count",enabled);}
        if(q.contains("top (12)")&&rows.size()>12)return new ArrayList<>(rows.subList(0,12));
        return rows;
    }
    /** Constraints mirror the live metadata SELECT, not guessed database rules. */
    private static void unique(String table,Map<String,Object> candidate,Map<String,Object> old)throws SQLException{
        String[][] keys="tbluser".equals(table)?new String[][]{{"user_id"},{"user_name"},{"email"},{"phone_number"}}:
            "tblesp32_device".equals(table)?new String[][]{{"device_id"},{"host_name"}}:
            "tblswitch".equals(table)?new String[][]{{"switch_id"},{"device_id","gpio_pin"}}:
            "tbldevice_permission".equals(table)?new String[][]{{"permission_id"},{"user_id","switch_id"}}:new String[0][];
        for(Map<String,Object> r:db.get(table))if(r!=old)for(String[] key:keys){boolean same=true;for(String c:key)same &= Objects.equals(r.get(c),candidate.get(c));if(same)throw new SQLException("Fixture duplicate key","23000",2627);}
    }
    private static synchronized int mutate(String sql,Map<Integer,Object> p)throws SQLException{
        Matcher ins=Pattern.compile("(?i)^INSERT INTO (\\w+)\\s*\\(([^)]+)\\)").matcher(sql);
        if(ins.find()){
            String table=ins.group(1).toLowerCase(Locale.ROOT);String[] cols=ins.group(2).split(",");Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<cols.length;i++)r.put(cols[i].trim(),p.get(i+1));
            String auto="tblschedule".equals(table)?"schedul_id":"tblcontrol_history".equals(table)?"history_id":"tbldevice_permission".equals(table)?"permission_id":null;
            if(auto!=null&&!r.containsKey(auto)){int max=0;for(Map<String,Object> item:db.get(table))max=Math.max(max,((Number)item.get(auto)).intValue());r.put(auto,max+1);}
            unique(table,r,null);db.get(table).add(r);writes.incrementAndGet();return 1;
        }
        Matcher update=Pattern.compile("(?i)^UPDATE (\\w+) SET (.+?) WHERE (.+)$").matcher(sql);
        if(!update.find()||!db.containsKey(update.group(1).toLowerCase(Locale.ROOT)))throw new SQLException("Unsupported write: "+sql);
        String table=update.group(1).toLowerCase(Locale.ROOT),where=update.group(3).toLowerCase(Locale.ROOT);String[] sets=update.group(2).split(",");int slots=0;for(String set:sets)if(set.contains("?"))slots++;
        Map<Integer,Object> filter=new HashMap<>();for(int i=slots+1;i<=p.size();i++)filter.put(i-slots,p.get(i));int changed=0;
        for(Map<String,Object> r:db.get(table))if(matches(where,r,filter)){
            Map<String,Object> next=new LinkedHashMap<>(r);int at=1;for(String set:sets){String[] pair=set.trim().split("\\s*=\\s*");String col=pair[0].trim().toLowerCase(Locale.ROOT);Object value=pair[1].equals("?")?p.get(at++):pair[1].equals("1")?true:pair[1].equals("0")?false:pair[1];next.put(col,value);}
            unique(table,next,r);r.clear();r.putAll(next);changed++;writes.incrementAndGet();
        }
        return changed;
    }
    private static ResultSet result(List<Map<String,Object>> rows){int[] at={-1};return proxy(ResultSet.class,(o,m,a)->{
        String n=m.getName();if("next".equals(n))return ++at[0]<rows.size();if("close".equals(n))return null;
        if(n.startsWith("get")&&a!=null){Object v=rows.get(at[0]).get(String.valueOf(a[0]).toLowerCase(Locale.ROOT));
            if("getString".equals(n))return v==null?null:String.valueOf(v);if("getBoolean".equals(n))return Boolean.TRUE.equals(v);if("getInt".equals(n))return v==null?0:((Number)v).intValue();if("getLong".equals(n))return v==null?0L:((Number)v).longValue();if("getTime".equals(n)||"getTimestamp".equals(n))return v;}
        throw new SQLException("Unsupported ResultSet operation: "+n);
    });}
}
