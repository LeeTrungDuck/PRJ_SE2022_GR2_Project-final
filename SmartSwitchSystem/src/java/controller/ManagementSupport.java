package controller;

import java.text.Normalizer;
import java.util.*;
import javax.servlet.http.HttpServletRequest;

/** Stateless instance helpers; no demo data or shared mutable static variables. */
final class ManagementSupport {
    Map<String,Object> row(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return result;
    }
    Map<String,Object> copyMap(Map<String,Object> value) { return new LinkedHashMap<>(value); }
    @SuppressWarnings("unchecked") List<Object> list(Object value) { return (List<Object>) value; }
    String str(Object value) { return value == null ? "" : String.valueOf(value); }
    boolean yes(Object value) { return Boolean.TRUE.equals(value); }
    String param(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value.trim();
    }
    void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }
    String input(HttpServletRequest request, String key, int maximum) {
        String value = param(request, key);
        require(value.length() <= maximum, "The " + key + " field exceeds the maximum length.");
        return value;
    }
    String required(HttpServletRequest request, String key, int maximum, String label) {
        String value = input(request, key, maximum);
        require(!value.isEmpty(), "Please enter " + label + ".");
        return value;
    }
    String choice(HttpServletRequest request, String key, String... allowed) {
        String value = param(request, key);
        require(Arrays.asList(allowed).contains(value), "Invalid value for " + key + ".");
        return value;
    }
    void password(HttpServletRequest request, String key, int minimum, boolean strong) {
        // Preserve the exact password; trimming would change the authentication secret.
        String value = request.getParameter(key);
        require(value != null && value.length() >= minimum && value.length() <= 128,
                "Password must contain between " + minimum + " and 128 characters.");
        if (strong) require(value.matches("(?s).*[A-Z].*") && value.matches("(?s).*\\d.*")
                && value.matches("(?s).*[^\\p{L}\\p{N}\\s].*"),
                "Password must include an uppercase letter, a number and a special character.");
    }
    String normalize(Object value) {
        return Normalizer.normalize(str(value), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim();
    }
    String initials(Object name) {
        String[] words = str(name).trim().split("\\s+");
        if (words[0].isEmpty()) return "?";
        return (words[0].substring(0, 1) + (words.length > 1 ? words[words.length - 1].substring(0, 1) : ""))
                .toUpperCase(Locale.ROOT);
    }
}
