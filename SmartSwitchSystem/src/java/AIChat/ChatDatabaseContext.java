package AIChat;

import dao.ChatDatabaseDAO;
import dto.ChatDatabaseSnapshotDTO;
import dto.ChatMessageDTO;
import dto.ChatSourceDTO;
import enums.Role;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.*;

/** Privacy allowlist and bounded context formatting; no model-controlled query executor. */
public class ChatDatabaseContext {
    protected ChatDatabaseDAO database()throws SQLException,ClassNotFoundException{return new ChatDatabaseDAO();}
    public String scope(String userId,Role role)throws SQLException,ClassNotFoundException {try(ChatDatabaseDAO dao=database()){return dao.scope(userId,role);}}
    public ChatDatabaseSnapshotDTO read(String userId,Role role,String question)throws SQLException,ClassNotFoundException {
        try(ChatDatabaseDAO dao=database()){return dao.read(userId,role,kinds(question));}
    }
    public Set<ChatDataKind> kinds(String question) {
        if(isControlAccessQuestion(question))return EnumSet.of(ChatDataKind.CONTROLLABLE_SWITCHES);
        String q=normalizeQuestion(question);
        Set<ChatDataKind> selected=EnumSet.noneOf(ChatDataKind.class);
        boolean history=contains(q,"history","lich su","nhat ky","audit","last command");
        boolean schedule=contains(q,"schedule","lich hen","hen gio","daily","run time","gio bat","gio tat");
        if(history)selected.add(ChatDataKind.HISTORY);
        if(schedule)selected.add(ChatDataKind.SCHEDULES);
        if(contains(q,"device","devices","thiet bi","esp32","online","offline"))selected.add(ChatDataKind.DEVICES);
        if(q.matches(".*\\besp[a-z0-9]*\\b.*"))selected.add(ChatDataKind.DEVICES);
        if(contains(q,"sw","switch","switches","gpio","cong tac","relay","trang thai","status","dang bat","dang tat","den","quat","bom","van")||!history&&!schedule&&contains(q,"bao nhieu","co may","danh sach"))selected.add(ChatDataKind.SWITCHES);
        if(q.matches(".*\\bsw[a-z0-9]+\\b.*"))selected.add(ChatDataKind.SWITCHES);
        return selected;
    }
    private boolean contains(String text,String... terms){for(String term:terms)if(text.contains(" "+term+" "))return true;return false;}
    private String normalizeQuestion(String value) {
        return " "+Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .replace('\u0111','d').replace('\u0110','D').toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ")+" ";
    }
    public boolean isControlAccessQuestion(String question) {
        String q=normalizeQuestion(question);
        if(contains(q,"history","lich su","nhat ky","audit","schedule","lich hen","hen gio"))return false;
        if(contains(q,"lam the nao","nhu the nao","huong dan","how do","how to","set permissions","change permissions","thu hoi","revoked","denied",
                "khong duoc","khong co quyen","khong the","khong dieu khien","chua duoc","not control","not operate","cannot","can not","can t"))return false;
        if(contains(q,"ban co the","ban dieu khien","you control","you turn")&&!contains(q,"toi","minh","my","me","i"))return false;
        boolean operation=contains(q,"dieu khien","control","controllable","operate","toggle")
                ||contains(q,"bat tat","bat","tat","turn on","turn off")&&!contains(q,"dang bat","dang tat","trang thai","status");
        boolean access=contains(q,"quyen","duoc","duoc phep","co the","allowed","permission","permissions","access","can i","can we","controllable");
        boolean listing=contains(q,"nao","nhung","cac","danh sach","bao nhieu","which","what","list","how many","my controllable");
        return operation&&access&&listing;
    }
    /** Exact permission facts are rendered from the same privacy-filtered, bounded data as model context. */
    public ChatMessageDTO controlAccessAnswer(ChatDatabaseSnapshotDTO snapshot,String question) {
        if(!isControlAccessQuestion(question))return null;
        Map<?,?> document=(Map<?,?>)new ChatJson().parse(format(snapshot));
        Map<?,?> datasets=(Map<?,?>)document.get("datasets");
        Map<?,?> controls=(Map<?,?>)datasets.get(ChatDataKind.CONTROLLABLE_SWITCHES.name());
        if(controls==null)throw new IllegalStateException("Missing control-access snapshot.");
        List<?> rows=(List<?>)controls.get("rows");
        long total=((Number)((Map<?,?>)controls.get("totals")).get("total")).longValue();
        boolean vietnamese=contains(normalizeQuestion(question),"toi","minh","quyen","duoc","co the","dieu khien","nao");
        StringBuilder text=new StringBuilder();
        if(total==0) {
            text.append(vietnamese?"Hiện tài khoản của bạn chưa được cấp quyền điều khiển switch đang hoạt động nào. Hãy nhờ Admin hoặc Operator kiểm tra Device Permissions.":"Your account currently has no active switch-control permissions. Ask an Admin or Operator to check Device Permissions.");
        } else {
            text.append(vietnamese?"Bạn hiện có quyền điều khiển ":"You currently have permission to control ").append(total)
                    .append(vietnamese?" switch theo phân quyền trong database:":" switch(es), according to the current database permissions:");
            int number=0;
            for(Object value:rows) {
                Map<?,?> row=(Map<?,?>)value;
                text.append('\n').append(++number).append(". ").append(row.get("switchId")).append(" — ").append(row.get("switchName"))
                        .append(" | GPIO ").append(row.get("gpio")).append(" | ").append(row.get("deviceId")).append(" — ").append(row.get("deviceName"));
            }
            if(Boolean.TRUE.equals(controls.get("partial")))text.append(vietnamese?"\nĐang hiển thị ":"\nShowing ").append(rows.size()).append('/').append(total).append(vietnamese?" switch được cấp quyền; danh sách trả lời chưa đầy đủ.":" permitted switches; this reply is a partial list.");
            text.append(vietnamese?"\nBạn có thể tìm các mã switch này tại Device Control để bật/tắt. Việc điều khiển thực tế còn phụ thuộc kết nối ESP32.":"\nFind these switch IDs in Device Control to turn them on or off. Actual operation also depends on ESP32 connectivity.");
        }
        return new ChatMessageDTO("assistant",text.toString(),Collections.singletonList(new ChatSourceDTO("Device Control","DEVICE_CONTROL")));
    }
    private String[] columns(ChatDataKind kind) {
        switch(kind){
            case DEVICES:return new String[]{"deviceId","deviceName","state"};
            case SWITCHES:
            case CONTROLLABLE_SWITCHES:return new String[]{"switchId","switchName","gpio","state","deviceId","deviceName"};
            case SCHEDULES:return new String[]{"switchId","switchName","deviceId","deviceName","command","runTime","enabled"};
            case HISTORY:return new String[]{"switchId","switchName","deviceId","deviceName","command","result","controlTime"};
            default:throw new IllegalArgumentException("Unsupported dataset.");
        }
    }
    private String label(String value) {
        String clean=value.replaceAll("[\\p{Cntrl}]"," ");
        String inspected=Normalizer.normalize(clean,Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D');
        if(inspected.matches("(?is).*(?:[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}|\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b|(?:[0-9a-f]{2}[:-]){5}[0-9a-f]{2}|https?://|\\b[a-z0-9.-]+\\.(?:local|lan|internal|com|net|org|vn)\\b|(?:[0-9a-f]{0,4}:){2,}[0-9a-f:]{1,4}|AIza[0-9a-z_-]{20,}|sk-[0-9a-z_-]{12,}|P1\\$\\d+\\$|(?:password|passwd|secret|token|api.?key|hostname|host_name|mat khau)\\s*[:=]|\\+?\\d[\\d .()-]{7,}\\d).*"))return "[label hidden]";
        return clean.substring(0,Math.min(clean.length(),120));
    }
    public String format(ChatDatabaseSnapshotDTO snapshot) {
        ChatJson json=new ChatJson();StringBuilder out=new StringBuilder("{\"source\":\"saved database records\",\"capturedAt\":").append(json.quote(snapshot.getCapturedAt())).append(",\"datasets\":{");
        boolean firstGroup=true;
        for(ChatDataKind kind:ChatDataKind.values()) {
            List<Map<String,Object>> records=snapshot.getRecords().get(kind);if(records==null)continue;
            if(!firstGroup)out.append(',');firstGroup=false;
            out.append(json.quote(kind.name())).append(":{\"totals\":{");
            Map<String,Long> counts=snapshot.getTotals().get(kind);boolean firstCount=true;
            for(String field:new String[]{"total","online","offline","error","on","off","enabled"}) {
                if(counts==null||!counts.containsKey(field))continue;if(!firstCount)out.append(',');firstCount=false;
                out.append(json.quote(field)).append(':').append(Math.max(0L,counts.get(field)));
            }
            out.append("},\"rows\":[");int emitted=0;
            for(Map<String,Object> row:records) {
                StringBuilder entry=new StringBuilder("{");boolean firstValue=true;
                for(String field:columns(kind)) {
                    Object value=row.get(field);if(value==null)continue;
                    if(!firstValue)entry.append(',');firstValue=false;entry.append(json.quote(field)).append(':');
                    if(value instanceof Boolean)entry.append(value);
                    else if(value instanceof Number)entry.append(((Number)value).longValue());
                    else entry.append(json.quote(field.endsWith("Name")||field.endsWith("Id")?label(String.valueOf(value)):String.valueOf(value).substring(0,Math.min(String.valueOf(value).length(),80))));
                }
                entry.append('}');if(out.length()+entry.length()>12000)break;
                if(emitted++>0)out.append(',');out.append(entry);
            }
            long total=counts==null?records.size():counts.getOrDefault("total",(long)records.size());
            out.append("],\"partial\":").append(total>emitted).append('}');
        }
        return out.append("}}").toString();
    }
    public List<ChatSourceDTO> sources(ChatDatabaseSnapshotDTO snapshot) {
        List<ChatSourceDTO> sources=new ArrayList<>();
        for(ChatDataKind kind:snapshot.getRecords().keySet()) {
            String action=kind==ChatDataKind.SCHEDULES?"SCHEDULE_MANAGER":kind==ChatDataKind.HISTORY?"CONTROL_HISTORY":"DEVICE_CONTROL";
            if(sources.stream().noneMatch(s->action.equals(s.getAction())))sources.add(new ChatSourceDTO(kind==ChatDataKind.SCHEDULES?"Schedules":kind==ChatDataKind.HISTORY?"Control History":"Device Control",action));
        }
        return sources;
    }
}
