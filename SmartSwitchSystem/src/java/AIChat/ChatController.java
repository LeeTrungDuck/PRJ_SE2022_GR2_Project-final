package AIChat;

import dao.UserDAO;
import dto.ChatMessageDTO;
import dto.UserDTO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;

/** MVC2, read-only assistant. Session ownership is never taken from form fields. */
@WebServlet(name = "ChatController", urlPatterns = {"/ChatController"})
public class ChatController extends HttpServlet {
    private final String PAGE = "/web/Chat.jsp";
    private final String SUCCESS = "/MainController?action=CHAT";

    protected UserDAO users() throws SQLException, ClassNotFoundException { return new UserDAO(); }
    protected ChatAssistant assistant() {
        return new ChatAssistant(new ChatClient(), new ChatKnowledgeBase(getServletContext()), new ChatDatabaseContext());
    }

    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("text/html;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Content-Security-Policy", "default-src 'none'; style-src 'self'; img-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'self'");
        String error = null, question = "", flight = null;
        boolean redirect = false, configured = false;
        UserDTO actor = null;
        HttpSession session = request.getSession(false);
        try {
            UserDTO login = session == null ? null : (UserDTO)session.getAttribute("LOGIN_USER");
            if (login == null) throw new ChatException(401, "Please sign in to use the assistant.");
            try (UserDAO dao = users()) { actor = dao.findById(login.getUserId()); }
            if (actor == null || !actor.isActive() || actor.getRole() == null) {
                actor = null;
                session.invalidate();
                session = null;
                throw new ChatException(401, "Your session has expired. Please sign in again.");
            }
            String owner = actor.getUserId() + ":" + actor.getRole().name();
            ChatAssistant helper = assistant();
            configured = helper.isConfigured();
            String dataScope = helper.scope(actor.getUserId(), actor.getRole());
            synchronized (session) {
                UserDTO current = (UserDTO)session.getAttribute("LOGIN_USER");
                if (current == null || !login.getUserId().equals(current.getUserId()) || login.getRole() != current.getRole()) {
                    actor = null;
                    throw new ChatException(401, "Your account changed. Please reopen the assistant.");
                }
                initialize(session, owner, dataScope);
                session.setAttribute("LOGIN_USER", actor);
            }
            String op = request.getParameter("uiOp");
            op = op == null ? "" : op;
            if ("GET".equals(request.getMethod())) {
                if (!op.isEmpty() || request.getParameter("question") != null) throw new ChatException(405, "Send messages and clear conversations using the form below.");
                synchronized (session) {
                    Object feedback = session.getAttribute("CHAT_FEEDBACK");
                    if (session.getAttribute("CHAT_BUSY") == null && feedback instanceof Map) {
                        Map<?,?> notice = (Map<?,?>)feedback;
                        error = (String)notice.get("error");
                        question = (String)notice.get("question");
                        response.setStatus((Integer)notice.get("status"));
                        session.removeAttribute("CHAT_FEEDBACK");
                    }
                }
            } else {
                if (request.getContentLengthLong() > 16384) throw new ChatException(413, "Your message is too large. Please use 1,000 characters or fewer.");
                if (!"send".equals(op) && !"clear".equals(op)) throw new ChatException(400, "Invalid chat operation. Please reload the assistant.");
                String csrf = request.getParameter("csrf"), requestId = request.getParameter("requestId");
                synchronized (session) {
                    if (!session.getAttribute("CHAT_CSRF").equals(csrf)) throw new ChatException(403, "This form has expired. Please try again using the refreshed form.");
                    if ("send".equals(op)) {
                        question = request.getParameter("question");
                        question = question == null ? "" : question.trim();
                        if (requestId != null && requestId.equals(session.getAttribute("CHAT_LAST_REQUEST"))) {
                            // The browser may replace an in-flight iframe navigation with a second submit.
                            // Open the existing result instead of masking it with an expired-form warning.
                            redirect = true;
                            return;
                        }
                    }
                    if (!ids(session).contains(requestId)) throw new ChatException(409, "This request was already submitted or has expired. Please use the refreshed form.");
                    if (session.getAttribute("CHAT_BUSY") != null) throw new ChatException(429, "A reply is already being prepared. Please wait and refresh the conversation.");
                }
                if ("clear".equals(op)) {
                    synchronized (session) {
                        if (session.getAttribute("CHAT_BUSY") != null) throw new ChatException(429, "A reply is already being prepared. Please wait a moment.");
                        if (!ids(session).remove(requestId)) throw new ChatException(409, "This request was already submitted. Please use the refreshed form.");
                        session.setAttribute("CHAT_HISTORY", new ArrayList<ChatMessageDTO>());
                        session.removeAttribute("CHAT_FEEDBACK");
                    }
                    redirect = true;
                } else {
                    question = request.getParameter("question");
                    question = question == null ? "" : question.trim();
                    if (question.isEmpty() || question.length() > 1000) throw new ChatException(400, "Please enter a question between 1 and 1,000 characters.");
                    for (int i = 0; i < question.length(); i++) {
                        char c = question.charAt(i);
                        if (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t') throw new ChatException(400, "The message contains unsupported characters.");
                    }
                    if (!configured) throw new ChatException(503, "The assistant is not configured yet. Please contact your administrator.");
                    List<ChatMessageDTO> history;
                    synchronized (session) {
                        // Recheck after validation: parallel requests must not both reserve the same form.
                        if (requestId.equals(session.getAttribute("CHAT_LAST_REQUEST"))) {
                            redirect = true;
                            return;
                        }
                        if (!ids(session).contains(requestId)) throw new ChatException(409, "This request was already submitted. Please use the refreshed form.");
                        if (session.getAttribute("CHAT_BUSY") != null) throw new ChatException(429, "A reply is already being prepared. Please wait a moment.");
                        rateLimit(session);
                        ids(session).remove(requestId);
                        flight = requestId;
                        session.setAttribute("CHAT_BUSY", flight);
                        session.setAttribute("CHAT_LAST_REQUEST", requestId);
                        session.removeAttribute("CHAT_FEEDBACK");
                        history = new ArrayList<>(history(session));
                    }
                    ChatMessageDTO reply = helper.answer(actor.getUserId(), actor.getRole(), history, question, dataScope);
                    UserDTO latest;
                    try (UserDAO dao = users()) { latest = dao.findById(actor.getUserId()); }
                    if (latest == null || !latest.isActive() || latest.getRole() != actor.getRole()) {
                        actor = null;
                        throw new ChatException(401, "Your account access changed. Please reopen the assistant.");
                    }
                    String currentScope = helper.scope(actor.getUserId(), actor.getRole());
                    synchronized (session) {
                        UserDTO current = (UserDTO)session.getAttribute("LOGIN_USER");
                        if (!owner.equals(session.getAttribute("CHAT_OWNER")) || !dataScope.equals(currentScope)
                                || !dataScope.equals(session.getAttribute("CHAT_DATA_SCOPE")) || current == null
                                || !actor.getUserId().equals(current.getUserId()) || actor.getRole() != current.getRole()) {
                            actor = null;
                            throw new ChatException(401, "Your account changed. Please reopen the assistant.");
                        }
                        List<ChatMessageDTO> messages = history(session);
                        messages.add(new ChatMessageDTO("user", question));
                        messages.add(reply);
                        while (messages.size() > 12) messages.remove(0);
                    }
                    redirect = true;
                }
            }
        } catch (ChatException e) {
            response.setStatus(e.getStatus());
            error = e.getMessage();
        } catch (SQLException | ClassNotFoundException e) {
            log("Chat database lookup failed", e);
            actor = null;
            response.setStatus(503);
            error = "Unable to load permitted chat data. Please try again later.";
        } catch (IOException e) {
            log("Chat guide could not be loaded");
            response.setStatus(503);
            error = "The assistant's help guides are temporarily unavailable. Please try again later.";
        } catch (IllegalStateException e) {
            actor = null;
            session = null;
            response.setStatus(401);
            error = "Your session has expired. Please sign in again.";
        } catch (RuntimeException e) {
            log("Unexpected chat failure", e);
            response.setStatus(500);
            error = "Unable to complete your request. Please try again later.";
        } finally {
            if (session != null && flight != null) {
                try {
                    synchronized (session) {
                        if (flight.equals(session.getAttribute("CHAT_BUSY"))) {
                            if (error != null && actor != null) {
                                Map<String,Object> notice = new LinkedHashMap<>();
                                notice.put("error", error);
                                notice.put("question", question.length() <= 1000 ? question : "");
                                notice.put("status", response.getStatus());
                                session.setAttribute("CHAT_FEEDBACK", notice);
                            }
                            session.removeAttribute("CHAT_BUSY");
                        }
                    }
                }
                catch (IllegalStateException ignored) { /* Session was invalidated while waiting for the provider. */ }
            }
            if (redirect) {
                response.setStatus(303);
                response.setHeader("Location", response.encodeRedirectURL(request.getContextPath() + SUCCESS));
            } else {
                Map<String,Object> view = new LinkedHashMap<>();
                view.put("authenticated", false);
                view.put("configured", configured);
                view.put("error", error);
                view.put("question", question.length() <= 1000 ? question : "");
                if (actor != null && session != null) {
                    try {
                        synchronized (session) {
                            UserDTO current = (UserDTO)session.getAttribute("LOGIN_USER");
                            if (current == null || !actor.getUserId().equals(current.getUserId()) || actor.getRole() != current.getRole()
                                    || !(actor.getUserId() + ":" + actor.getRole().name()).equals(session.getAttribute("CHAT_OWNER"))) {
                                throw new IllegalStateException("Chat identity changed.");
                            }
                            boolean busy = session.getAttribute("CHAT_BUSY") != null;
                            String id = (String)session.getAttribute("CHAT_BUSY");
                            if (!busy) {
                                id = UUID.randomUUID().toString();
                                Set<String> pending = ids(session);
                                pending.add(id);
                                while (pending.size() > 8) pending.remove(pending.iterator().next());
                            }
                            view.put("authenticated", true);
                            view.put("busy", busy);
                            if (busy && "GET".equals(request.getMethod())) response.setStatus(202);
                            view.put("csrf", session.getAttribute("CHAT_CSRF"));
                            view.put("requestId", id);
                            view.put("messages", new ArrayList<>(history(session)));
                        }
                    } catch (IllegalStateException e) {
                        response.setStatus(401);
                        view.put("error", "Your session has expired. Please sign in again.");
                    }
                }
                request.setAttribute("chatView", view);
                request.getRequestDispatcher(PAGE).forward(request, response);
            }
        }
    }

