package AIHelp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class AIChatBehaviorTest {

    private static int checks;

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
        checks++;
    }

    private static AIFilter filter(AIQueryPlan plan, String table, String column) {
        for (AIFilter filter : plan.getFilters()) {
            if (table.equalsIgnoreCase(filter.getTable())
                    && column.equalsIgnoreCase(filter.getColumn())) {
                return filter;
            }
        }
        return null;
    }

    private static void verifyProfileQuestion(AIQueryPlanTemplateResolver resolver,
            String question, String... expectedFields) {
        AIQueryPlan plan = resolver.resolve(question, "U-42");
        check(plan != null && plan.getTables().contains("USER"), "profile intent maps to USER");
        AIFilter userFilter = filter(plan, "USER", "user_id");
        check(userFilter != null && "=".equals(userFilter.getOperator())
                && "U-42".equals(userFilter.getValue()), "profile query is scoped to authenticated user");
        check(plan.getSelect().size() == expectedFields.length,
                "profile query selects only requested fields");
        for (String field : expectedFields) {
            check(plan.getSelect().contains(field), "requested profile field is selected");
        }
    }

    public static void main(String[] args) {
        AIQueryPlanTemplateResolver resolver = new AIQueryPlanTemplateResolver();
        ChatServlet servlet = new ChatServlet();
        verifyProfileQuestion(resolver, "User id của tôi là gì?", "USER.user_id");
        verifyProfileQuestion(resolver, "Emal hoặc sdt của tôi alf gì?",
                "USER.email", "USER.phone_number");
        verifyProfileQuestion(resolver, "Tên đăng nhập của tôi là gì?", "USER.user_name");

        AIQueryPlan scheduleCount = resolver.resolve("SW1 có bao nhiêu lịch hẹ", "U-42");
        AIFilter switchId = filter(scheduleCount, "SWITCH", "switch_id");
        check(switchId != null && "=".equals(switchId.getOperator())
                && "SW1".equals(switchId.getValue()), "switch ids are matched as ids for schedule counts");

        AIQueryPlan today = resolver.resolve("lịch hẹn giờ hôm nay", "U-42");
        check(today != null && today.getTables().contains("SCHEDULE"),
                "today schedule intent maps to schedules");
        check(servlet.builtInAnswer("lịch hẹn giờ hôm nay") == null,
                "today schedule lookup is not intercepted as a how-to question");
        check("1".equals(filter(today, "SCHEDULE", "is_active").getValue())
                && "1".equals(filter(today, "SCHEDULE", "is_enabled").getValue()),
                "schedule list excludes inactive and disabled records");
        check("SCHEDULE.run_time".equals(today.getOrderBy().getColumn()),
                "schedule list is ordered by run time");
        check(filter(today, "SCHEDULE", "user_id") == null,
                "general schedule query keeps role-based scope");

        AIQueryPlan mySchedules = resolver.resolve("lịch của tôi hôm nay", "U-42");
        check("U-42".equals(filter(mySchedules, "SCHEDULE", "user_id").getValue()),
                "current-user schedule query uses authenticated identity");
        check(resolver.requestsOtherUserData("show me the email of user U-99"),
                "other-user profile request is rejected");

        check(servlet.builtInAnswer("chào, đây là trang web gì vậy?").contains("Smart Switch"),
                "website question receives a built-in answer");
        check(servlet.builtInAnswer("chao, day la trang web gi vay").startsWith("Đây là"),
                "accent-free Vietnamese question receives Vietnamese answer");
        check(servlet.builtInAnswer("hướng dẫn tôi cách tạo tài khoản").contains("quản trị viên"),
                "account creation guidance is built in");
        check(servlet.builtInAnswer("hẹn giờ như thế nào?").contains("Add Schedule"),
                "schedule instructions are built in");

        String schema = new AIKnowledgeBase().getSchema();
        JsonObject payload = JsonParser.parseString(
                new GeminiClient().buildPayload("test", schema)).getAsJsonObject();
        JsonObject generationConfig = payload.getAsJsonObject("generationConfig");
        check("application/json".equals(generationConfig.get("responseMimeType").getAsString()),
                "Gemini JSON mode uses responseMimeType");
        check(generationConfig.has("responseSchema"), "Gemini schema uses responseSchema");
        check(!generationConfig.has("responseFormat"), "Gemini request omits invalid responseFormat field");
        check(generationConfig.getAsJsonObject("responseSchema")
                .getAsJsonObject("properties").getAsJsonObject("orderBy").has("anyOf"),
                "query schema unions are normalized for Gemini");
        String answerPrompt = new AIQueryPromptBuilder()
                .buildAnswerPrompt("[]", "Lịch hôm nay thế nào?", "U-42", "User", "VIEWER");
        check(answerPrompt.contains("same language as the user's question"),
                "generated answer follows the user's language");

        System.out.println("PASS: " + checks + " AI chat behavior checks.");
    }
}
