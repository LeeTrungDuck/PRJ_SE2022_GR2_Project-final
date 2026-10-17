package controller;

import dao.UserDAO;
import dto.ChatMessageDTO;
import dto.UserDTO;
import enums.Role;
import AIChat.ChatException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import AIChat.ChatAssistant;
import utills.URLMap;

/** No live database, provider or hardware. Tests the actual chat controller boundaries. */
public final class ChatControllerTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    @SuppressWarnings("unchecked") private static Map<String,Object> view(ManagementControllerTest.Request r){return(Map<String,Object>)r.attrs.get("chatView");}
    private static final class Assistant extends ChatAssistant {
        boolean ready=true;int calls;ChatException failure;boolean guideFailure;Role role;List<ChatMessageDTO> context;
        Assistant(){super(null,null);}
        @Override public boolean isConfigured(){return ready;}
        @Override public ChatMessageDTO answer(Role role,List<ChatMessageDTO> history,String question)throws ChatException,IOException{
            calls++;this.role=role;context=history;if(failure!=null)throw failure;if(guideFailure)throw new IOException("private guide path");
            return new ChatMessageDTO("assistant","Use the Schedules page.");
        }
    }
    private static String[] send(ManagementControllerTest.Request r,String question){return new String[]{"uiOp","send","csrf",String.valueOf(view(r).get("csrf")),"requestId",String.valueOf(view(r).get("requestId")),"question",question};}
    private static List<ChatMessageDTO> messages(ManagementControllerTest.Request r){return(List<ChatMessageDTO>)r.session.get("CHAT_HISTORY");}
    public static void main(String[] args)throws Exception{
        ManagementControllerTest.FakeUsers users=new ManagementControllerTest.FakeUsers();Assistant assistant=new Assistant();ManagementControllerTest.Request r=new ManagementControllerTest.Request();
        ChatTestController chat=new ChatTestController(){protected UserDAO users(){return users;}protected ChatAssistant assistant(){return assistant;}public void log(String s){}public void log(String s,Throwable e){}};
        chat.doGet(r.request,r.response);check(r.status==401&&!Boolean.TRUE.equals(view(r).get("authenticated"))&&assistant.calls==0,"anonymous chat is protected");
        check("/ChatController".equals(new URLMap().getUrl("CHAT")),"CHAT action maps to the servlet URL");
        for(String id:new String[]{"U1","U2","U3"}){
            r.session.put("LOGIN_USER",users.values.get(id));r.reset("GET");chat.doGet(r.request,r.response);
            check(r.status==200&&Boolean.TRUE.equals(view(r).get("authenticated"))&&view(r).get("csrf")!=null&&view(r).get("requestId")!=null,"active role can open chat: "+id);
        }
        r.session.put("LOGIN_USER",users.values.get("U1"));r.reset("GET");chat.doGet(r.request,r.response);
        String csrf=(String)view(r).get("csrf"),nonce=(String)view(r).get("requestId");
        r.reset("GET","uiOp","send","question","test");chat.doGet(r.request,r.response);check(r.status==405&&assistant.calls==0,"GET cannot send");
        r.reset("GET","uiOp","clear");chat.doGet(r.request,r.response);check(r.status==405,"GET cannot clear");
        r.reset("POST","uiOp","send","csrf","bad","requestId",nonce,"question","Hello");chat.doPost(r.request,r.response);check(r.status==403&&assistant.calls==0,"bad CSRF never calls provider");
        r.reset("POST","uiOp","send","csrf",csrf,"requestId","bad","question","Hello");chat.doPost(r.request,r.response);check(r.status==409&&assistant.calls==0,"invalid request token never calls provider");
        for(String question:new String[]{" ",new String(new char[1001]).replace('\0','a'),"bad\u0000message"}){
            r.reset("GET");chat.doGet(r.request,r.response);String[] form=send(r,question);r.reset("POST",form);chat.doPost(r.request,r.response);
            check(r.status==400&&assistant.calls==0,"invalid question is rejected before provider call");
        }
        assistant.ready=false;r.reset("GET");chat.doGet(r.request,r.response);String[] form=send(r,"A schedule question");r.reset("POST",form);chat.doPost(r.request,r.response);
        check(r.status==503&&assistant.calls==0&&messages(r).isEmpty()&&"A schedule question".equals(view(r).get("question")),"missing configuration is honest and retains the question");
        assistant.ready=true;r.reset("GET");chat.doGet(r.request,r.response);form=send(r,"How do schedules work?");r.reset("POST",form);chat.doPost(r.request,r.response);
        check(r.status==303&&r.location.endsWith("action=CHAT")&&assistant.calls==1&&messages(r).size()==2,"successful message uses PRG and stores one pair");
        check(assistant.role==Role.ADMIN&&assistant.context.isEmpty(),"only authenticated role and bounded conversation reach assistant");
        r.reset("POST",form);chat.doPost(r.request,r.response);check(r.status==303&&assistant.calls==1&&messages(r).size()==2,"duplicate POST opens the existing reply without charging twice");
        r.reset("GET");chat.doGet(r.request,r.response);check(assistant.calls==1&&((List<?>)view(r).get("messages")).size()==2,"GET refresh never repeats generation");
        form=send(r,"Another question");r.reset("POST",form);chat.doPost(r.request,r.response);check(r.status==429&&assistant.calls==1,"rapid sends are limited");
        r.reset("GET");chat.doGet(r.request,r.response);form=send(r,"Busy question");r.session.put("CHAT_BUSY","other");r.reset("POST",form);chat.doPost(r.request,r.response);check(r.status==429&&assistant.calls==1,"in-flight send blocks a parallel form");r.session.remove("CHAT_BUSY");
        r.session.put("CHAT_LAST_SENT",0L);r.reset("GET");chat.doGet(r.request,r.response);form=send(r,"Retry this question");assistant.failure=new ChatException(504,"Provider timeout.");r.reset("POST",form);chat.doPost(r.request,r.response);
        check(r.status==504&&r.session.get("CHAT_BUSY")==null&&messages(r).size()==2&&"Retry this question".equals(view(r).get("question")),"provider failure releases reservation, preserves draft and does not invent a reply");
        assistant.failure=null;r.session.put("CHAT_LAST_SENT",0L);assistant.guideFailure=true;r.reset("GET");chat.doGet(r.request,r.response);form=send(r,"Missing guide");r.reset("POST",form);chat.doPost(r.request,r.response);
        check(r.status==503&&!r.attrs.toString().contains("private guide path")&&r.session.get("CHAT_BUSY")==null,"guide failure is safe and releases reservation");assistant.guideFailure=false;
        for(int i=0;i<7;i++){
            r.session.put("CHAT_LAST_SENT",0L);r.session.put("CHAT_COUNT",0);r.reset("GET");chat.doGet(r.request,r.response);form=send(r,"Question "+i);r.reset("POST",form);chat.doPost(r.request,r.response);check(r.status==303,"history-cap setup sends successfully");
        }
        check(messages(r).size()==12,"session history retains at most six message pairs");
        r.session.put("CHAT_LAST_SENT",0L);r.session.put("CHAT_WINDOW",System.currentTimeMillis());r.session.put("CHAT_COUNT",10);int before=assistant.calls;r.reset("GET");chat.doGet(r.request,r.response);form=send(r,"Over quota");r.reset("POST",form);chat.doPost(r.request,r.response);
        check(r.status==429&&assistant.calls==before,"minute quota blocks outbound calls");
        r.reset("GET");chat.doGet(r.request,r.response);csrf=(String)view(r).get("csrf");nonce=(String)view(r).get("requestId");r.reset("POST","uiOp","clear","csrf",csrf,"requestId",nonce);chat.doPost(r.request,r.response);
        check(r.status==303&&messages(r).isEmpty()&&Integer.valueOf(10).equals(r.session.get("CHAT_COUNT")),"clear is POST, empties history and cannot bypass quota");
        r.session.put("CHAT_HISTORY",new ArrayList<ChatMessageDTO>(){{add(new ChatMessageDTO("user","private old chat"));}});
        r.session.put("LOGIN_USER",users.values.get("U2"));r.reset("GET");chat.doGet(r.request,r.response);
        check(messages(r).isEmpty()&&!csrf.equals(view(r).get("csrf")),"switching accounts resets history and CSRF");
        messages(r).add(new ChatMessageDTO("user","old role context"));users.values.get("U2").setRole(Role.OPERATOR);r.reset("GET");chat.doGet(r.request,r.response);
        check(messages(r).isEmpty()&&"U2:OPERATOR".equals(r.session.get("CHAT_OWNER")),"fresh database role resets earlier context");
        users.values.get("U2").setActive(false);r.reset("GET");chat.doGet(r.request,r.response);check(r.status==401&&r.session.isEmpty(),"inactive account session is invalidated");
        r.session.put("LOGIN_USER",users.values.get("U1"));users.readFailure=new SQLException("private SQL details");r.reset("GET");chat.doGet(r.request,r.response);
        check(r.status==503&&!r.attrs.toString().contains("private SQL details"),"database failure is caught and rendered safely");users.readFailure=null;
        LoginController login=new LoginController(){protected UserDAO users(){return users;}public void log(String s,Throwable e){}};
        r.session.put("CHAT_OWNER","old");r.session.put("CHAT_HISTORY",new ArrayList<>());r.session.put("CHAT_CSRF","old-token");r.session.put("CHAT_LAST_REQUEST","old-request");r.session.put("CHAT_FEEDBACK",new java.util.LinkedHashMap<>());r.session.put("CHAT_DATA_SCOPE","old-scope");r.reset("POST","userName","U1","passWord","oldPassword1!");login.doPost(r.request,r.response);
        check(r.status==302&&!r.session.containsKey("CHAT_OWNER")&&!r.session.containsKey("CHAT_HISTORY")&&!r.session.containsKey("CHAT_CSRF")&&!r.session.containsKey("CHAT_LAST_REQUEST")&&!r.session.containsKey("CHAT_FEEDBACK")&&!r.session.containsKey("CHAT_DATA_SCOPE"),"new login clears conversation, replay and database scope state before reuse");
        users.values.get("U2").setActive(true);r.session.put("LOGIN_USER",users.values.get("U1"));
        ChatTestController raced=new ChatTestController(){
            protected UserDAO users(){return new UserDAO((java.sql.Connection)null){public UserDTO findById(String id){UserDTO found=ManagementControllerTest.FakeUsers.copy(users.values.get(id));r.session.put("LOGIN_USER",users.values.get("U2"));return found;}public void close(){}};}
            protected ChatAssistant assistant(){return assistant;}public void log(String s,Throwable e){}
        };
        r.reset("GET");raced.doGet(r.request,r.response);
        check(r.status==401&&"U2".equals(((UserDTO)r.session.get("LOGIN_USER")).getUserId())&&!Boolean.TRUE.equals(view(r).get("authenticated")),"a slow identity lookup cannot overwrite a newer login");
        r.session.put("LOGIN_USER",users.values.get("U1"));
        ChatTestController switchedDuringReply=new ChatTestController(){
            protected UserDAO users(){return users;}
            protected ChatAssistant assistant(){return new ChatAssistant(null,null){public boolean isConfigured(){r.session.put("LOGIN_USER",users.values.get("U2"));return true;}};}
            public void log(String s,Throwable e){}
        };
        r.reset("GET");switchedDuringReply.doGet(r.request,r.response);
        check(r.status==401&&!Boolean.TRUE.equals(view(r).get("authenticated"))&&"U2".equals(((UserDTO)r.session.get("LOGIN_USER")).getUserId()),"late render never restores the old user's context");
        System.out.println("PASS: "+checks+" chat controller/auth/session/input checks. No live provider or database calls.");
    }
}
