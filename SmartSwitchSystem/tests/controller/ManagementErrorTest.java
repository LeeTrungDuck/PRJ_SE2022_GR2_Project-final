package controller;

import dao.UserDAO;
import dto.UserDTO;
import java.sql.SQLException;
import java.util.Map;

/** MVC2 error rendering probes using fakes, with no production database access. */
public final class ManagementErrorTest {
    private static int checks;
    private static void check(boolean valid, String label) {
        if (!valid) throw new AssertionError(label);
        checks++;
    }
    private static void addRequest(ManagementControllerTest.Request request, String csrf) {
        request.reset("POST", "uiOp", "addUser", "formName", "add", "csrf", csrf,
                "fullName", "  Tên giữ lại  ", "userName", "new.errors", "password", "Password12!",
                "role", "VIEWER", "email", "errors@example.com", "phone", "0900000089");
    }
    private static void assertForm(ManagementControllerTest.Request request) {
        check(request.status == 200 && request.forwards == 1 && "/web/UserManager.jsp".equals(request.forward),
                "write failure forwards exactly once to the populated management JSP");
        check(request.attrs.get("ERROR") != null && request.attrs.get("uiError") != null,
                "both standard MVC2 and existing JSP error attributes are supplied");
        check("Tên giữ lại".equals(request.attrs.get("OLD_fullName")), "retained values are trimmed");
        @SuppressWarnings("unchecked") Map<String,Object> edit = (Map<String,Object>) request.ui().get("edit");
        check("Tên giữ lại".equals(edit.get("fullName")) && "errors@example.com".equals(edit.get("email")),
                "dialog retains safe input values after failure");
        check(!request.attrs.toString().contains("Password12!"), "password is excluded from response models");
    }
    public static void main(String[] args) throws Exception {
        ManagementControllerTest.FakeUsers users = new ManagementControllerTest.FakeUsers();
        ManagementControllerTest.Request request = new ManagementControllerTest.Request();
        UserManagerController manager = new UserManagerController() {
            protected UserDAO users() { return users; }
            public void log(String text, Throwable error) { }
        };
        request.session.put("LOGIN_USER", users.values.get("U1"));
        manager.doGet(request.request, request.response);
        String csrf = (String) request.ui().get("csrf");

        users.writeFailure = new SQLException("internal UNIQUE detail", "23000", 2627);
        addRequest(request, csrf);
        manager.doPost(request.request, request.response);
        assertForm(request);
        check(request.attrs.get("uiError").toString().contains("already exists") && users.writes == 0,
                "UNIQUE failure becomes a friendly message without mutation");
        check(!request.attrs.toString().contains("internal UNIQUE detail"), "raw SQL error stays out of UI");

        users.writeFailure = new SQLException("internal FK detail", "23000", 547);
        addRequest(request, csrf);
        manager.doPost(request.request, request.response);
        assertForm(request);
        check(request.attrs.get("uiError").toString().contains("reference data"), "FK failure is explained safely");

        SQLException wrapped = new SQLException("internal wrapper");
        wrapped.setNextException(new SQLException("internal duplicate", "23000", 2601));
        users.writeFailure = wrapped;
        addRequest(request, csrf);
        manager.doPost(request.request, request.response);
        check(request.attrs.get("uiError").toString().contains("already exists"), "chained SQL errors are classified");

        users.writeFailure = new SQLException("internal connection detail", "08006");
        addRequest(request, csrf);
        manager.doPost(request.request, request.response);
        assertForm(request);
        check(request.attrs.get("uiError").toString().contains("Unable to save"), "generic write error is safe");

        users.writeFailure = null;
        addRequest(request, csrf);
        request.params.put("email", new String[]{"invalid-email"});
        manager.doPost(request.request, request.response);
        check(request.forwards == 1 && request.attrs.get("uiError").toString().contains("email"),
                "validation failure is caught by controller and rendered");

        users.readFailure = new SQLException("internal JDBC detail", "08006");
        request.reset("GET");
        manager.doGet(request.request, request.response);
        check(request.status == 503 && request.forwards == 1 && "/web/errorPage.jsp".equals(request.forward),
                "database read failure forwards once with HTTP 503");
        check(request.attrs.get("ERROR") != null && !request.attrs.toString().contains("internal JDBC detail"),
                "read error has a friendly UI message");
        users.readFailure = null;

        UserManagerController unavailable = new UserManagerController() {
            protected UserDAO users() throws ClassNotFoundException { throw new ClassNotFoundException("internal driver name"); }
            public void log(String text, Throwable error) { }
        };
        request.reset("GET");
        unavailable.doGet(request.request, request.response);
        check(request.status == 503 && request.forwards == 1 && request.attrs.get("ERROR") != null,
                "missing driver is caught and rendered");

        UserManagerController unexpected = new UserManagerController() {
            protected UserDAO users() { throw new IllegalStateException("internal runtime detail"); }
            public void log(String text, Throwable error) { }
        };
        request.reset("GET");
        unexpected.doGet(request.request, request.response);
        check(request.status == 500 && request.forwards == 1 && !request.attrs.toString().contains("internal runtime detail"),
                "unexpected runtime failure has one safe error response");

        request.session.put("LOGIN_USER", users.values.get("U2"));
        request.reset("GET");
        manager.doGet(request.request, request.response);
        check(request.status == 403 && request.forwards == 1 && request.attrs.get("ERROR") != null,
                "denied role gets a rendered message with HTTP 403");

        request.session.put("LOGIN_USER", users.values.get("U1"));
        request.reset("POST", "uiOp", "deleteUser", "id", "U2", "csrf", "invalid");
        manager.doPost(request.request, request.response);
        check(request.status == 403 && request.forwards == 1 && users.writes == 0,
                "CSRF failure forwards a message without mutation");

        request.reset("GET", "uiOp", "deleteUser", "id", "U2");
        manager.doGet(request.request, request.response);
        check(request.status == 405 && request.forwards == 1 && users.writes == 0,
                "GET mutation is rejected through the MVC2 error view");

        request.reset("POST", "uiOp", new String(new char[101]).replace('\0', 'x'));
        manager.doPost(request.request, request.response);
        check(request.status == 400 && request.forwards == 1 && request.attrs.get("ERROR") != null,
                "oversized operation is treated as invalid input rather than an internal error");

        ManagementControllerTest.SqlCapture sql = new ManagementControllerTest.SqlCapture();
        UserDAO dao = new UserDAO(sql.connection);
        check(!dao.findAll().get(0).getUserName().isEmpty(), "user list still returns profiles");
        check(!sql.statements.get(0).contains("password"), "user list query excludes passwords");

        addRequest(request, csrf);
        manager.doPost(request.request, request.response);
        check(request.status == 303 && request.forwards == 0 && request.location.contains("action=USER_MANAGER"),
                "successful write redirects to the read controller without forwarding twice");
        System.out.println("PASS: " + checks + " MVC2 error/input/forward checks. No real database writes.");
    }
}
