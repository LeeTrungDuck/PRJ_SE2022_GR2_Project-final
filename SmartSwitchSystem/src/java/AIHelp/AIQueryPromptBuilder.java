package AIHelp;

/** Builds prompts from external AI rules and authenticated-user context. */
public class AIQueryPromptBuilder {

    private final AIKnowledgeBase knowledgeBase;

    public AIQueryPromptBuilder() {
        knowledgeBase = new AIKnowledgeBase();
    }

    public String buildPlanPrompt(String question, String userId, String role) {
        return "You are the query planner for a Smart Switch system.\n"
                + "Return only one JSON object matching the supplied schema. Never return SQL.\n"
                + "The following rules are authoritative:\n"
                + knowledgeBase.getRules() + "\n"
                + "Authenticated user id: " + safe(userId) + "\n"
                + "Authenticated user role: " + safe(role) + "\n"
                + "Question: " + safe(question);
    }

    public String buildAnswerPrompt(String context, String question,
            String userId, String userName, String role) {
        return "You are a Smart Switch assistant. Answer using only the database context below. "
                + "Answer in the same language as the user's question. Use Vietnamese for Vietnamese "
                + "questions and English for English questions. Keep the answer concise and do not "
                + "translate or alter identifiers, statuses, or timestamps from the database. "
                + "An empty JSON array [] means there is no matching record and the user does not have "
                + "that permission; never infer or invent a switch, GPIO, role, or permission. "
                + "A role such as ADMIN does not by itself grant switch control: use only active permission "
                + "records for the authenticated user and active switches on active devices, and never infer "
                + "access from the total switch count. "
                + "If the answer is not present, say you do not know.\n"
                + "AUTHENTICATED USER CONTEXT: id=" + safe(userId)
                + ", user_name=" + safe(userName)
                + ", role=" + safe(role) + "\n"
                + "DATABASE CONTEXT:\n" + safe(context) + "\nQUESTION: " + safe(question);
    }

    public String getPlanSchema() {
        return knowledgeBase.getSchema();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
