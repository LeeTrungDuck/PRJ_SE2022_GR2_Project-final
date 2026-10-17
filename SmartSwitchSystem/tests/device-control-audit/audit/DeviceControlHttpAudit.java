package audit;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Real HTTP requests to an isolated Tomcat; failures are reported, never hidden. */
public final class DeviceControlHttpAudit {
    private final String base;
    private final Path evidence;
    private volatile String cookie="";
    private int passed,failed;
    private final List<String> log=new ArrayList<>();
    private static final String LIST="/MainController?action=DEVICE_CONTROL";
    private static final String TOGGLE="action=CHANGE_SWITCH_STAGE&id=SW1&page=1";
    private DeviceControlHttpAudit(String url,String dir)throws IOException {base=url;evidence=Paths.get(dir);Files.createDirectories(evidence);}
    private static final class Response { int status;String body,location; }
    private Response request(String path,String post)throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setInstanceFollowRedirects(false);c.setConnectTimeout(5000);c.setReadTimeout(20000);
        if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        if(post!=null) {
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");
            try(OutputStream out=c.getOutputStream()){out.write(post.getBytes(StandardCharsets.UTF_8));}
        }
        Response r=new Response();r.status=c.getResponseCode();r.location=c.getHeaderField("Location");
        String session=c.getHeaderField("Set-Cookie");if(session!=null)cookie=session.split(";",2)[0];
        InputStream in=r.status>=400?c.getErrorStream():c.getInputStream();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(in!=null)try(InputStream stream=in){byte[] b=new byte[4096];int n;while((n=stream.read(b))!=-1)bytes.write(b,0,n);}
        r.body=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();return r;
    }
    private void reset(String mode,String role)throws Exception {request("/__audit?op=reset&mode="+mode+"&role="+role,null);}
    private Map<String,String> stats()throws Exception {
        Map<String,String> values=new LinkedHashMap<>();
        for(String part:request("/__audit",null).body.split(";")){String[] pair=part.split("=",2);values.put(pair[0],pair[1]);}return values;
    }
    private void check(int tc,String label,boolean ok,String observed) {
        String line="TC"+tc+"\t"+(ok?"PASS":"FAIL")+"\t"+label+"\t"+observed;
        log.add(line);System.out.println(line);if(ok)passed++;else failed++;
    }
    private void save(String name,Response r)throws IOException {
        Files.write(evidence.resolve(name+".html"),r.body.getBytes(StandardCharsets.UTF_8));
    }
    private List<String> cards(Response r) {
        List<String> values=new ArrayList<>();Matcher m=Pattern.compile("<article class=\"dc-relay-card\"[\\s\\S]*?</article>").matcher(r.body);
        while(m.find())values.add(m.group());return values;
    }
    private String text(String html) {return html.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();}
    private boolean summary(Response r,int first,int last,int total) {
        return text(r.body).contains("Showing "+first+"\u2013"+last+" of "+total+" switch");
    }
    private boolean every(List<String> cards,String needle) {if(cards.isEmpty())return false;for(String card:cards)if(!card.contains(needle))return false;return true;}
    private Response follow(Response r)throws Exception {
        if(r.location==null)return r;
        URL dest=new URL(new URL(base),r.location);return request(dest.toString().substring(base.length()),null);
    }
    private void run()throws Exception {
        reset("normal","anonymous");
        for(String url:new String[]{LIST,"/DeviceControlController"}) {
            Response r=request(url,null);save("anonymous",r);
            check(1,"Anonymous "+url,r.status==200&&r.body.contains("name=\"userName\"")&&!r.body.contains("dc-relay-card"),"HTTP="+r.status+", Login rendered="+r.body.contains("name=\"userName\""));
        }
        reset("normal","ADMIN");Response r=request(LIST,null);save("list",r);
        check(2,"Names / GPIO / ESP32 / DB state / active records",r.status==200&&cards(r).size()==20&&r.body.contains("Audit switch 1")&&r.body.contains("GPIO 1")&&r.body.contains("ESP1")&&cards(r).get(0).contains("data-on=\"true\"")&&summary(r,1,20,45),"cards="+cards(r).size()+"; 45 active of 47 fixture rows");
        reset("normal","VIEWER");r=request(LIST,null);
        check(2,"Viewer sees only active permissions",cards(r).size()==2&&r.body.contains("value=\"SW1\"")&&r.body.contains("value=\"SW3\"")&&!r.body.contains("value=\"SW2\""),"cards="+cards(r).size());
        reset("normal","OPERATOR");r=request(LIST,null);
        check(2,"Operator sees active switch list",summary(r,1,20,45),"20 / 45 active switches");
        reset("mismatch","ADMIN");r=request(LIST,null);r=request(LIST,null);Map<String,String>s=stats();save("stale-state",r);
        check(3,"Reload synchronizes changed hardware",cards(r).get(0).contains("data-on=\"false\"")&&s.get("db1").equals(s.get("hw1")),s.toString());
        reset("normal","ADMIN");
        r=request(LIST+"&esp=ESP1",null);check(4,"ESP1 filter",summary(r,1,20,23)&&every(cards(r),"ESP1"),"ESP1: "+cards(r).size()+" cards, total 23");
        r=request(LIST+"&esp=ESP2",null);check(4,"ESP2 filter",summary(r,1,20,22)&&every(cards(r),"ESP2"),"ESP2: "+cards(r).size()+" cards, total 22");
        r=request(LIST+"&status=ON",null);check(5,"ON filter",summary(r,1,20,23)&&every(cards(r),"data-on=\"true\""),"total 23 ON");
        r=request(LIST+"&status=OFF",null);check(5,"OFF filter",summary(r,1,20,22)&&every(cards(r),"data-on=\"false\""),"total 22 OFF");
        r=request(LIST+"&status=all",null);check(5,"All filter",summary(r,1,20,45),"total 45");
        for(String status:new String[]{"ON","OFF"}) {
            r=request(LIST+"&esp=ESP2&status="+status,null);
            check(6,"ESP2 + "+status,summary(r,1,11,11)&&every(cards(r),"ESP2")&&every(cards(r),"data-on=\""+status.equals("ON")+"\""),"total 11");
        }
        check(7,"Clear filters link resets page + filters",r.body.contains("href=\"/SmartSwitchSystem/DeviceControlController\">Clear filters")&&summary(request("/DeviceControlController",null),1,20,45),"Clear link -> 20 / 45, page 1");
        reset("normal","ADMIN");r=request("/MainController",TOGGLE);s=stats();Response after=follow(r);save("toggle-success",after);
        check(8,"Toggle changes simulated hardware, writes state and reloads UI",r.status==302&&"OFF".equals(s.get("db1"))&&"OFF".equals(s.get("hw1"))&&"1".equals(s.get("writes"))&&cards(after).get(0).contains("data-on=\"false\""),s.toString());
        Response historyPage=request("/MainController?action=CONTROL_HISTORY",null);save("history",historyPage);
        check(10,"Success writes history and appears in history UI","1".equals(s.get("history"))&&historyPage.status==200&&historyPage.body.contains("SW1")&&historyPage.body.contains("OFF"),"history="+s.get("history")+"; history page HTTP="+historyPage.status);
        r=request("/MainController",TOGGLE);s=stats();after=follow(r);
        check(8,"Toggle OFF back to ON",r.status==302&&"ON".equals(s.get("db1"))&&"ON".equals(s.get("hw1"))&&"2".equals(s.get("writes"))&&cards(after).get(0).contains("data-on=\"true\""),s.toString());
        check(10,"Second success appends history","2".equals(s.get("history")),s.toString());
        for(String role:new String[]{"VIEWER","OPERATOR"}) {
            reset("normal",role);r=request("/MainController",TOGGLE);s=stats();
            check(8,"Permitted "+role+" can toggle","1".equals(s.get("commands"))&&"OFF".equals(s.get("db1"))&&"OFF".equals(s.get("hw1")),s.toString());
        }
        reset("falseAck","ADMIN");r=request("/MainController",TOGGLE);s=stats();
        check(8,"Hardware HTTP 200 with ok:false is rejected",s.get("db1").equals(s.get("hw1"))&&"0".equals(s.get("writes")),s.toString());
        for(String mode:new String[]{"offline","invalid"}) {
            reset(mode,"ADMIN");r=request(LIST,null);s=stats();
            check(9,"Reload reports "+mode+" hardware",r.body.contains("dc-sync-error")||r.body.contains("role=\"alert\""),"hardware reads="+s.get("reads")+"; no error on reload");
            r=request("/MainController",TOGGLE);after=follow(r);s=stats();save("error-"+mode,after);
            check(9,"Toggle reports "+mode+" hardware",after.status==200&&after.body.contains("Unable to connect to or communicate with ESP32")&&"0".equals(s.get("commands"))&&"0".equals(s.get("writes")),s.toString());
        }
        reset("normal","ADMIN");Response first=request(LIST,null),second=request(LIST+"&page=2",null),last=request(LIST+"&page=3",null);save("pagination-last",last);
        check(11,"Pagination 20 / 20 / 5 with Previous, Next and page numbers",summary(first,1,20,45)&&summary(second,21,40,45)&&summary(last,41,45,45)&&cards(last).size()==5&&!text(first.body).contains("Previous")&&text(first.body).contains("Next")&&text(second.body).contains("Previous")&&text(second.body).contains("Next")&&text(last.body).contains("Previous")&&!text(last.body).contains("Next"),"page 1=20; page 2=20; page 3=5");
        r=request(LIST+"&esp=ESP1&status=all",null);
        Matcher next=Pattern.compile("href=\"([^\"]+)\"[^>]*>\\s*Next\\s*</a>").matcher(r.body);
        boolean nextOk=false,previousOk=false;
        if(next.find()) {
            String link=next.group(1).replace("&amp;","&");
            Response viaNext=request(link.substring("/SmartSwitchSystem".length()),null);
            nextOk=summary(viaNext,21,23,23)&&every(cards(viaNext),"ESP1")&&link.contains("esp=ESP1")&&link.contains("status=ALL");
            Matcher previous=Pattern.compile("href=\"([^\"]+)\"[^>]*>\\s*Previous\\s*</a>").matcher(viaNext.body);
            if(previous.find())previousOk=summary(request(previous.group(1).replace("&amp;","&").substring("/SmartSwitchSystem".length()),null),1,20,23);
        }
        check(11,"Follow real Next and Previous links with filters",nextOk&&previousOk,"next="+nextOk+"; previous="+previousOk);
        r=request(LIST+"&esp=ESP1&status=ON&page=2",null);check(11,"Pagination clamps and preserves filters",summary(r,1,12,12)&&every(cards(r),"ESP1")&&every(cards(r),"data-on=\"true\""),"ESP1 + ON -> 12, page 1");
        reset("empty","ADMIN");r=request(LIST,null);save("empty",r);check(12,"Empty database renders safely",r.status==200&&cards(r).isEmpty()&&!r.body.contains("Exception"),"HTTP="+r.status+"; cards="+cards(r).size());
        reset("normal","ADMIN");r=request(LIST+"&esp=NO_SUCH_ESP",null);check(12,"No filter results renders safely",r.status==200&&cards(r).isEmpty(),"HTTP="+r.status+"; cards="+cards(r).size());
        for(String page:new String[]{"abc","-1","0","2147483648","999"}) {
            r=request(LIST+"&page="+page,null);boolean end="999".equals(page);
            check(13,"Invalid/out-of-range page="+page,r.status==200&&summary(r,end?41:1,end?45:20,45),"HTTP="+r.status+"; clamped/fallback");
        }
        r=request(LIST+"&status=garbage",null);check(13,"Invalid status rejected with visible error",r.status==200&&r.body.contains("Invalid status filter.")&&summary(r,1,20,45),"error shown; fallback All");
        r=request(LIST+"&status=on",null);check(13,"Lowercase status normalized",summary(r,1,20,23)&&every(cards(r),"data-on=\"true\""),"on -> ON");
        for(String esp:new String[]{"NO_SUCH_ESP","%3Cscript%3Eaudit%3C%2Fscript%3E"}) {
            r=request(LIST+"&esp="+esp,null);
            check(13,"Invalid ESP handled safely: "+esp,r.status==200&&cards(r).isEmpty()&&!r.body.contains("Exception"),"HTTP="+r.status+"; cards="+cards(r).size()+"; empty result without validation message");
            check(14,"ESP filter cannot inject raw script",!r.body.contains("<script>audit</script>"),"escaped in rendered attributes");
        }
        for(String path:new String[]{"/SwitchStageController?id=SW1","/MainController?"+TOGGLE}) {
            reset("normal","ADMIN");r=request(path,null);s=stats();
            check(14,"GET cannot toggle: "+path,"0".equals(s.get("commands"))&&"0".equals(s.get("writes")),"HTTP="+r.status+"; "+s);
        }
        for(String extra:new String[]{"","&csrf=invalid"}) {
            reset("normal","ADMIN");r=request("/SwitchStageController",TOGGLE+extra);s=stats();
            check(14,"Missing/invalid CSRF cannot toggle ["+extra+"]","0".equals(s.get("commands"))&&"0".equals(s.get("writes")),"HTTP="+r.status+"; "+s);
        }
        reset("normal","anonymous");r=request("/SwitchStageController",TOGGLE);s=stats();
        check(14,"Anonymous direct POST blocked","0".equals(s.get("commands"))&&r.body.contains("name=\"userName\""),"HTTP="+r.status+"; "+s);
        reset("normal","VIEWER");r=request("/SwitchStageController","id=SW2");after=follow(r);s=stats();
        check(14,"Revoked permission blocked","0".equals(s.get("commands"))&&after.body.contains("You do not have permission"),s.toString());
        reset("normal","ADMIN");r=request("/SwitchStageController","id=NO_SUCH_SWITCH");s=stats();
        check(14,"Nonexistent switch blocked","0".equals(s.get("commands"))&&"0".equals(s.get("writes")),s.toString());
        r=request("/SwitchStageController","id=");s=stats();
        check(14,"Missing switch ID cannot mutate",r.status<500&&"0".equals(s.get("commands"))&&"0".equals(s.get("writes")),"HTTP="+r.status+"; "+s);
        reset("double","ADMIN");ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<Response> a=pool.submit(()->request("/MainController",TOGGLE)),b=pool.submit(()->request("/MainController",TOGGLE));
            a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);s=stats();
            check(15,"Simultaneous duplicate submits execute once","1".equals(s.get("commands"))&&"1".equals(s.get("history")),s.toString());
        } finally {pool.shutdownNow();}
        Files.write(evidence.resolve("results.tsv"),log,StandardCharsets.UTF_8);
        System.out.println("AUDIT SUMMARY: "+passed+" passed, "+failed+" failed. Isolated DB / HTTP hardware fixtures; no live database or ESP32 writes.");
    }
    public static void main(String[] args)throws Exception {
        DeviceControlHttpAudit audit=new DeviceControlHttpAudit(args[0],args[1]);audit.run();if(audit.failed>0)System.exit(1);
    }
}
