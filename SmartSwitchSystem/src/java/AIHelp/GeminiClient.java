package AIHelp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Small HTTP client responsible only for communicating with the Gemini API. */
public class GeminiClient {

    private static final String MODEL = "gemini-3.5-flash-lite";
    private static final String API_KEY = "AQ.Ab8RN6Jcx0Fl2aW6oZb-BdXFHsgpqLBowkOyg0kydQazaIiB3g";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 120000;

    public String complete(String prompt, boolean jsonMode) throws Exception {
        return complete(prompt, jsonMode ? "json" : null);
    }

    public String complete(String prompt, String responseFormat) throws Exception {
        String apiKey = getApiKey();
        String payload = buildPayload(prompt, responseFormat);
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return send(payload, apiKey);
            } catch (IllegalStateException e) {
                last = e;
                if (!isTemporaryFailure(e) || attempt == 1) {
                    throw e;
                }
                Thread.sleep(1000L * (attempt + 1));
            }
        }
        throw last;
    }

    private String getApiKey() {
        return API_KEY;
    }

    private String buildPayload(String prompt, String responseFormat) {
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject payload = new JsonObject();
        payload.add("contents", contents);

        if (responseFormat != null && !responseFormat.trim().isEmpty()) {
            JsonObject textFormat = new JsonObject();
            textFormat.addProperty("mimeType", "APPLICATION_JSON");
            if (!"json".equalsIgnoreCase(responseFormat.trim())) {
                JsonElement schema = JsonParser.parseString(responseFormat);
                textFormat.add("schema", normalizeSchema(schema));
            }

            JsonObject responseFormatObject = new JsonObject();
            responseFormatObject.add("text", textFormat);
            JsonObject generationConfig = new JsonObject();
            generationConfig.add("responseFormat", responseFormatObject);
            payload.add("generationConfig", generationConfig);
        }
        return payload.toString();
    }

    /** Converts JSON Schema union types to the anyOf form supported by Gemini. */
    private JsonElement normalizeSchema(JsonElement schema) {
        if (schema.isJsonArray()) {
            JsonArray normalized = new JsonArray();
            for (JsonElement item : schema.getAsJsonArray()) {
                normalized.add(normalizeSchema(item));
            }
            return normalized;
        }
        if (!schema.isJsonObject()) {
            return schema.deepCopy();
        }

        JsonObject object = new JsonObject();
        for (java.util.Map.Entry<String, JsonElement> entry : schema.getAsJsonObject().entrySet()) {
            if ("type".equals(entry.getKey()) && entry.getValue().isJsonArray()) {
                JsonArray types = entry.getValue().getAsJsonArray();
                if (types.size() == 1) {
                    object.add("type", types.get(0).deepCopy());
                } else if (types.size() > 1) {
                    JsonArray alternatives = new JsonArray();
                    for (JsonElement type : types) {
                        JsonObject alternative = new JsonObject();
                        alternative.add("type", type.deepCopy());
                        alternatives.add(alternative);
                    }
                    object.add("anyOf", alternatives);
                } else {
                    object.add("type", types.deepCopy());
                }
            } else {
                object.add(entry.getKey(), normalizeSchema(entry.getValue()));
            }
        }
        return object;
    }

    private String send(String payload, String apiKey) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent";
        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("x-goog-api-key", apiKey);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(payload.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        InputStream responseStream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        if (responseStream == null) {
            connection.disconnect();
            throw new IllegalStateException("Gemini request failed with HTTP " + status + ": no response body");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseStream, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Gemini request failed with HTTP " + status + ": " + response);
            }
            return extractText(response.toString());
        } finally {
            connection.disconnect();
        }
    }

    private String extractText(String json) {
        JsonObject response = JsonParser.parseString(json).getAsJsonObject();
        if (!response.has("candidates") || response.getAsJsonArray("candidates").size() == 0) {
            throw new IllegalStateException("Gemini returned no candidates: " + json);
        }

        JsonObject candidate = response.getAsJsonArray("candidates").get(0).getAsJsonObject();
        if (!candidate.has("content") || !candidate.getAsJsonObject("content").has("parts")) {
            throw new IllegalStateException("Gemini returned no candidate content: " + json);
        }

        StringBuilder text = new StringBuilder();
        JsonArray parts = candidate.getAsJsonObject("content").getAsJsonArray("parts");
        for (JsonElement element : parts) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject part = element.getAsJsonObject();
            if (part.has("thought") && part.get("thought").getAsBoolean()) {
                continue;
            }
            if (part.has("text")) {
                text.append(part.get("text").getAsString());
            }
        }
        if (text.length() == 0) {
            throw new IllegalStateException("Gemini returned no response text: " + json);
        }
        return text.toString();
    }

    private boolean isTemporaryFailure(Exception exception) {
        String message = exception.getMessage();
        return message != null && (message.contains("HTTP 500") || message.contains("HTTP 502")
                || message.contains("HTTP 503"));
    }
}
