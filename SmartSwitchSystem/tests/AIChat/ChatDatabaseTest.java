package AIChat;

import adminaudit.AdminFixture;
import dao.ChatDatabaseDAO;
import dto.*;
import enums.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.servlet.ServletContext;

/** JDBC and outbound-prompt probes use an isolated in-memory fixture, never production SQL. */
public final class ChatDatabaseTest {
    private static int checks;
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private static final List<String> sql=new ArrayList<>(),getters=new ArrayList<>();
    private static Object call(Object target,Method method,Object[] args)throws Throwable{try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}}
    private static Connection spy(Connection raw) {
        return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(o,m,a)->{
            if(!"prepareStatement".equals(m.getName()))return call(raw,m,a);
            sql.add((String)a[0]);PreparedStatement statement=(PreparedStatement)call(raw,m,a);
            return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(s,sm,sa)->{
                if("executeUpdate".equals(sm.getName())||"execute".equals(sm.getName()))throw new AssertionError("Chat attempted a write.");
                Object result=call(statement,sm,sa);
                if(!(result instanceof ResultSet))return result;
                return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(r,rm,ra)->{if(rm.getName().startsWith("get")&&ra!=null)getters.add(String.valueOf(ra[0]));return call(result,rm,ra);});
            });
        });
    }
    private static void writeFixture(String query,Object... values)throws Exception {
        try(Connection c=AdminFixture.connection();PreparedStatement ps=c.prepareStatement(query)){for(int i=0;i<values.length;i++)ps.setObject(i+1,values[i]);ps.executeUpdate();}
    }
    private static ChatDatabaseSnapshotDTO read(String actor,Role role)throws Exception {
        try(ChatDatabaseDAO dao=new ChatDatabaseDAO(spy(AdminFixture.connection()))){return dao.read(actor,role,EnumSet.allOf(ChatDataKind.class));}
    }
    public static void main(String[] args)throws Exception {
        AdminFixture fixture=new AdminFixture();fixture.init();
        try {
            writeFixture("INSERT INTO tblControl_History(user_id,switch_id,command,result,control_time) VALUES(?,?,?,?,?)","U1","SW1","ON","ON",Timestamp.valueOf("2026-10-16 06:30:00"));
            writeFixture("INSERT INTO tblControl_History(user_id,switch_id,command,result,control_time) VALUES(?,?,?,?,?)","U3","SW3","OFF","OFF",Timestamp.valueOf("2026-10-16 07:00:00"));
            ChatDatabaseContext context=new ChatDatabaseContext(){protected ChatDatabaseDAO database(){return new ChatDatabaseDAO(spy(AdminFixture.connection()));}};
            ChatDatabaseSnapshotDTO admin=read("U1",Role.ADMIN),operator=read("U3",Role.OPERATOR),viewer=read("U2",Role.VIEWER);
            check(admin.getRecords().get(ChatDataKind.SWITCHES).size()==3&&operator.getRecords().get(ChatDataKind.SWITCHES).size()==3,"Admin and Operator see all active switches on active devices");
            check(viewer.getRecords().get(ChatDataKind.SWITCHES).size()==1&&viewer.getRecords().get(ChatDataKind.DEVICES).size()==1,"Viewer sees only the active grant and its parent device");
            check(viewer.getRecords().get(ChatDataKind.SCHEDULES).size()==1&&viewer.getRecords().get(ChatDataKind.HISTORY).size()==1,"Viewer schedule/history rows follow current switch visibility, not actor identity");
            String json=context.format(viewer);Map<?,?> parsed=(Map<?,?>)new ChatJson().parse(json);
            check(parsed.containsKey("capturedAt")&&json.contains("SW1")&&!json.contains("SW3")&&!json.contains("SW4")&&!json.contains("SW5"),"serialized snapshot is valid JSON and excludes forbidden/inactive switches");
            check(json.contains("2026-10-16T06:30")&&json.contains("\"result\":\"ON\""),"typed timestamps and actual result enums survive privacy filtering");
            check(!json.contains("U1")&&!json.contains("U2")&&!json.contains("U3")&&!json.contains("host_name")&&!json.contains("192.0.2")&&!json.contains("@example")&&!json.contains("090000000"),"no account IDs, actors, contacts or network fields enter the snapshot");
            check(sql.stream().allMatch(q->q.startsWith("SELECT ")&&!q.contains("SELECT *")&&q.contains("auth.user_id=?")&&q.contains("auth.role=?")),"chat DAO executes only fixed SELECT projections with a fresh account/role guard");
            for(String query:sql){String projection=query.substring(0,query.toLowerCase(Locale.ROOT).indexOf(" from ")).toLowerCase(Locale.ROOT);check(!projection.matches("(?s).*(password|host_name|email|phone|user_id|granted_by).*"),"private columns are absent from SELECT projections");}
            check(getters.stream().noneMatch(g->g.matches("(?i).*(password|host_name|email|phone|user_id|granted_by).*")),"DAO mapping never requests a private column from JDBC");
            check(read("U2",Role.ADMIN).getRecords().values().stream().allMatch(List::isEmpty),"a forged or stale elevated role cannot retrieve administrative rows");
            check(context.kinds("đổi mật khẩu").isEmpty()&&context.kinds("How do I change my email?").isEmpty(),"account-help questions do not retrieve operational rows");
            check(context.kinds("lịch sử điều khiển").equals(EnumSet.of(ChatDataKind.HISTORY))&&context.kinds("lịch hẹn giờ").contains(ChatDataKind.SCHEDULES),"Vietnamese database intents select the correct bounded groups");
            check(context.kinds("SW1 có bật không?").contains(ChatDataKind.SWITCHES),"a switch identifier selects saved switch data");
            check(context.kinds("Xem ESP1").contains(ChatDataKind.DEVICES),"a device identifier selects saved device data");
            writeFixture("UPDATE tblSwitch SET switch_name=? WHERE switch_id=?","secret@example.invalid","SW1");
            String masked=context.format(read("U2",Role.VIEWER));check(masked.contains("[label hidden]")&&!masked.contains("secret@example.invalid"),"obvious personal data in an otherwise permitted label is masked");
            for(String label:new String[]{"Mật khẩu=private-value","0901234567","192.0.2.77","AA:BB:CC:DD:EE:FF","private-host.local","https://private.example.invalid"}) {
                writeFixture("UPDATE tblSwitch SET switch_name=? WHERE switch_id=?",label,"SW1");
                String filtered=context.format(read("U2",Role.VIEWER));
                check(filtered.contains("[label hidden]")&&!filtered.contains(label),"private contact/network/credential patterns in labels do not reach context");
            }
            Map<ChatDataKind,List<Map<String,Object>>> contaminatedRows=new EnumMap<>(ChatDataKind.class);
            Map<String,Object> contaminated=new LinkedHashMap<>(viewer.getRecords().get(ChatDataKind.SWITCHES).get(0));contaminated.put("email","not-for-model@example.invalid");contaminated.put("password","private-hash");contaminated.put("userId","private-owner");
            contaminatedRows.put(ChatDataKind.SWITCHES,Collections.singletonList(contaminated));
            String restricted=context.format(new ChatDatabaseSnapshotDTO("private-scope","2026-10-16T00:00:00+07:00",contaminatedRows,viewer.getTotals()));
            check(!restricted.contains("not-for-model")&&!restricted.contains("private-hash")&&!restricted.contains("private-owner")&&!restricted.contains("private-scope"),"formatter independently drops extra fields and internal scope metadata");
            String scopeBefore=context.scope("U2",Role.VIEWER);
            writeFixture("UPDATE tblDevice_Permission SET is_active=? WHERE permission_id=?",false,2);
            ChatDatabaseSnapshotDTO revoked=read("U2",Role.VIEWER);
            check(!scopeBefore.equals(revoked.getScope())&&revoked.getRecords().values().stream().allMatch(List::isEmpty),"revocation changes scope and removes all dependent records");
            writeFixture("UPDATE tblDevice_Permission SET is_active=? WHERE permission_id=?",true,2);
            for(int i=0;i<18;i++)writeFixture("INSERT INTO tblSwitch(switch_id,device_id,switch_name,gpio_pin,status,is_active) VALUES(?,?,?,?,?,?)","BOUND"+i,"ESP1","Safe switch "+i,24+i,"OFF",true);
            ChatDatabaseSnapshotDTO bounded=read("U1",Role.ADMIN);String boundedJson=context.format(bounded);
            check(bounded.getRecords().get(ChatDataKind.SWITCHES).size()==12&&bounded.getTotals().get(ChatDataKind.SWITCHES).get("total")==21L&&boundedJson.contains("\"partial\":true"),"bounded lists retain authorized full totals and an honest partial marker");
            check(boundedJson.length()<14000,"database prompt context remains bounded");
            ServletContext servlet=(ServletContext)Proxy.newProxyInstance(ServletContext.class.getClassLoader(),new Class<?>[]{ServletContext.class},(o,m,a)->"getResourceAsStream".equals(m.getName())?Files.newInputStream(Paths.get("web"+(String)a[0])):null);
            String[] prompt={null};int[] calls={0};ChatClient capture=new ChatClient("https://example.invalid/v1","fixture-secret","fixture-model","max_tokens",1000){public String complete(String instructions,List<ChatMessageDTO> history,String question){prompt[0]=instructions;calls[0]++;return "Read the saved switch state.";}};
            ChatAssistant helper=new ChatAssistant(capture,new ChatKnowledgeBase(servlet),context);
            String userScope=helper.scope("U2",Role.VIEWER);ChatMessageDTO reply=helper.answer("U2",Role.VIEWER,Collections.emptyList(),"List my switch status",userScope);
            check(calls[0]==1&&prompt[0].contains("APPROVED DATABASE SNAPSHOT")&&prompt[0].contains("SW1")&&!prompt[0].contains("SW3")&&!prompt[0].contains("secret@example.invalid")&&!prompt[0].contains("Original Admin")&&!prompt[0].contains("fixture-secret"),"actual generation input contains scoped facts and no seeded private data");
            check(reply.getSources().stream().anyMatch(s->"DEVICE_CONTROL".equals(s.getAction())),"database answers link to the corresponding permitted page");
            try{helper.answer("U2",Role.VIEWER,Collections.emptyList(),"switches","outdated-scope");throw new AssertionError("Changed scope accepted");}catch(ChatException expected){check(expected.getStatus()==403&&calls[0]==1,"scope mismatch blocks model generation");}
            try(ChatDatabaseDAO invalid=new ChatDatabaseDAO(spy(AdminFixture.connection()))){try{invalid.scope("U2",null);throw new AssertionError("Null role accepted");}catch(IllegalArgumentException expected){checks++;}}
            System.out.println("PASS: "+checks+" database projection, privacy, permission and prompt checks. Only isolated fixtures were used.");
        } finally {fixture.destroy();}
    }
}
