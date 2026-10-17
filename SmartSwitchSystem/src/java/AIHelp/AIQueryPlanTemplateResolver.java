package AIHelp;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates safe plans for high-confidence, frequently used Smart Switch intents. */
public class AIQueryPlanTemplateResolver {

    private static final Pattern SIMPLE_SWITCH_NAME = Pattern.compile(
            "(?i)(?:den|switch|cong tac)\\s*([a-z0-9_-]+)");

    private static final Pattern SWITCH_NAME = Pattern.compile(
            "(?i)(?:đèn|den|switch|công tắc|cong tac)\\s*([a-z0-9_-]+(?:\\s+[a-z0-9_-]+)*?)(?=\\s+(?:co|la|su dung|dung)|$)");

    public AIQueryPlan resolve(String question, String currentUserId) {
        String text = normalize(question);
        if (isCurrentUserQuestion(text)) {
            AIQueryPlan plan = plan(Arrays.asList("USER"),
                    Arrays.asList("USER.user_name", "USER.full_name", "USER.role"), 1);
            addFilter(plan, "USER", "user_id", "=", currentUserId);
            return plan;
        }
        if (isPermissionCountQuestion(text)) {
            return permissionPlan(currentUserId, true, null);
        }
        if (isPermissionListQuestion(text)) {
            return permissionPlan(currentUserId, false, null);
        }
        if (isSpecificSwitchOnlineQuestion(text)) {
            String switchName = switchNameInQuestion(text);
            AIQueryPlan plan = plan(Arrays.asList("SWITCH", "DEVICE"),
                    Arrays.asList("SWITCH.switch_name", "DEVICE.name AS device_name", "DEVICE.status"), 1);
            AIJoin join = new AIJoin();
            join.setTable("DEVICE");
            join.setOn("DEVICE.device_id=SWITCH.device_id");
            plan.getJoins().add(join);
            addFilter(plan, "SWITCH", "switch_name", "LIKE", "%" + switchName + "%");
            addFilter(plan, "SWITCH", "is_active", "=", "1");
            addFilter(plan, "DEVICE", "is_active", "=", "1");
            return plan;
        }
        if (isOnlineSwitchListQuestion(text)) {
            AIQueryPlan plan = plan(Arrays.asList("DEVICE", "SWITCH"),
                    Arrays.asList("SWITCH.switch_name", "DEVICE.name AS device_name", "DEVICE.status"), 100);
            AIJoin join = new AIJoin();
            join.setTable("SWITCH");
            join.setOn("DEVICE.device_id=SWITCH.device_id");
            plan.getJoins().add(join);
            addFilter(plan, "DEVICE", "status", "=", "ONLINE");
            addFilter(plan, "DEVICE", "is_active", "=", "1");
            addFilter(plan, "SWITCH", "is_active", "=", "1");
            return plan;
        }
        if (isOnlineDeviceListQuestion(text)) {
            AIQueryPlan plan = plan(Arrays.asList("DEVICE"),
                    Arrays.asList("DEVICE.device_id", "DEVICE.name", "DEVICE.host_name", "DEVICE.status"), 100);
            addFilter(plan, "DEVICE", "status", "=", "ONLINE");
            addFilter(plan, "DEVICE", "is_active", "=", "1");
            return plan;
        }
        if (isOnlineSwitchCountQuestion(text)) {
            AIQueryPlan plan = plan(Arrays.asList("DEVICE", "SWITCH"),
                    Arrays.asList("COUNT(SWITCH.switch_id) AS total"), 1);
            AIJoin join = new AIJoin();
            join.setTable("SWITCH");
            join.setOn("DEVICE.device_id=SWITCH.device_id");
            plan.getJoins().add(join);
            addFilter(plan, "DEVICE", "status", "=", "ONLINE");
            addFilter(plan, "DEVICE", "is_active", "=", "1");
            addFilter(plan, "SWITCH", "is_active", "=", "1");
            return plan;
        }
        if (isSwitchCountQuestion(text)) {
            return plan(Arrays.asList("SWITCH"), Arrays.asList("COUNT(SWITCH.switch_id) AS total"), 1);
        }
        if (isDeviceCountQuestion(text)) {
            return plan(Arrays.asList("DEVICE"), Arrays.asList("COUNT(DEVICE.device_id) AS total"), 1);
        }
        if (isScheduleCountQuestion(text)) {
            Matcher switchName = SIMPLE_SWITCH_NAME.matcher(text);
            if (switchName.find()) {
                AIQueryPlan plan = plan(Arrays.asList("SCHEDULE", "SWITCH"),
                        Arrays.asList("COUNT(SCHEDULE.schedul_id) AS total"), 1);
                AIJoin join = new AIJoin();
                join.setTable("SWITCH");
                join.setOn("SWITCH.switch_id=SCHEDULE.switch_id");
                plan.getJoins().add(join);
                addFilter(plan, "SCHEDULE", "is_active", "=", "1");
                addFilter(plan, "SWITCH", "switch_name", "LIKE", "%" + switchName.group(1) + "%");
                return plan;
            }
        }
        if (isPermissionQuestion(text)) {
            return permissionPlan(currentUserId, false, switchNameInQuestion(text));
        }
        if (text.contains("gpio") || text.contains("chan gpio")) {
            Matcher matcher = SWITCH_NAME.matcher(text);
            if (matcher.find()) {
                AIQueryPlan plan = plan(Arrays.asList("SWITCH"),
                        Arrays.asList("SWITCH.switch_name", "SWITCH.gpio_pin"), 1);
                addFilter(plan, "SWITCH", "switch_name", "LIKE", "%" + matcher.group(1).trim() + "%");
                return plan;
            }
        }
        return null;
    }

