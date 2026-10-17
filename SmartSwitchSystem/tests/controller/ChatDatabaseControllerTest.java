package controller;

import dao.UserDAO;
import dto.ChatMessageDTO;
import enums.Role;
import AIChat.ChatException;
import java.sql.SQLException;
import java.util.*;
import AIChat.ChatAssistant;

/** Verifies that session history cannot retain data after operational access changes. */
public final class ChatDatabaseControllerTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    @SuppressWarnings("unchecked") private static Map<String,Object> view(ManagementControllerTest.Request r){return(Map<String,Object>)r.attrs.get("chatView");}
    private static String[] form(ManagementControllerTest.Request r){return new String[]{"uiOp","send","csrf",String.valueOf(view(r).get("csrf")),"requestId",String.valueOf(view(r).get("requestId")),"question","Switch status","userId","U1","role","ADMIN"};}
    private static final class Helper extends ChatAssistant {
        String visibility="permitted-SW1",actor;int calls,history;boolean failScope,failRead,revokeDuringReply,demoteDuringReply;
        ManagementControllerTest.FakeUsers users;
        Helper(){super(null,null);}
        public boolean isConfigured(){return true;}
        public String scope(String id,Role role)throws SQLException{if(failScope)throw new SQLException("private SQL context");return visibility;}
        public ChatMessageDTO answer(String id,Role role,List<ChatMessageDTO> h,String q,String scope)throws SQLException,ChatException{
            if(failRead)throw new SQLException("private SQL context");calls++;actor=id;history=h.size();
            if(revokeDuringReply)visibility="no-switches";
            if(demoteDuringReply)users.values.get(id).setRole(Role.OPERATOR);
            return new ChatMessageDTO("assistant","Saved SW1 state is OFF.");
        }
    }
    public static void main(String[] args)throws Exception {
        ManagementControllerTest.FakeUsers users=new ManagementControllerTest.FakeUsers();Helper helper=new Helper();helper.users=users;
        ChatTestController chat=new ChatTestController(){protected UserDAO users(){return users;}protected ChatAssistant assistant(){return helper;}public void log(String s,Throwable e){}};
        ManagementControllerTest.Request r=new ManagementControllerTest.Request();r.session.put("LOGIN_USER",users.values.get("U2"));
        chat.doGet(r.request,r.response);String[] original=form(r);r.reset("POST",original);chat.doPost(r.request,r.response);
        check(r.status==303&&helper.calls==1&&"U2".equals(helper.actor),"form userId/role cannot choose another account's database scope");
        helper.visibility="no-switches";r.reset("GET");chat.doGet(r.request,r.response);
        check(r.status==200&&((List<?>)view(r).get("messages")).isEmpty()&&!r.session.containsKey("CHAT_LAST_REQUEST"),"permission changes clear old database replies and replay state before rendering");
        r.reset("POST",original);chat.doPost(r.request,r.response);
        check(r.status==403&&helper.calls==1,"a form from the old permission scope cannot send old conversation context");
        r.reset("GET");chat.doGet(r.request,r.response);String[] next=form(r);r.reset("POST",next);chat.doPost(r.request,r.response);
        check(r.status==303&&helper.calls==2&&helper.history==0,"next accepted generation receives no revoked historical facts");
        helper.visibility="permitted-again";r.reset("GET");chat.doGet(r.request,r.response);next=form(r);helper.revokeDuringReply=true;r.reset("POST",next);chat.doPost(r.request,r.response);
        check(r.status==401&&!Boolean.TRUE.equals(view(r).get("authenticated"))&&((List<?>)r.session.get("CHAT_HISTORY")).isEmpty(),"access revoked during generation prevents the stale reply from being stored or displayed");
        helper.revokeDuringReply=false;r.reset("GET");chat.doGet(r.request,r.response);
        check(((List<?>)view(r).get("messages")).isEmpty(),"fresh view after an in-flight revocation is empty");
        helper.failScope=true;int before=helper.calls;r.reset("GET");chat.doGet(r.request,r.response);
        check(r.status==503&&!r.attrs.toString().contains("private SQL")&&!view(r).containsKey("messages")&&helper.calls==before,"scope lookup failure hides history and does not call the model");
        helper.failScope=false;r.reset("GET");chat.doGet(r.request,r.response);next=form(r);helper.failRead=true;r.reset("POST",next);chat.doPost(r.request,r.response);
        check(r.status==503&&helper.calls==before&&r.session.get("CHAT_BUSY")==null&&!r.attrs.toString().contains("private SQL"),"snapshot failure releases the request and shows only a safe UI error");
        helper.failRead=false;helper.visibility="fresh-for-demotion";r.reset("GET");chat.doGet(r.request,r.response);next=form(r);helper.demoteDuringReply=true;r.reset("POST",next);chat.doPost(r.request,r.response);
        check(r.status==401&&((List<?>)r.session.get("CHAT_HISTORY")).isEmpty(),"fresh account role is checked again before a generated reply is stored");
        System.out.println("PASS: "+checks+" database chat session/scope/error checks. No real database or provider calls.");
    }
}
