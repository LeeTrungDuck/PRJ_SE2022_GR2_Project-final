package AIHelp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses and normalizes the structured query plan returned by Gemini. */
public class AIQueryPlanParser {

    private static final Pattern FILTER_TEXT = Pattern.compile(
            "(?i)^\\s*(USER|DEVICE|SWITCH|PERMISSION|SCHEDULE|HISTORY)\\.([A-Z_][A-Z0-9_]*)\\s*(=|<>|!=|>=|<=|>|<|LIKE)\\s*(.+?)\\s*$");

    public AIQueryPlan parse(String response) {
        String json = cleanJsonObject(response);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        normalizeSelect(root);
        normalizeFilters(root);
        AIQueryPlan plan = new Gson().fromJson(root, AIQueryPlan.class);
        validate(plan);
        return plan;
    }

    private void normalizeSelect(JsonObject root) {
        if (!root.has("select") || !root.get("select").isJsonArray()) {
            return;
        }
        JsonArray select = root.getAsJsonArray("select");
        for (int i = 0; i < select.size(); i++) {
            if (!select.get(i).isJsonObject()) {
                continue;
            }
            JsonObject item = select.get(i).getAsJsonObject();
            String column = value(item, "expression");
            if (column == null) {
                column = value(item, "column");
            }
            if (column == null) {
                throw new IllegalArgumentException("Gemini returned an invalid select item.");
            }
            String aggregate = value(item, "aggregate");
            if (aggregate != null && !column.toUpperCase(Locale.ROOT)
                    .startsWith(aggregate.toUpperCase(Locale.ROOT) + "(")) {
                column = aggregate.toUpperCase(Locale.ROOT) + "(" + column + ")";
            }
            String alias = value(item, "alias");
            if (alias != null && !column.toUpperCase(Locale.ROOT).contains(" AS ")) {
                column += " AS " + alias;
            }
            select.set(i, new com.google.gson.JsonPrimitive(column));
        }
    }

    private void normalizeFilters(JsonObject root) {
        if (!root.has("filters") || !root.get("filters").isJsonArray()) {
            return;
        }
        JsonArray filters = root.getAsJsonArray("filters");
        for (int i = 0; i < filters.size(); i++) {
            if (!filters.get(i).isJsonPrimitive()) {
                continue;
            }
            Matcher matcher = FILTER_TEXT.matcher(filters.get(i).getAsString());
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Gemini returned an invalid filter.");
            }
            JsonObject filter = new JsonObject();
            filter.addProperty("table", matcher.group(1).toUpperCase(Locale.ROOT));
            filter.addProperty("column", matcher.group(2).toLowerCase(Locale.ROOT));
            filter.addProperty("operator", matcher.group(3).toUpperCase(Locale.ROOT));
            filter.addProperty("value", stripQuotes(matcher.group(4)));
            filter.addProperty("secondValue", (String) null);
            filters.set(i, filter);
        }
    }

    private String cleanJsonObject(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini returned an empty query plan.");
        }
        String json = response.trim().replaceFirst("^```json\\s*", "")
                .replaceFirst("^```\\s*", "").replaceFirst("\\s*```$", "").trim();
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first < 0 || last <= first) {
            throw new IllegalArgumentException("Gemini did not return a JSON object.");
        }
        return json.substring(first, last + 1);
    }

    private void validate(AIQueryPlan plan) {
        if (plan == null || plan.getTables() == null || plan.getTables().isEmpty()
                || plan.getSelect() == null || plan.getSelect().isEmpty()) {
            throw new IllegalArgumentException("Gemini returned an incomplete query plan.");
        }
        if (plan.getLimit() != null && (plan.getLimit() <= 0 || plan.getLimit() > 100)) {
            throw new IllegalArgumentException("Query plan limit must be between 1 and 100.");
        }
    }

    private String value(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull()
                ? object.get(name).getAsString() : null;
    }

    private String stripQuotes(String value) {
        String result = value.trim();
        if (result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'")))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }
}
