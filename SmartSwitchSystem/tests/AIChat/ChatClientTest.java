package AIChat;

import com.sun.net.httpserver.HttpServer;
import dto.ChatMessageDTO;
import dto.ChatSourceDTO;
import enums.Role;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import javax.servlet.ServletContext;

/** Real localhost HTTP tests for the JSON/protocol boundary, plus role-filtered guide retrieval. */
public final class ChatClientTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private interface Call{void run()throws Exception;}
    private static void failure(Call call,int status,String label)throws Exception{
        try{call.run();throw new AssertionError(label);}catch(ChatException expected){check(expected.getStatus()==status&&!expected.getMessage().contains("fixture-secret"),label);}
    }
    public static void main(String[] args)throws Exception{
        ChatJson json=new ChatJson();String special="Xin chào\n\"quoted\" \\ \t <script>";
        check(special.equals(json.parse(json.quote(special))),"Unicode, quotes, slashes and control escapes round-trip");
        check(((Map<?,?>)json.parse("{\"nested\":[true,false,null,-1.25e2]}")).containsKey("nested"),"JSON objects, arrays and scalar types parse");
        for(String invalid:new String[]{"{}trailing","{\"a\":}","[1,]","\"bad\nstring\"","{\"a\":01}","\"\\uXXYY\""}){
            try{json.parse(invalid);throw new AssertionError("Invalid JSON accepted");}catch(IllegalArgumentException expected){checks++;}
        }
        int[] status={200},calls={0};String[] response={"{\"choices\":[{\"message\":{\"content\":"+json.quote(special)+"}}]}"},body={null},authorization={null};boolean[] delay={false};
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.ExecutorService workers=Executors.newCachedThreadPool();server.setExecutor(workers);
        server.createContext("/v1/chat/completions",exchange->{
            calls[0]++;authorization[0]=exchange.getRequestHeaders().getFirst("Authorization");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=exchange.getRequestBody()){byte[] b=new byte[2048];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}body[0]=new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            if(delay[0])try{Thread.sleep(400);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] out=response[0].getBytes(StandardCharsets.UTF_8);
            try{exchange.sendResponseHeaders(status[0],out.length);exchange.getResponseBody().write(out);}catch(java.io.IOException ignored){}finally{exchange.close();}
        });server.start();
        try{
            String base="http://127.0.0.1:"+server.getAddress().getPort()+"/v1";
            ChatClient client=new ChatClient(base,"fixture-secret","fixture-model","max_completion_tokens",2000);
            check(client.isConfigured(),"loopback fixture is allowed");
            check(special.equals(client.complete("system guide",Collections.emptyList(),special)),"provider content is decoded as text");
            Map<?,?> request=(Map<?,?>)json.parse(body[0]);
            check("fixture-model".equals(request.get("model"))&&Boolean.FALSE.equals(request.get("stream"))&&request.containsKey("max_completion_tokens"),"request follows the configured non-streaming protocol");
            check("Bearer fixture-secret".equals(authorization[0])&&!body[0].contains("fixture-secret"),"key is only in authorization header");
            List<?> messages=(List<?>)request.get("messages");check(messages.size()==2&&special.equals(((Map<?,?>)messages.get(1)).get("content")),"user text cannot break request JSON");
            response[0]="{\"choices\":[{\"message\":{\"content\":\"fixture-secret\"}}]}";
            check("[redacted]".equals(client.complete("guide",Collections.emptyList(),"hello")),"credential echoes are redacted");
            for(int value:new int[]{401,403,429,503,302}){status[0]=value;failure(()->client.complete("guide",Collections.emptyList(),"question"),value==401||value==403?503:value==429?429:502,"safe provider status "+value);}
            status[0]=200;
            for(String invalid:new String[]{"not JSON","{\"choices\":[]}","{\"choices\":[{\"message\":{\"content\":null}}]}","{\"choices\":[{\"message\":{\"content\":\"\"}}]}"}){
                response[0]=invalid;failure(()->client.complete("guide",Collections.emptyList(),"question"),502,"malformed provider reply is rejected");
            }
            response[0]=new String(new char[131073]).replace('\0','x');failure(()->client.complete("guide",Collections.emptyList(),"question"),502,"oversized provider response is bounded");
            delay[0]=true;response[0]="{}";failure(()->new ChatClient(base,"fixture-secret","fixture-model","max_tokens",100).complete("guide",Collections.emptyList(),"question"),504,"provider timeout is bounded");delay[0]=false;
            for(ChatClient invalid:new ChatClient[]{new ChatClient(base,"","model","max_tokens",100),new ChatClient("http://example.invalid/v1","key","model","max_tokens",100),new ChatClient("https://example.invalid/v1?key=bad","key","model","max_tokens",100),new ChatClient(base,"key\r\nHeader: bad","model","max_tokens",100),new ChatClient(base,"key","model","invented",100)}){
                check(!invalid.isConfigured(),"invalid/missing server configuration is not treated as ready");
            }
            failure(()->new ChatClient("http://127.0.0.1:1/v1","fixture-secret","model","max_tokens",100).complete("guide",Collections.emptyList(),"question"),502,"connection failure stays safe");
        }finally{server.stop(0);workers.shutdownNow();}

        ServletContext context=(ServletContext)java.lang.reflect.Proxy.newProxyInstance(ServletContext.class.getClassLoader(),new Class<?>[]{ServletContext.class},(o,m,a)->{
            if("getResourceAsStream".equals(m.getName()))return Files.newInputStream(Paths.get("web"+(String)a[0]));return null;
        });
        ChatKnowledgeBase knowledge=new ChatKnowledgeBase(context);
        List<ChatKnowledgeBase.Excerpt> admin=knowledge.retrieve("User Management add user role account",Role.ADMIN);
        check(admin.stream().anyMatch(item->"USER_MANAGER".equals(item.source().getAction())),"Admin can retrieve the User Management guide");
        List<ChatKnowledgeBase.Excerpt> viewer=knowledge.retrieve("User Management add user role account",Role.VIEWER);
        check(viewer.stream().noneMatch(item->"USER_MANAGER".equals(item.source().getAction())||"PERMISSION_MANAGER".equals(item.source().getAction())),"Viewer cannot retrieve Admin/Operator-only guide excerpts");
        check(knowledge.retrieve("tạo lịch hẹn giờ",Role.VIEWER).stream().anyMatch(item->"SCHEDULE_MANAGER".equals(item.source().getAction())),"Vietnamese keywords match bundled schedule guides");
        List<ChatKnowledgeBase.Excerpt> scheduleHelp=knowledge.retrieve("How do I create a schedule?",Role.ADMIN);
        check(scheduleHelp.size()==1&&"SCHEDULE_MANAGER".equals(scheduleHelp.get(0).source().getAction()),"generic shared words do not add unrelated guide links");
        check(knowledge.retrieve("quantum zebras astrophysics",Role.ADMIN).isEmpty(),"unrelated questions do not invent a retrieved source");
        String[] prompt={null};int[] historySize={0};
        ChatClient capture=new ChatClient("","","","max_tokens",100){public boolean isConfigured(){return true;}public String complete(String instructions,List<ChatMessageDTO> h,String q){prompt[0]=instructions;historySize[0]=h.size();return"Use Account Settings.";}};
        List<ChatMessageDTO> history=new ArrayList<>();for(int i=0;i<12;i++)history.add(new ChatMessageDTO(i%2==0?"user":"assistant","Message "+i));
        ChatMessageDTO reply=new ChatAssistant(capture,knowledge).answer(Role.VIEWER,history,"change my password");
        check(historySize[0]==6&&prompt[0].contains("VIEWER")&&prompt[0].contains("GUIDE EXCERPTS")&&!reply.getSources().isEmpty(),"generation receives bounded history and role-filtered retrieval context");
        check(prompt[0].contains("cannot execute commands")&&!prompt[0].contains("fixture-secret"),"assistant has no tools or credentials in generation context");
        System.out.println("PASS: "+checks+" chat HTTP/JSON/RAG checks. Only a localhost provider was contacted.");
    }
}
