package AIChat;

import dto.ChatMessageDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** OpenAI-compatible Chat Completions, with server-only credentials and bounded IO. */
public class ChatClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String tokenField;
    private final int readTimeout;
    private final ChatJson json = new ChatJson();

    public ChatClient() {
        this(setting("smartSwitch.chat.baseUrl", "CHAT_API_BASE_URL", "https://api.openai.com/v1"),
                setting("smartSwitch.chat.apiKey", "CHAT_API_KEY", ""),
                setting("smartSwitch.chat.model", "CHAT_MODEL", ""),
                setting("smartSwitch.chat.tokenField", "CHAT_TOKEN_FIELD", "max_completion_tokens"), 15000);
    }

    public ChatClient(String baseUrl, String apiKey, String model, String tokenField, int readTimeout) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.tokenField = tokenField;
        this.readTimeout = Math.max(100, Math.min(readTimeout, 30000));
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value == null || value.trim().isEmpty()) value = System.getenv(environment);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public boolean isConfigured() {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host) || "::1".equals(host);
            return host != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || local && "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && !apiKey.isEmpty() && apiKey.length() <= 4096 && !hasControl(apiKey)
                    && !model.isEmpty() && model.length() <= 128 && !hasControl(model)
                    && ("max_completion_tokens".equals(tokenField) || "max_tokens".equals(tokenField));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean hasControl(String value) {
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return true;
        return false;
    }

    public String complete(String instructions, List<ChatMessageDTO> history, String question) throws ChatException {
        if (!isConfigured()) throw new ChatException(503, "The assistant is not configured yet. Please contact your administrator.");
        HttpURLConnection connection = null;
        try {
            StringBuilder body = new StringBuilder("{\"model\":").append(json.quote(model))
                    .append(",\"stream\":false,\"").append(tokenField).append("\":1024,\"messages\":[")
                    .append(message("system", instructions));
            for (ChatMessageDTO item : history) {
                if ("user".equals(item.getRole()) || "assistant".equals(item.getRole())) {
                    body.append(',').append(message(item.getRole(), item.getText()));
                }
            }
            body.append(',').append(message("user", question)).append("]}");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection = (HttpURLConnection) URI.create(baseUrl + "/chat/completions").toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(readTimeout);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
            int status = connection.getResponseCode();
            if (status == 429) throw new ChatException(429, "The AI service is busy. Please wait a moment and try again.");
            if (status == 401 || status == 403) throw new ChatException(503, "The AI service could not be authorized. Please contact your administrator.");
            if (status < 200 || status >= 300) throw new ChatException(502, "The AI service is unavailable. Please try again later.");
            String response;
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 131072) throw new ChatException(502, "The AI service returned an oversized response. Please try a shorter question.");
                    output.write(buffer, 0, count);
                }
                response = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
            return answer(response);
        } catch (SocketTimeoutException e) {
            throw new ChatException(504, "The AI service took too long to reply. Please try again.");
        } catch (IOException | IllegalArgumentException e) {
            throw new ChatException(502, "Unable to reach the AI service. Please try again later.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String message(String role, String content) {
        return "{\"role\":" + json.quote(role) + ",\"content\":" + json.quote(content) + "}";
    }

    private String answer(String response) throws ChatException {
        try {
            Object root = json.parse(response);
            Object choices = root instanceof Map ? ((Map<?,?>)root).get("choices") : null;
            Object first = choices instanceof List && !((List<?>)choices).isEmpty() ? ((List<?>)choices).get(0) : null;
            Object message = first instanceof Map ? ((Map<?,?>)first).get("message") : null;
            Object content = message instanceof Map ? ((Map<?,?>)message).get("content") : null;
            if (content instanceof String && !((String)content).trim().isEmpty() && ((String)content).length() <= 6000) {
                return ((String)content).trim().replace(apiKey, "[redacted]");
            }
        } catch (IllegalArgumentException e) {
            // Provider bodies are deliberately not included in exceptions or logs.
        }
        throw new ChatException(502, "The AI service returned an invalid reply. Please try again.");
    }
}