    private AIQueryPlan permissionPlan(String currentUserId, boolean count, String switchName) {
        List<String> select = count
                ? Arrays.asList("COUNT(DISTINCT SWITCH.switch_id) AS total")
                : Arrays.asList("SWITCH.switch_name", "SWITCH.gpio_pin");
        AIQueryPlan plan = plan(Arrays.asList("PERMISSION", "SWITCH", "DEVICE"), select, count ? 1 : 100);
        AIJoin join = new AIJoin();
        join.setTable("SWITCH");
        join.setOn("SWITCH.switch_id=PERMISSION.switch_id");
        plan.getJoins().add(join);
        AIJoin deviceJoin = new AIJoin();
        deviceJoin.setTable("DEVICE");
        deviceJoin.setOn("DEVICE.device_id=SWITCH.device_id");
        plan.getJoins().add(deviceJoin);
        addFilter(plan, "PERMISSION", "user_id", "=", currentUserId);
        addFilter(plan, "PERMISSION", "is_active", "=", "1");
        addFilter(plan, "SWITCH", "is_active", "=", "1");
        addFilter(plan, "DEVICE", "is_active", "=", "1");
        if (switchName != null && !switchName.trim().isEmpty()) {
            addFilter(plan, "SWITCH", "switch_name", "LIKE", "%" + switchName.trim() + "%");
        }
        return plan;
    }

    public boolean requestsOtherUserData(String question) {
        String text = normalize(question);
        boolean userData = text.contains("nguoi dung") || text.contains("user")
                || text.contains("user_id") || text.contains("email")
                || text.contains("so dien thoai") || text.contains("tai khoan");
        boolean currentUser = text.contains("toi") || text.contains("cua toi") || text.contains("my ");
        return userData && !currentUser;
    }

    public boolean isMutationRequest(String question) {
        String text = normalize(question);
        boolean mutation = text.contains("them ") || text.startsWith("them")
                || text.contains("cap quyen") || text.contains("thu hoi quyen")
                || text.contains("thay doi") || text.contains("doi quyen")
                || text.contains("sua role") || text.startsWith("xoa")
                || text.contains("xoa ") || text.contains("vo hieu hoa")
                || text.contains("bat cong tac") || text.contains("tat cong tac");
        boolean managedData = text.contains("nguoi dung") || text.contains("user")
                || text.contains("role") || text.contains("quyen") || text.contains("switch")
                || text.contains("cong tac") || text.contains("lich") || text.contains("esp");
        return mutation && managedData;
    }

    public boolean isSpecificPermissionQuestion(String question) {
        String text = normalize(question);
        return isPermissionQuestion(text) && !isPermissionListQuestion(text)
                && switchNameInQuestion(text) != null;
    }

    public boolean isPermissionCountQuestion(String question) {
        String text = normalize(question);
        return (text.contains("bao nhieu") || text.contains("tong so") || text.contains("so luong"))
                && (text.contains("switch") || text.contains("cong tac") || text.contains("den"))
                && (text.contains("dieu khien") || text.contains("quyen"));
    }

