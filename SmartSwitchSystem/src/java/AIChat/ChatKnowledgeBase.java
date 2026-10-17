package AIChat;

import dto.ChatSourceDTO;
import enums.Role;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.servlet.ServletContext;

/** Basic keyword RAG over curated guides under WEB-INF, filtered before generation. */
public class ChatKnowledgeBase {
    public final class Excerpt {
        private final String title, action, content;
        private Excerpt(String title, String action, String content) {
            this.title = title; this.action = action; this.content = content;
        }
        public String getContent() { return content; }
        public ChatSourceDTO source() { return new ChatSourceDTO(title, action); }
    }

    private final ServletContext context;
    public ChatKnowledgeBase(ServletContext context) { this.context = context; }

    public List<Excerpt> retrieve(String question, Role role) throws IOException {
        List<Excerpt> candidates = new ArrayList<>();
        load(candidates, "common.md");
        if (role == Role.VIEWER) load(candidates, "viewer.md");
        if (role == Role.OPERATOR || role == Role.ADMIN) load(candidates, "operator.md");
        if (role == Role.ADMIN) load(candidates, "admin.md");
        final Set<String> terms = new HashSet<>(Arrays.asList(normalize(question).split("[^a-z0-9]+")));
        terms.removeIf(word -> word.length() < 3 || Arrays.asList("the", "how", "can", "and", "toi", "cho", "cua", "voi", "nhung", "duoc").contains(word));
        candidates.sort(Comparator.comparingInt((Excerpt item) -> score(item, terms)).reversed());
        List<Excerpt> chosen = new ArrayList<>();
        int best = candidates.isEmpty() ? 0 : score(candidates.get(0), terms);
        int minimum = best > 1 ? Math.max(2, (best + 1) / 2) : 1;
        int limit = best > 1 ? 3 : 1;
        for (Excerpt item : candidates) if (score(item, terms) >= minimum && chosen.size() < limit) chosen.add(item);
        return chosen;
    }

    private int score(Excerpt item, Set<String> terms) {
        Set<String> words = new HashSet<>(Arrays.asList(normalize(item.title + " " + item.content).split("[^a-z0-9]+")));
        int matches = 0;
        for (String term : terms) if (words.contains(term)) matches++;
        return matches;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private void load(List<Excerpt> excerpts, String name) throws IOException {
        try (InputStream input = context.getResourceAsStream("/WEB-INF/chat/" + name)) {
            if (input == null) throw new IOException("Bundled chat guide is missing.");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line, title = null, action = null;
                StringBuilder text = new StringBuilder();
                int size = 0;
                while ((line = reader.readLine()) != null) {
                    size += line.length();
                    if (size > 16000) throw new IOException("Bundled chat guide exceeds its limit.");
                    if (line.startsWith("## ")) {
                        if (title != null) excerpts.add(new Excerpt(title, action, text.toString().trim()));
                        String[] heading = line.substring(3).split("\\|", 2);
                        if (heading.length != 2) throw new IOException("Invalid chat guide heading.");
                        title = heading[0].trim(); action = heading[1].trim(); text.setLength(0);
                    } else text.append(line).append('\n');
                }
                if (title != null) excerpts.add(new Excerpt(title, action, text.toString().trim()));
            }
        }
    }
}