    private void initialize(HttpSession session, String owner, String dataScope) {
        if (!owner.equals(session.getAttribute("CHAT_OWNER")) || !dataScope.equals(session.getAttribute("CHAT_DATA_SCOPE"))) {
            session.setAttribute("CHAT_OWNER", owner);
            session.setAttribute("CHAT_DATA_SCOPE", dataScope);
            session.setAttribute("CHAT_HISTORY", new ArrayList<ChatMessageDTO>());
            session.setAttribute("CHAT_CSRF", UUID.randomUUID().toString());
            session.setAttribute("CHAT_REQUEST_IDS", new LinkedHashSet<String>());
            session.removeAttribute("CHAT_BUSY");
            session.removeAttribute("CHAT_LAST_SENT");
            session.removeAttribute("CHAT_WINDOW");
            session.removeAttribute("CHAT_COUNT");
            session.removeAttribute("CHAT_LAST_REQUEST");
            session.removeAttribute("CHAT_FEEDBACK");
        }
    }

    @SuppressWarnings("unchecked") private List<ChatMessageDTO> history(HttpSession session) {
        return (List<ChatMessageDTO>)session.getAttribute("CHAT_HISTORY");
    }
    @SuppressWarnings("unchecked") private Set<String> ids(HttpSession session) {
        return (Set<String>)session.getAttribute("CHAT_REQUEST_IDS");
    }
    private void rateLimit(HttpSession session) throws ChatException {
        long now = System.currentTimeMillis();
        Long last = (Long)session.getAttribute("CHAT_LAST_SENT");
        if (last != null && now - last < 1500) throw new ChatException(429, "Please wait a moment before sending another question.");
        Long start = (Long)session.getAttribute("CHAT_WINDOW");
        int count = start == null || now - start >= 60000 ? 0 : (Integer)session.getAttribute("CHAT_COUNT");
        if (count >= 10) throw new ChatException(429, "You have sent several questions. Please wait a minute before trying again.");
        if (count == 0) session.setAttribute("CHAT_WINDOW", now);
        session.setAttribute("CHAT_COUNT", count + 1);
        session.setAttribute("CHAT_LAST_SENT", now);
    }
}
