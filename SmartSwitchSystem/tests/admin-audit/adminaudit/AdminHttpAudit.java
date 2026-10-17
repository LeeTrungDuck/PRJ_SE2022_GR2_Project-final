package adminaudit;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Acceptance probes A1-A10. Failing expectations remain failures in the report. */
public final class AdminHttpAudit {
    private final String base;private final Path evidence;private String cookie="";private int passed,failed;
    private final List<String> results=new ArrayList<>();
    private static final class Response{int status;String body,location;}
    private AdminHttpAudit(String url,String dir)throws IOException{base=url;evidence=Paths.get(dir);Files.createDirectories(evidence);}
    private static String enc(String text)throws Exception{return URLEncoder.encode(text,"UTF-8");}
    private static String form(String...pairs)throws Exception{StringBuilder s=new StringBuilder();for(int i=0;i<pairs.length;i+=2){if(s.length()>0)s.append('&');s.append(enc(pairs[i])).append('=').append(enc(pairs[i+1]));}return s.toString();}
    private Response request(String endpoint,String post)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+endpoint).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(20000);
        if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        if(post!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream o=c.getOutputStream()){o.write(post.getBytes(StandardCharsets.UTF_8));}}
        Response r=new Response();r.status=c.getResponseCode();r.location=c.getHeaderField("Location");String sc=c.getHeaderField("Set-Cookie");if(sc!=null)cookie=sc.split(";",2)[0];
        InputStream in=r.status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(in!=null)try(InputStream stream=in){byte[] b=new byte[4096];int n;while((n=stream.read(b))!=-1)bytes.write(b,0,n);}
        r.body=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();return r;
    }
    private Response page(String action)throws Exception{return request("/MainController?action="+action,null);}
    private void reset(String mode,String actor)throws Exception{request("/__admin?op=reset&mode="+mode+"&actor="+actor,null);}
    private Map<String,String> fields(Response r)throws Exception{Map<String,String> m=new LinkedHashMap<>();for(String pair:r.body.split("&")){String[] a=pair.split("=",2);if(a.length==2)m.put(URLDecoder.decode(a[0],"UTF-8"),URLDecoder.decode(a[1],"UTF-8"));}return m;}
    private Map<String,String> row(String table,String key,String value)throws Exception{return fields(request("/__admin?op=row&table="+table+"&key="+key+"&value="+enc(value),null));}
    private Map<String,String> stats()throws Exception{return fields(request("/__admin",null));}
    private String token(String action)throws Exception{
        Response r=request("/MainController?action="+action+("USER_MANAGER".equals(action)?"&form=add":""),null);
        Matcher m=Pattern.compile("name=\"csrf\"\\s+value=\"([^\"]+)\"").matcher(r.body);
        if(!m.find()){save("missing-token-"+action,r);throw new AssertionError("CSRF token missing: "+action+" HTTP "+r.status);}return m.group(1);
    }
    private Response post(String action,String csrf,String...pairs)throws Exception{return request("/MainController",form("action",action,"csrf",csrf)+"&"+form(pairs));}
    private void save(String name,Response r)throws IOException{Files.write(evidence.resolve(name+".html"),r.body.getBytes(StandardCharsets.UTF_8));}
    private void check(String id,String label,boolean ok,String actual){String line=id+"\t"+(ok?"PASS":"FAIL")+"\t"+label+"\t"+actual;results.add(line);System.out.println(line);if(ok)passed++;else failed++;}
    private void run()throws Exception{
        reset("normal","U1");Response r=page("DEVICE_CONTROL");save("admin-device-control",r);
        check("A1","Admin sees all active switches",r.status==200&&r.body.contains("value=\"SW1\"")&&r.body.contains("value=\"SW2\"")&&r.body.contains("value=\"SW3\"")&&!r.body.contains("value=\"SW4\"")&&!r.body.contains("value=\"SW5\""),"HTTP="+r.status+"; active switch/ESP32 filter");
        check("A2","Admin sees SW2 without a personal grant",r.body.contains("value=\"SW2\"")&&"SW1".equals(row("tbldevice_permission","permission_id","1").get("switch_id"))&&"2".equals(stats().get("tbldevice_permission")),"SW2 is visible; original grants are U1/SW1 and U2/SW1");
        r=post("CHANGE_SWITCH_STAGE","","id","SW1");Map<String,String> s=stats();
        check("A3","Switch OFF to ON updates simulated hardware, DB and history",r.status==302&&"ON".equals(s.get("hw2"))&&"ON".equals(row("tblswitch","switch_id","SW1").get("status"))&&"1".equals(s.get("tblcontrol_history")),s.toString());
        r=post("CHANGE_SWITCH_STAGE","","id","SW1");s=stats();
        check("A3","Switch ON to OFF appends history",r.status==302&&"OFF".equals(s.get("hw2"))&&"OFF".equals(row("tblswitch","switch_id","SW1").get("status"))&&"2".equals(s.get("tblcontrol_history")),s.toString());
        reset("falseAck","U1");r=post("CHANGE_SWITCH_STAGE","","id","SW1");s=stats();
        check("A3","Reject an HTTP 200 false acknowledgement",s.get("hw2").equals(row("tblswitch","switch_id","SW1").get("status"))&&"0".equals(s.get("tblcontrol_history")),s+"; db="+row("tblswitch","switch_id","SW1").get("status"));
        reset("normal","U1");r=page("USER_MANAGER");save("admin-user-manager",r);
        check("A4","User Management page and sidebar are available",r.status==200&&r.body.contains("action=USER_MANAGER")&&r.body.contains("Add User")&&r.body.contains("Audit User 2"),"HTTP="+r.status);
        String csrf=token("USER_MANAGER");int k=0;
        for(String role:new String[]{"ADMIN","OPERATOR","VIEWER"}){
            String username="created_"+role.toLowerCase(Locale.ROOT);String phone="090010000"+(++k);
            r=post("USER_MANAGER",csrf,"uiOp","addUser","formName","add","fullName","Created "+role,"userName",username,"password","Password12!","role",role,"email",username+"@example.invalid","phone",phone);
            Map<String,String> u=row("tbluser","user_name",username);
            check("A5","Create "+role+" with a hashed password",r.status==303&&role.equals(u.get("role"))&&"true".equals(u.get("is_active"))&&"true".equals(u.get("password_hashed")),"HTTP="+r.status+"; "+u);
            AdminHttpAudit client=new AdminHttpAudit(base,evidence.toString());Response login=client.request("/MainController",form("action","LOGIN","userName",username,"passWord","Password12!"));
            check("A5","New "+role+" can sign in",login.status==302&&login.location!=null&&login.location.contains("DEVICE_CONTROL"),"HTTP="+login.status+"; Location="+login.location);
        }
        reset("normal","U1");csrf=token("USER_MANAGER");
        r=post("USER_MANAGER",csrf,"uiOp","updateUser","id","U2","fullName","Edited Viewer","email","edited@example.invalid","phone","0900000002","role","VIEWER");
        check("A6","Edit a normal account",r.status==303&&"Edited Viewer".equals(row("tbluser","user_id","U2").get("full_name")),"HTTP="+r.status);
        r=post("USER_MANAGER",csrf,"uiOp","toggleUser","id","U2");check("A6","Lock a normal account",r.status==303&&"false".equals(row("tbluser","user_id","U2").get("is_active")),"HTTP="+r.status);
        r=post("USER_MANAGER",csrf,"uiOp","toggleUser","id","U2");check("A6","Unlock a normal account",r.status==303&&"true".equals(row("tbluser","user_id","U2").get("is_active")),"HTTP="+r.status);
        r=post("USER_MANAGER",csrf,"uiOp","deleteUser","id","U2");Map<String,String> u=row("tbluser","user_id","U2");
        check("A6","Soft delete retains account and revokes its grant",r.status==303&&"true".equals(u.get("found"))&&"false".equals(u.get("is_active"))&&"false".equals(row("tbldevice_permission","permission_id","2").get("is_active")),"HTTP="+r.status+"; account retained, grant inactive");
        for(String op:new String[]{"deleteUser","toggleUser","changeRole"}){
            reset("normal","U1");csrf=token("USER_MANAGER");r=post("USER_MANAGER",csrf,"uiOp",op,"id","U1","role","OPERATOR");u=row("tbluser","user_id","U1");
            check("A6","Reject self change: "+op,r.status!=303&&"true".equals(u.get("is_active"))&&"ADMIN".equals(u.get("role")),"HTTP="+r.status+"; "+u);
            reset("normal","U4");csrf=token("USER_MANAGER");r=post("USER_MANAGER",csrf,"uiOp",op,"id","U1","role","OPERATOR");u=row("tbluser","user_id","U1");save("original-admin-"+op,r);
            check("A6","Protect original Admin U1 from another Admin: "+op,r.status!=303&&"true".equals(u.get("is_active"))&&"ADMIN".equals(u.get("role")),"HTTP="+r.status+"; "+u);
        }
        reset("normal","U1");csrf=token("PERMISSION_MANAGER");
        r=post("PERMISSION_MANAGER",csrf,"permissionFormat","active-v1","uiOp","savePermissions","viewer","U2","access_SW2","on");
        Map<String,String> grant=row("tbldevice_permission","permission_id","3");
        check("A7","Grant and save SW2 access for Viewer",r.status==303&&"SW2".equals(grant.get("switch_id"))&&"U2".equals(grant.get("user_id"))&&"U1".equals(grant.get("granted_by"))&&"true".equals(grant.get("is_active")),grant.toString());
        check("A7","Unchecked SW1 access is soft revoked","false".equals(row("tbldevice_permission","permission_id","2").get("is_active")),row("tbldevice_permission","permission_id","2").toString());
        r=post("PERMISSION_MANAGER",csrf,"permissionFormat","active-v1","uiOp","revokePermissions","viewer","U2");
        check("A7","Revoke draft does not prematurely write database",r.status==303&&"true".equals(row("tbldevice_permission","permission_id","3").get("is_active")),"Draft only until Save Permissions");
        r=post("PERMISSION_MANAGER",csrf,"permissionFormat","active-v1","uiOp","savePermissions","viewer","U2");
        check("A7","Save revoke retains permission row as inactive",r.status==303&&"false".equals(row("tbldevice_permission","permission_id","3").get("is_active")),row("tbldevice_permission","permission_id","3").toString());
        r=post("PERMISSION_MANAGER",csrf,"permissionFormat","active-v1","uiOp","savePermissions","viewer","U2","access_SW2","on");
        check("A7","Re-grant reuses the unique permission row",r.status==303&&"3".equals(stats().get("tbldevice_permission"))&&"true".equals(row("tbldevice_permission","permission_id","3").get("is_active")),stats().toString());
        reset("normal","U1");
        for(String name:new String[]{"UserManagerController","PermissionManagementController","DeviceManagementController","ScheduleManagementController","AccountManagementController"}){
            r=request("/"+name,null);save("direct-"+name,r);
            check("A8","Admin direct URL /"+name,r.status==200&&!r.body.contains("name=\"userName\" autocomplete=\"username\""),"HTTP="+r.status);
        }
        for(String action:new String[]{"USER_MANAGER","PERMISSION_MANAGER","DEVICE_MANAGER","SCHEDULE_MANAGER","ACCOUNT_MANAGER"}){
            r=page(action);check("A8","Admin MainController route "+action,r.status==200,"HTTP="+r.status);
        }
        csrf=token("DEVICE_MANAGER");
        r=post("DEVICE_MANAGER",csrf,"uiOp","addDevice","deviceId","ESPTEMP","deviceName","New audit ESP","hostName","192.0.2.99");
        check("A9","Create ESP32",r.status==303&&"true".equals(row("tblesp32_device","device_id","ESPTEMP").get("is_active")),"HTTP="+r.status);
        r=post("DEVICE_MANAGER",csrf,"uiOp","updateDevice","deviceId","ESPTEMP","deviceName","Renamed audit ESP");
        check("A9","Rename ESP32",r.status==303&&"Renamed audit ESP".equals(row("tblesp32_device","device_id","ESPTEMP").get("name")),"HTTP="+r.status);
        r=post("DEVICE_MANAGER",csrf,"uiOp","addSwitch","switchId","SWTEMP","switchDeviceId","ESPTEMP","switchName","New audit relay","gpioPin","23");
        check("A9","Create switch",r.status==303&&"23".equals(row("tblswitch","switch_id","SWTEMP").get("gpio_pin")),"HTTP="+r.status);
        r=post("DEVICE_MANAGER",csrf,"uiOp","updateSwitch","switchId","SWTEMP","switchName","Renamed audit relay");
        check("A9","Rename switch",r.status==303&&"Renamed audit relay".equals(row("tblswitch","switch_id","SWTEMP").get("switch_name")),"HTTP="+r.status);
        r=post("DEVICE_MANAGER",csrf,"uiOp","addSwitch","switchId","SWDUP","switchDeviceId","ESP1","switchName","Duplicate GPIO","gpioPin","2");save("duplicate-gpio",r);
        check("A9","Reject duplicate GPIO within one ESP32",r.status!=303&&"false".equals(row("tblswitch","switch_id","SWDUP").get("found"))&&r.body.contains("already exists"),"HTTP="+r.status+"; unique device_id + gpio_pin fixture mirrors SQL metadata");
        r=post("DEVICE_MANAGER",csrf,"uiOp","addSwitch","switchId","SWOTHER","switchDeviceId","ESP2","switchName","Other ESP same GPIO","gpioPin","2");
        check("A9","Allow same GPIO on a different ESP32",r.status==303&&"true".equals(row("tblswitch","switch_id","SWOTHER").get("found")),"HTTP="+r.status);
        r=post("DEVICE_MANAGER",csrf,"uiOp","addDevice","deviceId","ESPDUP","deviceName","Duplicate host","hostName","192.0.2.10");save("duplicate-host",r);
        check("A9","Reject identical hostname/IP text",r.status!=303&&"false".equals(row("tblesp32_device","device_id","ESPDUP").get("found"))&&r.body.contains("already exists"),"HTTP="+r.status+"; unique host_name fixture mirrors SQL metadata");
        r=post("DEVICE_MANAGER",csrf,"uiOp","addDevice","deviceId","ESPALIAS","deviceName","Same IP with scheme","hostName","http://192.0.2.10");
        check("A9","Reject same IP expressed with an HTTP scheme",r.status!=303&&"false".equals(row("tblesp32_device","device_id","ESPALIAS").get("found")),"HTTP="+r.status+"; "+row("tblesp32_device","device_id","ESPALIAS"));
        r=post("DEVICE_MANAGER",csrf,"uiOp","deleteSwitch","switchId","SWTEMP","confirm","yes");
        check("A9","Soft delete switch",r.status==303&&"false".equals(row("tblswitch","switch_id","SWTEMP").get("is_active")),"HTTP="+r.status);
        r=post("DEVICE_MANAGER",csrf,"uiOp","deleteDevice","deviceId","ESPTEMP","confirm","yes");
        check("A9","Soft delete ESP32",r.status==303&&"false".equals(row("tblesp32_device","device_id","ESPTEMP").get("is_active")),"HTTP="+r.status);
        reset("normal","U1");csrf=token("SCHEDULE_MANAGER");
        r=post("SCHEDULE_MANAGER",csrf,"uiOp","addSchedule","switchId","SW1","scheduleAction","ON","runTime","07:45","enabled","true");
        check("A10","Create enabled schedule",r.status==303&&"true".equals(row("tblschedule","schedul_id","2").get("is_enabled"))&&"U1".equals(row("tblschedule","schedul_id","2").get("user_id")),"HTTP="+r.status);
        r=post("SCHEDULE_MANAGER",csrf,"uiOp","updateSchedule","scheduleId","2","switchId","SW1","scheduleAction","OFF","runTime","08:15","enabled","true");
        check("A10","Edit schedule command and time",r.status==303&&"OFF".equals(row("tblschedule","schedul_id","2").get("action"))&&"08:15:00".equals(row("tblschedule","schedul_id","2").get("run_time")),row("tblschedule","schedul_id","2").toString());
        r=post("SCHEDULE_MANAGER",csrf,"uiOp","updateSchedule","scheduleId","2","switchId","SW1","scheduleAction","OFF","runTime","08:15");
        check("A10","Disable schedule via update",r.status==303&&"false".equals(row("tblschedule","schedul_id","2").get("is_enabled")),"HTTP="+r.status);
        r=post("SCHEDULE_MANAGER",csrf,"uiOp","updateSchedule","scheduleId","2","switchId","SW1","scheduleAction","OFF","runTime","08:15","enabled","true");
        check("A10","Enable schedule via update",r.status==303&&"true".equals(row("tblschedule","schedul_id","2").get("is_enabled")),"HTTP="+r.status);
        Response schedules=page("SCHEDULE_MANAGER");save("admin-schedules",schedules);
        r=post("SCHEDULE_MANAGER",csrf,"uiOp","runSchedule","scheduleId","2");s=stats();save("schedule-run-attempt",r);
        check("A10","Run now schedule action exists and sends a command",r.status==303&&Integer.parseInt(s.get("commands"))>0,"HTTP="+r.status+"; invalid operation="+r.body.contains("Invalid schedule operation.")+"; commands="+s.get("commands"));
        r=post("SCHEDULE_MANAGER",csrf,"uiOp","deleteSchedule","scheduleId","2","confirm","yes");
        check("A10","Soft delete schedule",r.status==303&&"true".equals(row("tblschedule","schedul_id","2").get("found"))&&"false".equals(row("tblschedule","schedul_id","2").get("is_active")),row("tblschedule","schedul_id","2").toString());
        Files.write(evidence.resolve("results.tsv"),results,StandardCharsets.UTF_8);
        System.out.println("ADMIN AUDIT: "+passed+" passed, "+failed+" failed; production HTTP/controllers/DAOs/JSP with isolated JDBC and ESP32 fixtures. No live writes.");
    }
    public static void main(String[] args)throws Exception{AdminHttpAudit a=new AdminHttpAudit(args[0],args[1]);a.run();if(a.failed>0)System.exit(1);}
}
