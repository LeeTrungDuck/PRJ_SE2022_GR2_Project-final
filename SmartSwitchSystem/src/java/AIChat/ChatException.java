package AIChat;

/** Safe user-facing failure. Provider response bodies and credentials are excluded. */
public class ChatException extends Exception {
    private final int status;

    public ChatException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
