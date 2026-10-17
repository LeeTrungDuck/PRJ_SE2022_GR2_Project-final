package dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Data rendered as escaped text by the chat JSP. */
public class ChatMessageDTO implements Serializable {
    private final String role;
    private final String text;
    private final List<ChatSourceDTO> sources;

    public ChatMessageDTO(String role, String text) {
        this(role, text, Collections.<ChatSourceDTO>emptyList());
    }

    public ChatMessageDTO(String role, String text, List<ChatSourceDTO> sources) {
        this.role = role;
        this.text = text;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }

    public String getRole() { return role; }
    public String getText() { return text; }
    public List<ChatSourceDTO> getSources() { return sources; }
}
