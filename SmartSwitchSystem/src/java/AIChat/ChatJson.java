package AIChat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small bounded JSON codec for the JDK 8 HTTP client; never evaluates JavaScript. */
final class ChatJson {
    String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 32) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    Object parse(String input) {
        Reader reader = new Reader(input);
        Object result = reader.value(0);
        reader.space();
        if (reader.at != input.length()) throw new IllegalArgumentException("Invalid JSON.");
        return result;
    }

    private final class Reader {
        private final String input;
        private int at;
        Reader(String input) { this.input = input; }
        void space() { while (at < input.length() && " \r\n\t".indexOf(input.charAt(at)) >= 0) at++; }
        char next() {
            if (at >= input.length()) throw new IllegalArgumentException("Incomplete JSON.");
            return input.charAt(at++);
        }
        Object value(int depth) {
            if (depth > 32) throw new IllegalArgumentException("JSON is too deeply nested.");
            space();
            if (at >= input.length()) throw new IllegalArgumentException("Incomplete JSON.");
            char c = input.charAt(at);
            if (c == '"') return string();
            if (c == '{') {
                at++;
                Map<String,Object> map = new LinkedHashMap<>();
                space();
                if (at < input.length() && input.charAt(at) == '}') { at++; return map; }
                while (true) {
                    space();
                    if (at >= input.length() || input.charAt(at) != '"') throw new IllegalArgumentException("Invalid JSON key.");
                    String key = string();
                    space();
                    if (next() != ':') throw new IllegalArgumentException("Invalid JSON object.");
                    map.put(key, value(depth + 1));
                    space();
                    char end = next();
                    if (end == '}') return map;
                    if (end != ',') throw new IllegalArgumentException("Invalid JSON object.");
                }
            }
            if (c == '[') {
                at++;
                List<Object> list = new ArrayList<>();
                space();
                if (at < input.length() && input.charAt(at) == ']') { at++; return list; }
                while (true) {
                    list.add(value(depth + 1));
                    space();
                    char end = next();
                    if (end == ']') return list;
                    if (end != ',') throw new IllegalArgumentException("Invalid JSON array.");
                }
            }
            for (String literal : new String[]{"true", "false", "null"}) {
                if (input.startsWith(literal, at)) {
                    at += literal.length();
                    return "null".equals(literal) ? null : Boolean.valueOf(literal);
                }
            }
            int start = at;
            while (at < input.length() && "-+0123456789.eE".indexOf(input.charAt(at)) >= 0) at++;
            String number = input.substring(start, at);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("Invalid JSON value.");
            return new java.math.BigDecimal(number);
        }
        String string() {
            if (next() != '"') throw new IllegalArgumentException("Invalid JSON string.");
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c < 32) throw new IllegalArgumentException("Invalid JSON string.");
                if (c != '\\') { out.append(c); continue; }
                switch (next()) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (at + 4 > input.length()) throw new IllegalArgumentException("Invalid JSON escape.");
                        try { out.append((char)Integer.parseInt(input.substring(at, at + 4), 16)); }
                        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid JSON escape."); }
                        at += 4;
                        break;
                    default: throw new IllegalArgumentException("Invalid JSON escape.");
                }
            }
        }
    }
}
