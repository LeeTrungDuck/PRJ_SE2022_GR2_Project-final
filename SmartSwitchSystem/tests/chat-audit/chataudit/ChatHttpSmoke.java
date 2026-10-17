package chataudit;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class ChatHttpSmoke {
    private final String base;private final Path evidence;private String cookie="";private int checks;
    private static final class Response{int status;String body,location;Map<String,List<String>> headers;}
    private ChatHttpSmoke(String base,String dir)throws IOException{this.base=base;evidence=Paths.get(dir);Files.createDirectories(evidence);}
    private Response request(String path,String post)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(3000);c.setReadTimeout(20000);if(!cookie.isEmpty())c.setRequestProperty("Cookie",cookie);
        if(post!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/x-www-form-urlencoded");try(OutputStream o=c.getOutputStream()){o.write(post.getBytes(StandardCharsets.UTF_8));}}
        Response r=new Response();r.status=c.getResponseCode();r.location=c.getHeaderField("Location");r.headers=c.getHeaderFields();String set=c.getHeaderField("Set-Cookie");if(set!=null)cookie=set.split(";",2)[0];
        InputStream in=r.status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(in!=null)try(InputStream s=in){byte[] b=new byte[4096];int n;while((n=s.read(b))!=-1)bytes.write(b,0,n);}r.body=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();return r;
    }
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    private void save(String name,Response r)throws IOException{Files.write(evidence.resolve(name+".html"),r.body.getBytes(StandardCharsets.UTF_8));}
    private String field(Response r,String name){Matcher m=Pattern.compile("name=\""+name+"\"\\s+value=\"([^\"]*)\"").matcher(r.body);if(!m.find())throw new AssertionError("Missing form field "+name+", HTTP "+r.status);return m.group(1);}
    private String form(Response r,String op,String question)throws Exception{return"action=CHAT&uiOp="+op+"&csrf="+field(r,"csrf")+"&requestId="+field(r,"requestId")+"&question="+URLEncoder.encode(question,"UTF-8");}
    private int calls()throws Exception{Matcher m=Pattern.compile("calls=(\\d+)").matcher(request("/__chatfixture",null).body);m.find();return Integer.parseInt(m.group(1));}
    private void reset(String actor,String mode)throws Exception{request("/__admin?op=reset&actor="+actor,null);request("/__chatfixture?mode="+mode,null);}
    private Response chat()throws Exception{return request("/MainController?action=CHAT",null);}
    private void run()throws Exception{
        Response r=chat();check(r.status==401&&r.body.contains("Go to sign in"),"anonymous route renders a protected sign-in state");
        reset("U1","ready");r=request("/MainController?action=DEVICE_CONTROL",null);save("dashboard",r);check(r.status==200&&r.body.contains("class=\"chat-widget\"")&&r.body.contains("title=\"SmartSwitch AI assistant conversation\""),"shared launcher and lazy iframe are in the real dashboard");
        for(String action:new String[]{"USER_MANAGER","PERMISSION_MANAGER","ACCOUNT_MANAGER","DEVICE_MANAGER","SCHEDULE_MANAGER","CONTROL_HISTORY"}){r=request("/MainController?action="+action,null);check(r.status==200&&r.body.contains("class=\"chat-widget\""),"launcher on "+action);}
        r=chat();save("welcome",r);check(r.status==200&&r.body.contains("How can I help?")&&r.body.contains("name=\"requestId\""),"chat welcome and native forms render");
        check(request("/WEB-INF/chat/admin.md",null).status==404,"bundled role guides are not publicly downloadable");
        check(!r.body.contains("<script")&&!r.body.contains("chat-fixture-secret-not-real"),"chat has no scripts or provider credentials in HTML");
        String post=form(r,"send","How do I create a schedule?");int before=calls();Response sent=request("/MainController",post);check(sent.status==303&&sent.location.endsWith("action=CHAT")&&calls()==before+1,"form sends to provider once and redirects");
        r=chat();save("conversation",r);check(r.body.contains("daily run time")&&r.body.contains("Related pages")&&r.body.contains("SCHEDULE_MANAGER"),"AI response and retrieved page references render");
        check(request("/MainController",post).status==303&&calls()==before+1,"duplicate form opens the existing result without repeating provider request");
        check(request("/ChatController?uiOp=send&question=test",null).status==405,"GET cannot send");
        check(request("/ChatController","uiOp=send&csrf=wrong&requestId=x&question=test").status==403,"invalid CSRF is blocked on direct URL");
        r=chat();Response cleared=request("/MainController",form(r,"clear",""));check(cleared.status==303&&chat().body.contains("How can I help?"),"New chat clears only the conversation");
        reset("U2","ready");r=chat();sent=request("/MainController",form(r,"send","User Management add user role account"));check(sent.status==303,"Viewer can ask a question");
        String state=request("/__chatfixture",null).body;check(state.contains("viewer=true")&&state.contains("adminGuide=false"),"provider receives role-filtered context, not Admin-only guides");
        reset("U2","ready");r=chat();sent=request("/MainController",form(r,"send","List my switch status"));state=request("/__chatfixture",null).body;
        check(sent.status==303&&state.contains("database=true")&&state.contains("grantedSwitch=true")&&state.contains("otherSwitch=false")&&state.contains("privateData=false"),"Viewer generation receives only permitted operational database projections");
        request("/__admin?op=revokeChat",null);r=chat();
        check(r.status==200&&r.body.contains("How can I help?")&&!r.body.contains("daily run time"),"permission revocation clears old database-derived conversation in the real JSP");
        sent=request("/MainController",form(r,"send","List my switch status"));state=request("/__chatfixture",null).body;
        check(sent.status==303&&state.contains("grantedSwitch=false")&&state.contains("otherSwitch=false")&&state.contains("privateData=false"),"revoked Viewer switch never re-enters a later provider request");
        reset("U1","ready");r=chat();sent=request("/MainController",form(r,"send","List all switch status"));state=request("/__chatfixture",null).body;
        check(sent.status==303&&state.contains("grantedSwitch=true")&&state.contains("otherSwitch=true")&&state.contains("privateData=false"),"Admin receives all active operational records without account/network information");
        reset("U2","ready");r=chat();before=calls();sent=request("/MainController",form(r,"send","vậy tôi có thể điều khiển SW nào ?")+"&userId=U1&role=ADMIN");r=chat();save("my-controllable-switches",r);
        check(sent.status==303&&calls()==before&&r.body.contains("SW1")&&r.body.contains("GPIO 2")&&r.body.contains("DEVICE_CONTROL")&&!r.body.contains("SW3"),"Vietnamese SW question returns the Viewer's actual control grants without trusting submitted identity or generated facts");
        request("/__admin?op=revokeChat",null);r=chat();check(r.body.contains("How can I help?")&&!r.body.contains("GPIO 2"),"permission revocation clears a previously listed control grant");
        sent=request("/MainController",form(r,"send","vậy tôi có thể điều khiển SW nào ?"));r=chat();
        check(sent.status==303&&calls()==before&&r.body.contains("chưa được cấp quyền")&&!r.body.contains("SW1"),"revoked Viewer grant receives an honest empty control list in the real JSP");
        reset("U1","ready");r=chat();before=calls();sent=request("/MainController",form(r,"send","Which switches can I control?"));r=chat();
        check(sent.status==303&&calls()==before&&r.body.contains("SW1")&&!r.body.contains("SW3"),"Admin control list requires a personal permission instead of using all visible switches");
        reset("U3","ready");r=chat();before=calls();sent=request("/MainController",form(r,"send","Which switches can I control?"));r=chat();
        check(sent.status==303&&calls()==before&&r.body.contains("no active switch-control permissions"),"Operator without a personal grant gets an exact empty control list");
        reset("U1","missing");r=chat();save("missing-configuration",r);before=calls();sent=request("/MainController",form(r,"send","Hello"));check(sent.status==503&&sent.body.contains("not configured yet")&&calls()==before,"missing configuration shows honest state without provider request");
        reset("U1","busy");r=chat();sent=request("/MainController",form(r,"send","Schedule help"));save("provider-error",sent);check(sent.status==429&&sent.body.contains("AI service is busy")&&!sent.body.contains("chat-fixture-secret"),"provider error is safe and visible");
        reset("U1","html");r=chat();sent=request("/MainController",form(r,"send","<script>alert('user')</script>"));r=chat();save("escaped-content",r);
        check(sent.status==303&&!r.body.contains("<script>")&&!r.body.contains("<img src=x>")&&r.body.contains("&lt;script&gt;"),"both provider and user HTML are escaped");
        reset("U1","slow");Response first=chat(),second=chat();final String one=form(first,"send","Schedules first"),two=form(second,"send","Schedules second");before=calls();
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try{Future<Response>a=pool.submit(()->{start.await();return request("/MainController",one);});Future<Response>b=pool.submit(()->{start.await();return request("/MainController",two);});start.countDown();int sa=a.get(20,TimeUnit.SECONDS).status,sb=b.get(20,TimeUnit.SECONDS).status;
            check((sa==303&&sb==429||sa==429&&sb==303)&&calls()==before+1,"parallel forms create one outbound generation");
        }finally{pool.shutdownNow();}
        reset("U1","slow");r=chat();final String duplicate=form(r,"send","Schedules duplicate");before=calls();
        ExecutorService replayPool=Executors.newFixedThreadPool(2);
        try {
            Future<Response> original=replayPool.submit(()->request("/MainController",duplicate));
            for(int i=0;i<30&&calls()==before;i++)Thread.sleep(30);
            Response replay=request("/MainController",duplicate);
            check(replay.status==303,"in-flight duplicate redirects instead of showing an expired-form warning");
            Response pending=chat();save("pending-reply",pending);
            check(pending.status==202&&pending.body.contains("http-equiv=\"refresh\"")&&pending.body.contains("Preparing your reply")&&pending.body.contains("title=\"Send question\" disabled"),"pending iframe refreshes with native HTML and disables Send");
            check(original.get(20,TimeUnit.SECONDS).status==303&&calls()==before+1,"original and duplicate create exactly one provider call");
            Response done=chat();save("recovered-reply",done);
            check(done.status==200&&done.body.contains("daily run time")&&!done.body.contains("http-equiv=\"refresh\""),"pending conversation finishes with a reply and stops refreshing");
        } finally { replayPool.shutdownNow(); }
        reset("U1","slow");r=chat();String abandoned=form(r,"send","Schedules after navigation replacement");before=calls();
        URL endpoint=new URL(base+"/MainController");byte[] abandonedBody=abandoned.getBytes(StandardCharsets.UTF_8);
        try(Socket browserNavigation=new Socket(endpoint.getHost(),endpoint.getPort())) {
            OutputStream wire=browserNavigation.getOutputStream();
            String headers="POST "+endpoint.getPath()+" HTTP/1.1\r\nHost: "+endpoint.getHost()+"\r\nCookie: "+cookie+"\r\nContent-Type: application/x-www-form-urlencoded\r\nContent-Length: "+abandonedBody.length+"\r\nConnection: close\r\n\r\n";
            wire.write(headers.getBytes(StandardCharsets.US_ASCII));wire.write(abandonedBody);wire.flush();
            for(int i=0;i<30&&calls()==before;i++)Thread.sleep(30);
            check(calls()==before+1,"abandoned iframe navigation has started one generation");
        }
        check(request("/MainController",abandoned).status==303,"replacement submit recovers a disconnected original navigation");
        for(int i=0;i<35;i++){r=chat();if(r.status!=202)break;Thread.sleep(100);}
        check(r.status==200&&r.body.contains("daily run time")&&calls()==before+1,"reply survives the original connection closing and appears without duplicate generation");
        reset("U1","ready");r=request("/web/Chat.jsp",null);check(r.status==302&&r.location.contains("action=CHAT"),"direct JSP routes through the controller");
        request("/LogOutController",null);r=chat();check(r.status==401&&!r.body.contains("daily run time"),"logout removes prior conversation");
        System.out.println("PASS: "+checks+" chat HTTP/JSP integration checks. Local provider and isolated JDBC only.");
    }
    public static void main(String[] args)throws Exception{new ChatHttpSmoke(args[0],args[1]).run();}
}