    public boolean isPermissionListQuestion(String question) {
        String text = normalize(question);
        return isPermissionQuestion(text)
                && (text.contains("danh sach") || text.contains("liet ke")
                || text.contains("nhung switch")
                || text.contains("switch nao"));
    }

    private String switchNameInQuestion(String text) {
        Matcher matcher = SIMPLE_SWITCH_NAME.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String candidate = matcher.group(1).trim();
        String firstWord = candidate.split("\\s+", 2)[0];
        if (Arrays.asList("nao", "gi", "cac", "nhung", "toi", "minh", "cua", "ban",
                "nay", "do", "kia", "ay", "all", "any", "which", "what", "my", "me", "i", "you",
                "this", "that", "these", "those").contains(firstWord)) {
            return null;
        }
        return candidate;
    }

    private AIQueryPlan plan(List<String> tables, List<String> select, int limit) {
        AIQueryPlan plan = new AIQueryPlan();
        plan.setTables(new ArrayList<>(tables));
        plan.setSelect(new ArrayList<>(select));
        plan.setLimit(limit);
        return plan;
    }

    private void addFilter(AIQueryPlan plan, String table, String column,
            String operator, String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        AIFilter filter = new AIFilter();
        filter.setTable(table);
        filter.setColumn(column);
        filter.setOperator(operator);
        filter.setValue(value);
        plan.getFilters().add(filter);
    }

    private boolean isCurrentUserQuestion(String text) {
        return text.contains("toi la ai") || text.contains("vai tro")
                || text.contains("role cua toi") || text.equals("role")
                || text.contains("vai tro cua toi");
    }

    private boolean isPermissionQuestion(String text) {
        return (text.contains("quyen") || text.contains("duoc phep")
                || text.contains("co the dieu khien")
                || text.contains("co dieu khien")
                || text.contains("duoc dieu khien"))
                && (text.contains("switch") || text.contains("cong tac")
                || text.contains("dieu khien") || text.contains("den"));
    }

    private boolean isScheduleCountQuestion(String text) {
        return (text.contains("bao nhieu") || text.contains("tong so") || text.contains("so luong"))
                && (text.contains("lich") || text.contains("schedule"))
                && (text.contains("den") || text.contains("switch") || text.contains("cong tac"));
    }

    private boolean isSwitchCountQuestion(String text) {
        return (text.contains("bao nhieu") || text.contains("tong so") || text.contains("so luong"))
                && (text.contains("switch") || text.contains("cong tac") || text.contains("den"))
                && !text.contains("dieu khien") && !text.contains("quyen");
    }

    private boolean isOnlineSwitchCountQuestion(String text) {
        boolean count = isCountQuestion(text);
        boolean switchQuestion = text.contains("switch") || text.contains("cong tac") || text.contains("den");
        boolean online = containsOnlineTerm(text);
        return count && switchQuestion && online;
    }

    private boolean isSpecificSwitchOnlineQuestion(String text) {
        return containsOnlineTerm(text) && switchNameInQuestion(text) != null
                && !isCountQuestion(text) && !isOnlineSwitchListQuestion(text);
    }

    private boolean isOnlineSwitchListQuestion(String text) {
        boolean switchQuestion = text.contains("switch") || text.contains("cong tac") || text.contains("den");
        boolean listRequest = text.contains("liet ke") || text.contains("danh sach")
                || text.contains("cac ") || text.contains("nhung ") || text.contains(" nao ");
        return containsOnlineTerm(text) && switchQuestion && listRequest && !isCountQuestion(text);
    }

    private boolean isOnlineDeviceListQuestion(String text) {
        boolean deviceQuestion = text.contains("esp") || text.contains("thiet bi") || text.contains("device");
        return containsOnlineTerm(text) && deviceQuestion && !isCountQuestion(text);
    }

    private boolean containsOnlineTerm(String text) {
        return text.contains("online") || text.contains("onine") || text.contains("on line")
                || text.contains("truc tuyen");
    }

    private boolean isCountQuestion(String text) {
        return text.contains("bao nhieu") || text.contains("tong so") || text.contains("so luong")
                || text.contains("how many") || text.contains("number of");
    }

    private boolean isDeviceCountQuestion(String text) {
        return (text.contains("bao nhieu") || text.contains("tong so") || text.contains("so luong"))
                && (text.contains("esp") || text.contains("thiet bi") || text.contains("device"));
    }

    private String normalize(String value) {
        String text = value == null ? "" : value;
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
