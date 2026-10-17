package AIChat;

import adminaudit.AdminFixture;
import dao.ChatDatabaseDAO;
import dto.ChatDatabaseSnapshotDTO;
import dto.ChatMessageDTO;
import enums.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/** Control permissions use isolated JDBC data, never a real SQL database or AI provider. */
public final class ChatControlAccessTest {
    private static int checks;

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }

    private static void fixtureWrite(String sql, Object... values) throws Exception {
        try (Connection connection = AdminFixture.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static void grant(int id, String actor, String switchId) throws Exception {
        fixtureWrite("INSERT INTO tblDevice_Permission(permission_id,user_id,switch_id,granted_by,is_active) VALUES(?,?,?,?,?)",
                id, actor, switchId, "U1", true);
    }

    private static ChatMessageDTO answer(ChatAssistant helper, String actor, Role role, String question) throws Exception {
        return helper.answer(actor, role, Collections.emptyList(), question, helper.scope(actor, role));
    }

    public static void main(String[] args) throws Exception {
        AdminFixture fixture = new AdminFixture();
        fixture.init();
        try {
            ChatDatabaseContext context = new ChatDatabaseContext() {
                @Override protected ChatDatabaseDAO database() {
                    return new ChatDatabaseDAO(AdminFixture.connection());
                }
            };
            String vietnamese = "vậy tôi có thể điều khiển SW nào ?";
            for (String question : new String[]{vietnamese, "Toi co the dieu khien SW nao?",
                    "Tôi có quyền điều khiển những công tắc nào?", "SW nào tôi được phép bật tắt?",
                    "Which switches can I control?", "List my controllable switches",
                    "What switches am I allowed to operate?", "Tôi được điều khiển bao nhiêu SW?",
                    "Những SW nào tôi đã được cấp quyền điều khiển?", "Which switches have I been granted permission to control?"}) {
                check(context.kinds(question).equals(EnumSet.of(ChatDataKind.CONTROLLABLE_SWITCHES)),
                        "personal control-list intent: " + question);
            }
            check(context.kinds("Các SW nào đang bật?").contains(ChatDataKind.SWITCHES),
                    "bare SW also selects ordinary saved switch data");
            for (String question : new String[]{"lịch sử điều khiển", "Switch nào đang bật?",
                    "How do I create a schedule?", "Bạn có thể điều khiển SW nào?", "Which switches can you control?",
                    "Admin cấp quyền điều khiển SW như thế nào?", "Tôi không được phép điều khiển SW nào?",
                    "Tôi có thể xem những SW nào đang bật?", "Which switches can I not control?", "How do I grant control permissions?"}) {
                check(!context.isControlAccessQuestion(question), "other intents do not become personal permissions: " + question);
            }

            int[] calls = {0};
            ChatClient client = new ChatClient("https://example.invalid/v1", "fixture-secret", "fixture-model", "max_tokens", 1000) {
                @Override public String complete(String prompt, List<ChatMessageDTO> history, String question) {
                    calls[0]++;
                    throw new AssertionError("Exact control-permission facts must not depend on generated text.");
                }
            };
            ChatAssistant helper = new ChatAssistant(client, new ChatKnowledgeBase(null), context);
            ChatMessageDTO viewer = answer(helper, "U2", Role.VIEWER, vietnamese);
            check(viewer.getText().contains("SW1") && viewer.getText().contains("GPIO 2") && viewer.getText().contains("ESP1")
                    && !viewer.getText().contains("SW2") && !viewer.getText().contains("SW3"),
                    "Viewer answer lists the granted switch and omits ungranted switches");
            check(viewer.getSources().size() == 1 && "DEVICE_CONTROL".equals(viewer.getSources().get(0).getAction()),
                    "permission facts link to Device Control, not unrelated history");

            try (ChatDatabaseDAO dao = new ChatDatabaseDAO(AdminFixture.connection())) {
                check(dao.read("U1", Role.ADMIN, EnumSet.of(ChatDataKind.SWITCHES)).getTotals().get(ChatDataKind.SWITCHES).get("total") == 3L,
                        "Admin's general viewing scope remains unchanged");
            }
            String admin = answer(helper, "U1", Role.ADMIN, "Which switches can I control?").getText();
            check(admin.contains("SW1") && !admin.contains("SW2") && !admin.contains("SW3"),
                    "Admin control answer does not confuse viewing all switches with personal control grants");
            String operator = answer(helper, "U3", Role.OPERATOR, "Which switches can I control?").getText();
            check(operator.contains("no active switch-control permissions") && !operator.contains("SW1"),
                    "Operator without a personal grant gets an honest empty result");
            String oldOperatorScope = helper.scope("U3", Role.OPERATOR);
            grant(80, "U3", "SW3");
            check(!oldOperatorScope.equals(helper.scope("U3", Role.OPERATOR)),
                    "an Operator control grant changes conversation scope despite unchanged viewing access");
            operator = answer(helper, "U3", Role.OPERATOR, "Which switches can I control?").getText();
            check(operator.contains("SW3") && !operator.contains("SW1"), "Operator receives only their own newly granted switch");

            grant(81, "U1", "SW4");
            grant(82, "U1", "SW5");
            ChatDatabaseSnapshotDTO active = context.read("U1", Role.ADMIN, vietnamese);
            check(active.getTotals().get(ChatDataKind.CONTROLLABLE_SWITCHES).get("total") == 1L,
                    "inactive switches and switches on inactive ESP32 devices cannot become controllable");
            String adminScope = helper.scope("U1", Role.ADMIN);
            fixtureWrite("UPDATE tblDevice_Permission SET is_active=? WHERE permission_id=?", false, 1);
            check(!adminScope.equals(helper.scope("U1", Role.ADMIN)),
                    "Admin revocation changes conversation scope even though all switches remain visible");
            try {
                helper.answer("U1", Role.ADMIN, Collections.emptyList(), vietnamese, adminScope);
                throw new AssertionError("Stale control scope accepted");
            } catch (ChatException expected) {
                check(expected.getStatus() == 403, "a stale control scope is rejected before an answer is stored");
            }
            check(answer(helper, "U1", Role.ADMIN, vietnamese).getText().contains("chưa được cấp quyền"),
                    "revoked Admin grant is not inferred from role or earlier conversation");
            fixtureWrite("UPDATE tblDevice_Permission SET is_active=? WHERE permission_id=?", true, 1);
            check(context.read("U2", Role.ADMIN, vietnamese).getRecords().get(ChatDataKind.CONTROLLABLE_SWITCHES).isEmpty(),
                    "forged or stale administrative roles do not bypass the live account-role guard");

            fixtureWrite("UPDATE tblSwitch SET switch_name=? WHERE switch_id=?", "private@example.invalid", "SW1");
            String masked = answer(helper, "U2", Role.VIEWER, vietnamese).getText();
            check(masked.contains("[label hidden]") && !masked.contains("private@example.invalid") && !masked.contains("U1") && !masked.contains("U2"),
                    "direct replies retain the context's label privacy filter and exclude account identities");
            fixtureWrite("UPDATE tblSwitch SET switch_name=? WHERE switch_id=?", "Safe switch", "SW1");
            for (int i = 0; i < 13; i++) {
                String id = "EXTRA" + i;
                fixtureWrite("INSERT INTO tblSwitch(switch_id,device_id,switch_name,gpio_pin,status,is_active) VALUES(?,?,?,?,?,?)",
                        id, "ESP1", "Safe load " + i, 24 + i, "OFF", true);
                grant(100 + i, "U1", id);
            }
            ChatDatabaseSnapshotDTO bounded = context.read("U1", Role.ADMIN, vietnamese);
            check(bounded.getRecords().get(ChatDataKind.CONTROLLABLE_SWITCHES).size() == 12
                    && bounded.getTotals().get(ChatDataKind.CONTROLLABLE_SWITCHES).get("total") == 14L,
                    "control list is bounded while retaining the exact total of permitted switches");
            check(context.controlAccessAnswer(bounded, vietnamese).getText().contains("12/14")
                    && context.controlAccessAnswer(bounded, vietnamese).getText().contains("chưa đầy đủ"),
                    "partial native replies explicitly state their list limit");
            fixtureWrite("UPDATE tblDevice_Permission SET is_active=? WHERE permission_id=?", false, 2);
            check(answer(helper, "U2", Role.VIEWER, vietnamese).getText().contains("chưa được cấp quyền"),
                    "Viewer revocation removes the switch from the next exact reply");
            check(calls[0] == 0, "permission-fact replies require no AI-provider request or hardware action");
            System.out.println("PASS: " + checks + " control-access intent, role, revocation, privacy and bounded-reply checks. Isolated JDBC only.");
        } finally {
            fixture.destroy();
        }
    }
}
