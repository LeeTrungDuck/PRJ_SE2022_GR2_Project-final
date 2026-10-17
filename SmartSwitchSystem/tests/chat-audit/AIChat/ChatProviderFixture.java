package AIChat;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

/** Local-only provider. It never reads or uses real environment credentials. */
@WebServlet(urlPatterns="/__chatfixture",loadOnStartup=2)
public class ChatProviderFixture extends HttpServlet {
    private HttpServer server;private ExecutorService workers;private volatile String mode="ready",prompt="";private final AtomicInteger calls=new AtomicInteger();
    private final ChatJson json=new ChatJson();
    @Override public void init()throws javax.servlet.ServletException{
        try{
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);workers=Executors.newCachedThreadPool();server.setExecutor(workers);
            server.createContext("/v1/chat/completions",x->{
                calls.incrementAndGet();ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=x.getRequestBody()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)b.write(buffer,0,n);}
                Map<?,?> request=(Map<?,?>)json.parse(new String(b.toByteArray(),StandardCharsets.UTF_8));
                prompt=String.valueOf(((Map<?,?>)((List<?>)request.get("messages")).get(0)).get("content"));
                if("slow".equals(mode))try{Thread.sleep(1800);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                String answer="html".equals(mode)?"<script>alert('fixture')</script> & <img src=x>":"Open Schedules, choose your switch and a daily run time, then select Add Schedule.\nYou can update Enabled and Save to pause or resume a schedule.";
                int code="busy".equals(mode)?429:200;
                String response="invalid".equals(mode)?"not-json":"{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":"+json.quote(answer)+"}}]}";
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);try{x.sendResponseHeaders(code,bytes.length);x.getResponseBody().write(bytes);}finally{x.close();}
            });server.start();configure("ready");
        }catch(IOException e){throw new javax.servlet.ServletException(e);}
    }
    private void configure(String value){
        mode=value;System.setProperty("smartSwitch.chat.baseUrl","http://127.0.0.1:"+server.getAddress().getPort()+"/v1");
        System.setProperty("smartSwitch.chat.apiKey","chat-fixture-secret-not-real");
        System.setProperty("smartSwitch.chat.model","missing".equals(value)?"disabled\nmodel":"fixture-model");
        System.setProperty("smartSwitch.chat.tokenField","max_completion_tokens");
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse res)throws IOException{
        if(req.getParameter("mode")!=null)configure(req.getParameter("mode"));
        res.setContentType("text/plain;charset=UTF-8");
        boolean privateData=prompt.contains("@example.invalid")||prompt.contains("090000000")||prompt.contains("192.0.2.")||prompt.contains("http://127.0.0.1")||prompt.contains("Original Admin")||prompt.contains("\"userId\"")||prompt.contains("\"hostName\"");
        res.getWriter().print("mode="+mode+";calls="+calls+";adminGuide="+prompt.contains("Only Admin can use User Management.")+";viewer="+prompt.contains("user's role is VIEWER")+";database="+prompt.contains("APPROVED DATABASE SNAPSHOT")+";grantedSwitch="+prompt.contains("\"switchId\":\"SW1\"")+";otherSwitch="+prompt.contains("\"switchId\":\"SW3\"")+";privateData="+privateData);
    }
    @Override public void destroy(){if(server!=null)server.stop(0);if(workers!=null)workers.shutdownNow();}
}
