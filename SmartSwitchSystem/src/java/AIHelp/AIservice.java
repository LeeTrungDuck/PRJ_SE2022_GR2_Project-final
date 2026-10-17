package AIHelp;

/** Coordinates prompt creation, Gemini calls, and query-plan parsing. */
public class AIservice {

    private final GeminiClient gemini;
    private final AIQueryPromptBuilder prompts;
    private final AIQueryPlanParser planParser;
    private final AIQueryPlanTemplateResolver templates;

    public AIservice() {
        gemini = new GeminiClient();
        prompts = new AIQueryPromptBuilder();
        planParser = new AIQueryPlanParser();
        templates = new AIQueryPlanTemplateResolver();
    }

    public AIQueryPlan generateQueryPlan(String userQuery, String currentUserId,
            String currentRole) throws Exception {
        AIQueryPlan template = templates.resolve(userQuery, currentUserId);
        if (template != null) {
            return template;
        }
        String prompt = prompts.buildPlanPrompt(userQuery, currentUserId, currentRole);
        return planParser.parse(gemini.complete(prompt, prompts.getPlanSchema()));
    }

    public String callGeminiWithContext(String context, String userQuery,
            String currentUserId, String currentUserName, String currentUserRole) throws Exception {
        String prompt = prompts.buildAnswerPrompt(context, userQuery,
                currentUserId, currentUserName, currentUserRole);
        return gemini.complete(prompt, false);
    }
}
