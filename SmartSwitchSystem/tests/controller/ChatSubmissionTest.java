package controller;

import dao.UserDAO;
import dto.ChatMessageDTO;
import enums.Role;
import AIChat.ChatException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import AIChat.ChatAssistant;

/** Reproduces a second navigation replacing an in-flight iframe submission. */
public final class ChatSubmissionTest {
    private static int checks;
    private static void check(boolean condition,String label) { if(!condition)throw new AssertionError(label);checks++; }
    @SuppressWarnings("unchecked") private static Map<String,Object> view(ManagementControllerTest.Request r) { return (Map<String,Object>)r.attrs.get("chatView"); }
    private static String[] form(ManagementControllerTest.Request r,String question) { return new String[]{"uiOp","send","csrf",String.valueOf(view(r).get("csrf")),"requestId",String.valueOf(view(r).get("requestId")),"question",question}; }
    private static ChatTestController controller(ManagementControllerTest.FakeUsers users,ChatAssistant helper) {
        return new ChatTestController(){protected UserDAO users(){return users;}protected ChatAssistant assistant(){return helper;}public void log(String s){}public void log(String s,Throwable e){}};
    }
    private static HttpServletRequest shared(ManagementControllerTest.Request r,ManagementControllerTest.Request owner) {
        return new HttpServletRequestWrapper(r.request){public HttpSession getSession(boolean create){return owner.httpSession;}public HttpSession getSession(){return owner.httpSession;}};
    }
    public static void main(String[] args)throws Exception {
        ManagementControllerTest.FakeUsers users=new ManagementControllerTest.FakeUsers();
        ManagementControllerTest.Request first=new ManagementControllerTest.Request(),other=new ManagementControllerTest.Request();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        ChatAssistant slow=new ChatAssistant(null,null){
            public boolean isConfigured(){return true;}
            public ChatMessageDTO answer(Role role,List<ChatMessageDTO> history,String question)throws ChatException,IOException {
                calls.incrementAndGet();entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("fixture timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                return new ChatMessageDTO("assistant","One reply.");
            }
        };
        ChatTestController chat=controller(users,slow);first.session.put("LOGIN_USER",users.values.get("U1"));
        chat.doGet(first.request,first.response);String[] original=form(first,"How do schedules work?");
        first.reset("POST",original);Throwable[] threadFailure={null};
        Thread sending=new Thread(()->{try{chat.doPost(first.request,first.response);}catch(Throwable e){threadFailure[0]=e;}});
        sending.start();
        try {
            check(entered.await(3,TimeUnit.SECONDS),"original request reached the provider");
            other.reset("POST",original);chat.doPost(shared(other,first),other.response);
            check(other.status==303&&calls.get()==1,"in-flight duplicate redirects to the conversation without an expired-form error or second generation");
            other.reset("GET");chat.doGet(shared(other,first),other.response);
            check(other.status==202&&Boolean.TRUE.equals(view(other).get("busy")),"GET exposes the pending reply so the iframe can refresh without JavaScript");
        } finally { release.countDown();sending.join(5000); }
        check(!sending.isAlive()&&threadFailure[0]==null&&first.status==303,"original generation finishes normally");
        other.reset("GET");chat.doGet(shared(other,first),other.response);
        check(other.status==200&&!Boolean.TRUE.equals(view(other).get("busy"))&&((List<?>)view(other).get("messages")).size()==2,"refreshed conversation contains one question and one reply");
        other.reset("POST",original);chat.doPost(shared(other,first),other.response);
        check(other.status==303&&calls.get()==1,"completed duplicate also opens the existing result without calling the provider");
        other.reset("POST","uiOp","send","csrf","wrong","requestId",original[5],"question","Hello");chat.doPost(shared(other,first),other.response);
        check(other.status==403&&calls.get()==1,"replay recovery does not bypass CSRF");
        other.reset("GET");chat.doGet(shared(other,first),other.response);String csrf=String.valueOf(view(other).get("csrf"));
        other.reset("POST","uiOp","send","csrf",csrf,"requestId","invented","question","Keep this draft");chat.doPost(shared(other,first),other.response);
        check(other.status==409&&calls.get()==1&&"Keep this draft".equals(view(other).get("question")),"unknown tokens stay blocked and the bounded draft is retained");

        ManagementControllerTest.Request failed=new ManagementControllerTest.Request();AtomicInteger failedCalls=new AtomicInteger();
        ChatAssistant unavailable=new ChatAssistant(null,null){public boolean isConfigured(){return true;}public ChatMessageDTO answer(Role role,List<ChatMessageDTO> history,String question)throws ChatException{failedCalls.incrementAndGet();throw new ChatException(429,"Provider quota reached.");}};
        ChatTestController broken=controller(users,unavailable);failed.session.put("LOGIN_USER",users.values.get("U1"));broken.doGet(failed.request,failed.response);String[] errorForm=form(failed,"Retry my question");
        failed.reset("POST",errorForm);broken.doPost(failed.request,failed.response);
        check(failed.status==429&&failed.session.get("CHAT_BUSY")==null,"provider failure ends the pending state");
        failed.reset("POST",errorForm);broken.doPost(failed.request,failed.response);
        check(failed.status==303&&failedCalls.get()==1,"duplicate of a failed generation does not replace the real error or call Gemini again");
        failed.reset("GET");broken.doGet(failed.request,failed.response);
        check(failed.status==429&&"Provider quota reached.".equals(view(failed).get("error"))&&"Retry my question".equals(view(failed).get("question")),"pending-view recovery preserves the original provider error and draft");
        failed.reset("GET");broken.doGet(failed.request,failed.response);
        check(failed.status==200&&view(failed).get("error")==null&&failedCalls.get()==1,"subsequent GET does not repeat generation or the one-time notice");
        failed.session.put("CHAT_FEEDBACK",new java.util.LinkedHashMap<String,Object>());failed.session.put("LOGIN_USER",users.values.get("U2"));failed.reset("GET");broken.doGet(failed.request,failed.response);
        check(!failed.session.containsKey("CHAT_LAST_REQUEST")&&!failed.session.containsKey("CHAT_FEEDBACK"),"account changes clear replay and feedback state");
        System.out.println("PASS: "+checks+" chat submission/recovery checks. No real provider or database calls.");
    }
}
