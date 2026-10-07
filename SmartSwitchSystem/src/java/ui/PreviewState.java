package ui;
import java.io.Serializable;
import java.time.*;
import java.util.*;
import static ui.UiSupport.*;
/** Session-local demonstration state; never writes to a DAO or sends a device command. */
public final class PreviewState implements Serializable {
    private static final long serialVersionUID=1L;
    public final String csrf=UUID.randomUUID().toString();
    public final List<Map<String,Object>> users=rows(PreviewSeeds.users());
    public final List<Map<String,Object>> nodes=rows(PreviewSeeds.nodes());
    public final List<Map<String,Object>> switches=rows(PreviewSeeds.switches());
    public final List<Map<String,Object>> permissionDevices=rows(PreviewSeeds.permissionDevices());
    public final List<Map<String,Object>> viewers=rows(PreviewSeeds.viewers());
    public final List<Map<String,Object>> scheduleRelays=rows(PreviewSeeds.scheduleRelays());
    public final List<Map<String,Object>> schedules=rows(PreviewSeeds.schedules());
    public final List<Map<String,Object>> history=rows(PreviewSeeds.history());
    public final Map<String,Object> actors=PreviewSeeds.actors();
    public final List<Map<String,Object>> relays=new ArrayList<>();
    public final Map<String,Object> profile=row("fullName","Nguyễn Văn An","userName","admin_sys","email","nguyenvanan.iot@datacenter.vn",
    "phone","(+84) 982.019.233");
    public final Map<String,Object> savedPermissions=new LinkedHashMap<>();
    public final Map<String,Object> draftPermissions=new LinkedHashMap<>();
    public Map<String,Object> pendingProfile;
    public int nextUser=7,nextSwitch=5,nextSchedule=7,nextHistory=89422;
    public PreviewState() {
        relays.add(row("id","uv-21","name","Đèn UV khử khuẩn","gpio",21,"node","ESP32-03","location","Kho Thiết Bị","mode","AUTO_CYCLE_MODE",
        "kind","uv","on",true));
        relays.add(row("id","valve-22","name","Van cấp nước TĐ","gpio",22,"node","ESP32-03","location","Kho Thiết Bị","mode","VALVE_SOLENOID",
        "kind","valve","on",false));
        for(Map<String,Object> viewer:viewers) {
            Map<String,Object> permissions=new LinkedHashMap<>();
            Map<String,Object> grants=map(viewer.get("grants"));
            for(Map<String,Object> device:permissionDevices)for(Map<String,Object> item:rows(list(device.get("switches")))) {
                String id=str(item.get("id"));
                List<Object> grant=grants.containsKey(id)?list(grants.get(id)):Arrays.asList(false,false);
                permissions.put(id,row("canView",yes(grant.get(0))||yes(grant.get(1)),"canControl",yes(grant.get(1))));
            }
            savedPermissions.put(str(viewer.get("id")),permissions);
            draftPermissions.put(str(viewer.get("id")),copy(permissions));
        }
    }
    public void log(String device,String name,int gpio,String command) {
        history.add(0,row("id",nextHistory++,"timestamp",OffsetDateTime.now(ZONE).withNano(0).toString(),"actor","admin","source","WEB_UI",
        "device",device,"ip","—","switchName",name,"gpio",gpio,"relay",0,"command",command,"result","DEMO","rtt",null));
        while(history.size()>200)history.remove(history.size()-1);
    }
}
