package ui;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import static ui.UiSupport.*;
/** Builds a detached snapshot for JSTL; JSP never mutates session state. */
public final class PreviewViews {
    private PreviewViews() {
    }
    public static Map<String,Object> prepare(String page,PreviewState s,HttpServletRequest r) {
        Map<String,Object> ui=row("csrf",s.csrf,"profile",copy(s.profile),"page",page);
        String form=Boolean.TRUE.equals(r.getAttribute("uiHideDialog"))?"":param(r,"form");
        if(r.getAttribute("uiFormName")!=null)form=str(r.getAttribute("uiFormName"));
        String q=input(r,"q",200);
        ui.put("q",q);
        ui.put("form",form);
        ui.put("dialog",false);
        if("USER_MANAGER".equals(page))users(ui,s,r,form);
        else if("PERMISSION_MANAGER".equals(page))permissions(ui,s,r,form);
        else if("ACCOUNT_MANAGER".equals(page)) {
            ui.put("edit",bound(r,s.profile));
            if("otp".equals(form)&&s.pendingProfile!=null) {
                ui.put("dialog",true);
                ui.put("pendingProfile",copy(s.pendingProfile));
                ui.put("dialogTitle","Xác Nhận Email");
            }
        }
        else if("DEVICE_CONTROL".equals(page)) {
            List<Map<String,Object>> relays=new ArrayList<>();
            for(Map<String,Object> item:s.relays) {
                Map<String,Object> row=copyMap(item);
                row.put("commandLabel","valve".equals(item.get("kind"))?(yes(item.get("on"))?"ĐÓNG VAN":"MỞ VAN"):(yes(item.get("on"))?"TẮT SỚM":"BẬT ĐÈN"));
                relays.add(row);
            }
            ui.put("relays",relays);
        }
        else if("DEVICE_MANAGER".equals(page))devices(ui,s,r,form);
        else if("SCHEDULE_MANAGER".equals(page))schedules(ui,s,r,form);
        else if("CONTROL_HISTORY".equals(page))history(ui,s,r,form);
        return ui;
    }
    private static Map<String,Object> bound(HttpServletRequest r,Map<String,Object> defaults) {
        Map<String,Object> result=copyMap(defaults);
        if("POST".equals(r.getMethod()))for(String key:Arrays.asList("fullName","userName","email","phone","role","nodeId","name","ip","hostname",
        "location","gpio","wiring","type","boot","rating","runAt","command")) {
            String value=r.getParameter(key);
            if(value!=null)result.put(key,value.substring(0,Math.min(value.length(),300)));
        }
        return result;
    }
    private static void dialog(Map<String,Object> ui,String title,String op,Map<String,Object> target) {
        ui.put("dialog",true);
        ui.put("dialogTitle",title);
        ui.put("postOperation",op);
        ui.put("target",copy(target));
    }
    private static String roleClass(Object role) {
        return "ADMIN".equals(role)?"admin":"OPERATION".equals(role)?"operator":"viewer";
    }
    private static void users(Map<String,Object> ui,PreviewState s,HttpServletRequest r,String form) {
        int total=(s.users.size()+5)/6,page=1;
        try {
            page=Math.max(1,Math.min(Math.max(total,1),Integer.parseInt(param(r,"p"))));
        }
        catch(NumberFormatException ignored) {
        }
        List<Map<String,Object>> items=new ArrayList<>();
        for(int i=(page-1)*6;i<Math.min(page*6,s.users.size());i++) {
            Map<String,Object> u=copyMap(s.users.get(i));
            u.put("initials",initials(u.get("fullName")));
            u.put("roleClass",roleClass(u.get("role")));
            u.put("roleLabel","OPERATION".equals(u.get("role"))?"OPERATOR":u.get("role"));
            items.add(u);
        }
        ui.putAll(row("users",items,"pageNumber",page,"pageCount",Math.max(total,1)));
        if("add".equals(form)) {
            dialog(ui,"Thêm Người Dùng Mới","addUser",row());
            ui.put("edit",bound(r,row("role","VIEWER")));
        }
        else if(Arrays.asList("delete","lock","password").contains(form)) {
            Map<String,Object> u=find(s.users,"id",param(r,"id"));
            require(!yes(u.get("superAdmin")),"SuperAdmin được bảo vệ.");
            dialog(ui,"delete".equals(form)?"Xóa Tài Khoản?":"lock".equals(form)?"Đổi Trạng Thái Tài Khoản?":"Đặt Lại Mật Khẩu","delete".equals(form)?"deleteUser":"lock".equals(form)?"toggleUser":"resetPassword",
            u);
        }
    }
    private static void permissions(Map<String,Object> ui,PreviewState s,HttpServletRequest r,String form) {
        String active=param(r,"viewer");
        if(active.isEmpty())active=str(s.viewers.get(0).get("id"));
        Map<String,Object> target=find(s.viewers,"id",active),draft=map(s.draftPermissions.get(active));
        List<Map<String,Object>> viewers=new ArrayList<>();
        for(Map<String,Object> v:s.viewers) {
            Map<String,Object> item=copyMap(v);
            int count=0;
            for(Object grant:map(s.draftPermissions.get(str(v.get("id")))).values())if(yes(map(grant).get("canView")))count++;
            item.put("grantCount",count);
            item.put("selected",active.equals(v.get("id")));
            if(normalize(str(v.get("name"))+" "+v.get("id")).contains(normalize(ui.get("q"))))viewers.add(item);
        }
        List<Map<String,Object>> groups=new ArrayList<>();
        int granted=0;
        for(Map<String,Object> device:s.permissionDevices) {
            Map<String,Object> group=copyMap(device);
            for(Map<String,Object> sw:rows(list(group.get("switches")))) {
                Map<String,Object> grant=map(draft.get(str(sw.get("id"))));
                sw.putAll(grant);
                if(yes(grant.get("canView")))granted++;
            }
            groups.add(group);
        }
        ui.putAll(row("viewers",viewers,"viewerTotal",s.viewers.size(),"activeViewer",copy(target),"permissionDevices",groups,"permissionCount",
        granted,"permissionTotal",draft.size(),"dirty",!draft.equals(s.savedPermissions.get(active))));
        if("revoke".equals(form))dialog(ui,"Hủy Toàn Bộ Quyền?","revokePermissions",target);
    }
    private static void devices(Map<String,Object> ui,PreviewState s,HttpServletRequest r,String form) {
        String status=param(r,"status"),location=param(r,"location"),parent=param(r,"parent"),type=param(r,"type"),switchQuery=input(r,"sq",
        200);
        List<Map<String,Object>> nodes=new ArrayList<>(),switches=new ArrayList<>();
        Set<String> locations=new TreeSet<>();
        int online=0;
        for(Map<String,Object> node:s.nodes) {
            if(yes(node.get("online")))online++;
            locations.add(str(node.get("location")));
            if((status.isEmpty()||"all".equals(status)||("online".equals(status)==yes(node.get("online"))))&&(location.isEmpty()||"all".equals(location)||location.equals(node.get("location")))&&normalize(node.values()).contains(normalize(ui.get("q"))))nodes.add(copyMap(node));
        }
        for(Map<String,Object> sw:s.switches) {
            Map<String,Object> node=find(s.nodes,"id",str(sw.get("nodeId")));
            if((parent.isEmpty()||"all".equals(parent)||parent.equals(sw.get("nodeId")))&&(type.isEmpty()||"all".equals(type)||type.equals(sw.get("type")))&&normalize(str(sw.get("name"))+" "+sw.get("nodeId")+" GPIO "+sw.get("gpio")).contains(normalize(switchQuery))) {
                Map<String,Object> item=copyMap(sw);
                item.putAll(row("parentIp",node.get("ip"),"online",node.get("online"),"icon","light".equals(sw.get("type"))?"lightbulb":"fan".equals(sw.get("type"))?"fan":"motor".equals(sw.get("type"))?"droplet":"plug"));
                switches.add(item);
            }
        }
        ui.putAll(row("nodes",nodes,"allNodes",copy(s.nodes),"nodeTotal",s.nodes.size(),"nodeOnline",online,"nodeOffline",s.nodes.size()-online,
        "locations",new ArrayList<>(locations),"switches",switches,"switchTotal",s.switches.size(),"status",status,"location",location,"parent",
        parent,"type",type,"sq",switchQuery));
        String id=param(r,"id");
        if("node".equals(form)) {
            Map<String,Object> item=id.isEmpty()?row():find(s.nodes,"id",id);
            dialog(ui,id.isEmpty()?"Thêm Thiết Bị ESP32":"Chỉnh Sửa Thiết Bị ESP32","saveNode",item);
            ui.put("edit",bound(r,item));
        }
        else if("switch".equals(form)) {
            Map<String,Object> item=id.isEmpty()?row("wiring","NO","type","light","boot","OFF"):find(s.switches,"key",id);
            dialog(ui,id.isEmpty()?"Gán Switch / Relay Vào ESP32":"Chỉnh Sửa Switch","saveSwitch",item);
            ui.put("edit",bound(r,item));
        }
        else if("switchSchedule".equals(form)) {
            Map<String,Object> item=find(s.switches,"key",id);
            dialog(ui,"Hẹn Giờ Switch","saveSwitchSchedule",item);
            Map<String,Object> edit=item.get("schedule")==null?row("command","ON"):copyMap(map(item.get("schedule")));
            if(edit.containsKey("action"))edit.put("command",edit.get("action"));
            ui.put("edit",bound(r,edit));
        }
        else if(Arrays.asList("deleteNode","deleteSwitch","rebootNode").contains(form)) {
            Map<String,Object> item=form.endsWith("Node")?find(s.nodes,"id",id):find(s.switches,"key",id);
            dialog(ui,"rebootNode".equals(form)?"Mô Phỏng Reboot?":"Xóa Bản Ghi?",form,item);
        }
    }
    private static void schedules(Map<String,Object> ui,PreviewState s,HttpServletRequest r,String form) {
        LocalDateTime now=LocalDateTime.now(ZONE),earliest=null;
        String nextId="",status=param(r,"status"),relayFilter=param(r,"relay");
        int enabled=0;
        List<Map<String,Object>> items=new ArrayList<>();
        for(Map<String,Object> item:s.schedules) {
            LocalDateTime next=next(item,now);
            if("once".equals(item.get("mode"))&&next==null)item.put("enabled",false);
            if(yes(item.get("enabled"))) {
                enabled++;
                if(next!=null&&(earliest==null||next.isBefore(earliest))) {
                    earliest=next;
                    nextId=str(item.get("id"));
                }
            }
        }
        for(Map<String,Object> item:s.schedules) {
            Map<String,Object> relay=find(s.scheduleRelays,"id",str(item.get("switchId")));
            if((status.isEmpty()||"all".equals(status)||("enabled".equals(status)==yes(item.get("enabled"))))&&(relayFilter.isEmpty()||"all".equals(relayFilter)||relayFilter.equals(item.get("switchId")))&&normalize(str(item.get("name"))+" "+relay.values()+" GPIO "+relay.get("gpio")).contains(normalize(ui.get("q")))) {
                Map<String,Object> view=copyMap(item);
                LocalDateTime next=next(item,now);
                view.putAll(row("relay",copy(relay),"expired",next==null,"isNext",nextId.equals(item.get("id")),"nextLabel",yes(item.get("enabled"))?"Dự kiến: "+occurrence(next,
                now):next==null?"Đã qua giờ. Sửa lịch để chọn ngày mới.":"Lịch đang tạm dừng."));
                items.add(view);
            }
        }
        Map<String,Object> edit=param(r,"edit").isEmpty()?row("id","","name","","switchId","esp01-g16","time","06:30","action","ON","mode",
        "weekly","date",now.toLocalDate().toString(),"days",new ArrayList<Object>(Arrays.asList(1,2,3,4,5))):copyMap(find(s.schedules,"id",
        param(r,"edit")));
        if(r.getAttribute("uiDraft")!=null)edit=map(r.getAttribute("uiDraft"));
        if(!edit.containsKey("date")||str(edit.get("date")).isEmpty())edit.put("date",now.toLocalDate().toString());
        List<Map<String,Object>> days=new ArrayList<>();
        for(int day:Arrays.asList(1,2,3,4,5,6,0))days.add(row("value",day,"label",day==0?"CN":"T"+(day+1)));
        ui.putAll(row("schedules",items,"scheduleTotal",s.schedules.size(),"scheduleEnabled",enabled,"schedulePaused",s.schedules.size()-enabled,
        "scheduleRelays",copy(s.scheduleRelays),"status",status,"relayFilter",relayFilter,"edit",edit,"weekDays",days,"clock",now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
        "nextSummary",earliest==null?"Chưa có lịch đang bật":occurrence(earliest,now)));
        if("delete".equals(form))dialog(ui,"Xóa Lịch Hẹn Giờ?","deleteSchedule",find(s.schedules,"id",param(r,"id")));
    }
    public static Map<String,Object> trace(PreviewState s,String id) {
        Map<String,Object> record=find(s.history,"id",id),actor=map(s.actors.get(str(record.get("actor"))));
        return row("preview",true,"event_id",record.get("id"),"timestamp",record.get("timestamp"),"actor",row("username",actor.get("username"),
        "role",actor.get("role"),"source",record.get("source")),"target",row("device_id",record.get("device"),"ip",record.get("ip"),"switch_name",
        record.get("switchName"),"gpio",record.get("gpio"),"relay",record.get("relay")),"command",row("state",record.get("command"),"value",
        "ON".equals(record.get("command"))?1:0),"result",row("code",record.get("result"),"rtt_ms",record.get("rtt")));
    }
    private static void history(Map<String,Object> ui,PreviewState s,HttpServletRequest r,String form) {
        String device=param(r,"device"),command=param(r,"command"),result=param(r,"result"),sort=param(r,"sort");
        List<Map<String,Object>> records=new ArrayList<>();
        Set<String> devices=new TreeSet<>();
        for(Map<String,Object> record:s.history) {
            devices.add(str(record.get("device")));
            Map<String,Object> actor=map(s.actors.get(str(record.get("actor"))));
            String code=str(record.get("result"));
            boolean match=result.isEmpty()||"all".equals(result)||("success".equals(result)?"200_OK".equals(code):"error".equals(result)?!Arrays.asList("200_OK",
            "DEMO").contains(code):result.equals(code));
            if((device.isEmpty()||"all".equals(device)||device.equals(record.get("device")))&&(command.isEmpty()||"all".equals(command)||command.equals(record.get("command")))&&match&&normalize("#LOG-"+record.get("id")+" "+record.values()+" "+actor.values()+" GPIO "+record.get("gpio")).contains(normalize(ui.get("q")))) {
                Map<String,Object> item=copyMap(record);
                item.putAll(row("actorInfo",copy(actor),"resultLabel","200_OK".equals(code)?"200 OK":"DEMO".equals(code)?"MÔ PHỎNG":code,"resultClass",
                "200_OK".equals(code)?"is-success":"DEMO".equals(code)?"is-demo":"is-error","timestampLabel",OffsetDateTime.parse(str(record.get("timestamp"))).atZoneSameInstant(ZONE).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                records.add(item);
            }
        }
        records.sort((a,b)-> {
            int cmp=OffsetDateTime.parse(str(a.get("timestamp"))).toInstant().compareTo(OffsetDateTime.parse(str(b.get("timestamp"))).toInstant());if(cmp==0)cmp=Integer.compare((Integer)a.get("id"),
            (Integer)b.get("id"));return "asc".equals(sort)?cmp:-cmp;
        });
        ui.putAll(row("history",records,"historyTotal",s.history.size(),"historyDevices",new ArrayList<>(devices),"device",device,"command",
        command,"result",result,"sort",sort,"readAt",LocalTime.now(ZONE).format(DateTimeFormatter.ofPattern("HH:mm:ss"))));
        if("trace".equals(form)) {
            Map<String,Object> record=find(s.history,"id",param(r,"id"));
            dialog(ui,"Chi Tiết Nhật Ký","",record);
            ui.put("traceJson",json(trace(s,param(r,"id"))));
        }
    }
}
