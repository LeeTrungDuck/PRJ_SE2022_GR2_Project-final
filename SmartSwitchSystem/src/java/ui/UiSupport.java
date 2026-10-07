package ui;
import java.text.Normalizer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
/** Small helpers for the server-rendered UI preview. */
public final class UiSupport {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private UiSupport() {
    }
    public static Map<String,Object> row(Object... pairs) {
        return PreviewSeeds.row(pairs);
    }
    @SuppressWarnings("unchecked") public static List<Map<String,Object>> rows(List<?> values) {
        return (List<Map<String,Object>>)(List<?>)values;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> map(Object value) {
        return (Map<String,Object>)value;
    }
    @SuppressWarnings("unchecked") public static List<Object> list(Object value) {
        return (List<Object>)value;
    }
    public static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
    public static boolean yes(Object value) {
        return Boolean.TRUE.equals(value);
    }
    public static String param(HttpServletRequest r, String key) {
        String value = r.getParameter(key);
        return value == null ? "" : value.trim();
    }
    public static String input(HttpServletRequest r, String key, int max) {
        String value = param(r,key);
        require(value.length() <= max, "Trường " + key + " vượt quá độ dài cho phép.");
        return value;
    }
    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    public static String required(HttpServletRequest r, String key, int max, String label) {
        String value = input(r,key,max);
        require(!value.isEmpty(), "Vui lòng nhập " + label + ".");
        return value;
    }
    public static String choice(HttpServletRequest r, String key, String... allowed) {
        String value = param(r,key);
        require(Arrays.asList(allowed).contains(value), "Giá trị " + key + " không hợp lệ.");
        return value;
    }
    public static String email(HttpServletRequest r) {
        String value=required(r,"email",254,"email");
        require(value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"Email không hợp lệ.");
        return value;
    }
    public static void password(HttpServletRequest r, String key, int min, boolean strong) {
        String value=r.getParameter(key);
        require(value!=null && value.length()>=min && value.length()<=128,"Mật khẩu cần từ "+min+" đến 128 ký tự.");
        if(strong) require(value.matches("(?s).*[A-Z].*") && value.matches("(?s).*\\d.*") && value.matches("(?s).*[^\\p{L}\\p{N}\\s].*"),"Mật khẩu cần có chữ hoa, chữ số và ký tự đặc biệt.");
    }
    public static String normalize(Object value) {
        return Normalizer.normalize(str(value),Normalizer.Form.NFD).replaceAll("\\p{M}","").replace('đ','d').replace('Đ','D').toLowerCase(Locale.ROOT).replaceAll("gpio\\s*0*(\\d+)",
        "gpio $1").trim();
    }
    public static Map<String,Object> find(List<Map<String,Object>> items,String key,String id) {
        for(Map<String,Object> item:items) if(str(item.get(key)).equals(id)) return item;
        throw new IllegalArgumentException("Không tìm thấy bản ghi. Vui lòng tải lại trang.");
    }
    public static Map<String,Object> maybeFind(List<Map<String,Object>> items,String key,String id) {
        for(Map<String,Object> item:items) if(str(item.get(key)).equals(id)) return item;
        return null;
    }
    public static int number(String value,int min,int max,String label) {
        try {
            int n=Integer.parseInt(value);
            require(n>=min&&n<=max,label+" ngoài phạm vi cho phép.");
            return n;
        }
        catch(NumberFormatException e) {
            throw new IllegalArgumentException(label+" phải là số nguyên.");
        }
    }
    public static String initials(Object name) {
        String[] words=str(name).trim().split("\\s+");
        if(words.length==0||words[0].isEmpty()) return "?";
        return (words[0].substring(0,1)+(words.length>1?words[words.length-1].substring(0,1):"")).toUpperCase(Locale.ROOT);
    }
    public static String ipv4(String value) {
        String[] parts=value.split("\\.",-1);
        require(parts.length==4,"Địa chỉ IPv4 không hợp lệ.");
        List<String> result=new ArrayList<>();
        for(String part:parts) {
            require(part.matches("\\d{1,3}"),"Địa chỉ IPv4 không hợp lệ.");
            result.add(String.valueOf(number(part,0,255,"Địa chỉ IPv4")));
        }
        return String.join(".",result);
    }
    public static LocalDateTime future(String value) {
        try {
            LocalDateTime time=LocalDateTime.parse(value);
            require(time.isAfter(LocalDateTime.now(ZONE)),"Thời gian phải nằm trong tương lai.");
            return time;
        }
        catch(java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Ngày giờ không hợp lệ.");
        }
    }
    public static LocalTime time(String value) {
        require(value.matches("([01]\\d|2[0-3]):[0-5]\\d"),"Giờ phải có định dạng HH:mm (24h).");
        return LocalTime.parse(value);
    }
    public static LocalDateTime next(Map<String,Object> schedule,LocalDateTime now) {
        LocalTime time=time(str(schedule.get("time")));
        if("once".equals(schedule.get("mode"))) {
            LocalDateTime candidate;
            try {
                candidate=LocalDate.parse(str(schedule.get("date"))).atTime(time);
            }
            catch(java.time.format.DateTimeParseException e) {
                return null;
            }
            return candidate.isAfter(now)?candidate:null;
        }
        for(int offset=0;offset<=7;offset++) {
            LocalDateTime candidate=now.toLocalDate().plusDays(offset).atTime(time);
            int day=candidate.getDayOfWeek().getValue()%7;
            if(list(schedule.get("days")).contains(day)&&candidate.isAfter(now))return candidate;
        }
        return null;
    }
    public static String occurrence(LocalDateTime time,LocalDateTime now) {
        if(time==null)return "Đã qua giờ dự kiến";
        String day=time.toLocalDate().equals(now.toLocalDate())?"Hôm nay":time.toLocalDate().equals(now.toLocalDate().plusDays(1))?"Ngày mai":time.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        return day+", "+time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }
    public static Object copy(Object value) {
        if(value instanceof Map) {
            Map<String,Object> result=new LinkedHashMap<>();
            map(value).forEach((k,v)->result.put(k,copy(v)));
            return result;
        }
        if(value instanceof List) {
            List<Object> result=new ArrayList<>();
            for(Object item:list(value))result.add(copy(item));
            return result;
        }
        return value;
    }
    public static Map<String,Object> copyMap(Map<String,Object> value) {
        return map(copy(value));
    }
    public static String json(Object value) {
        if(value==null)return "null";
        if(value instanceof Boolean||value instanceof Number)return value.toString();
        if(value instanceof Map) {
            List<String> parts=new ArrayList<>();
            map(value).forEach((k,v)->parts.add(json(k)+": "+json(v)));
            return "{\n"+String.join(",\n",parts)+"\n}";
        }
        if(value instanceof List) {
            List<String> parts=new ArrayList<>();
            for(Object item:list(value))parts.add(json(item));
            return "["+String.join(", ",parts)+"]";
        }
        StringBuilder out=new StringBuilder("\"");
        for(char c:str(value).toCharArray()) {
            switch(c) {
                case '"':out.append("\\\"");
                break;
                case '\\':out.append("\\\\");
                break;
                case '\n':out.append("\\n");
                break;
                case '\r':out.append("\\r");
                break;
                case '\t':out.append("\\t");
                break;
                default:if(c<32)out.append(String.format("\\u%04x",(int)c));
                else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
