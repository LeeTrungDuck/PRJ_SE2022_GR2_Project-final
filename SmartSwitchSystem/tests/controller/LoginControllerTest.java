package controller;

import dao.UserDAO;
import dto.UserDTO;
import enums.Role;
import java.sql.SQLException;
import javax.servlet.http.HttpServletRequestWrapper;
import utills.PasswordUtil;
import utills.URLMap;

/** Login lifecycle probes using fake query results; no real database access. */
public final class LoginControllerTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class CredentialUsers extends UserDAO {
        UserDTO stored;
        SQLException failure;
        int lookups;
        String submittedName;
        boolean closed;

        CredentialUsers(UserDTO stored) {
            super(new ManagementControllerTest.SqlCapture().connection);
            this.stored = stored;
        }

        @Override public UserDTO findByUserName(String name) throws SQLException {
            lookups++;
            submittedName = name;
            if (failure != null) throw failure;
            return stored != null && stored.getUserName().equals(name) ? stored : null;
        }

        @Override public void close() { closed = true; }
    }

    private static final class Fixture {
        final ManagementControllerTest.Request request = new ManagementControllerTest.Request();
        final CredentialUsers users;
        int rotations, logs;
        final LoginController controller;

        Fixture(UserDTO stored) {
            users = new CredentialUsers(stored);
            controller = new LoginController() {
                protected UserDAO users() { return users; }
                public void log(String message, Throwable exception) { logs++; }
            };
        }

        void submit(String name, String password) throws Exception {
            request.reset("POST", "userName", name, "passWord", password);
            controller.doPost(new HttpServletRequestWrapper(request.request) {
                @Override public String changeSessionId() { rotations++; return "rotated"; }
            }, request.response);
        }

        void rejected(String message) {
            check(message.equals(request.attrs.get("ERROR")), "expected friendly login message");
            check(URLMap.getLOGIN_PAGE().equals(request.forward) && request.forwards == 1,
                    "failed login forwards once to login JSP");
            check(request.location == null && !request.session.containsKey("LOGIN_USER"),
                    "failed login does not redirect or retain authenticated identity");
            check(rotations == 0, "failed login does not rotate an authenticated session");
        }
    }

    private static UserDTO user(boolean active, String password) {
        return new UserDTO("U1", "account", password, "Tài khoản test", Role.VIEWER,
                active, "account@example.com", "0900000001");
    }

    public static void main(String[] args) throws Exception {
        String password = "CorrectPassword1!";
        String hashed = PasswordUtil.hash(password);

        CredentialUsers verified = new CredentialUsers(user(false, hashed));
        check(verified.checkLogin("account", password) != null,
                "DAO returns verified inactive identity for controller status check");
        check(verified.checkLogin("account", "wrong") == null,
                "inactive identity is not returned with an incorrect password");

        Fixture inactive = new Fixture(user(false, hashed));
        inactive.request.session.put("LOGIN_USER", user(true, password));
        inactive.submit("account", password);
        inactive.rejected("Your account has been deactivated.");
        check(inactive.users.closed, "inactive login closes DAO");
        check(!inactive.request.attrs.toString().contains(password), "password excluded from error attributes");

        Fixture wrongInactive = new Fixture(user(false, hashed));
        wrongInactive.submit("account", "wrong");
        wrongInactive.rejected("Incorrect username or password.");

        Fixture missing = new Fixture(null);
        missing.submit("unknown", password);
        missing.rejected("Incorrect username or password.");
        check(missing.users.closed, "unknown account closes DAO without a null dereference");

        Fixture wrongActive = new Fixture(user(true, password));
        wrongActive.submit("account", "wrong");
        wrongActive.rejected("Incorrect username or password.");

        Fixture active = new Fixture(user(true, hashed));
        active.request.session.put("PERMISSION_DRAFTS", "old-draft");
        active.request.session.put("MANAGEMENT_CSRF", "old-token");
        active.request.session.put("MANAGEMENT_FLASH", "old-flash");
        active.submit("  account  ", password);
        check(active.request.status == 302 && active.request.forwards == 0
                && "/SmartSwitchSystem/MainController?action=DEVICE_CONTROL".equals(active.request.location),
                "active login redirects to device control without forwarding twice");
        check(active.rotations == 1 && active.request.session.get("LOGIN_USER") == active.users.stored,
                "only active verified account receives a rotated authenticated session");
        check(!active.request.session.containsKey("PERMISSION_DRAFTS")
                && !active.request.session.containsKey("MANAGEMENT_CSRF")
                && !active.request.session.containsKey("MANAGEMENT_FLASH"), "login clears old management state");
        check("account".equals(active.users.submittedName) && active.users.closed,
                "username is trimmed and DAO closed");

        Fixture legacy = new Fixture(user(true, password));
        legacy.submit("account", password);
        check(legacy.request.status == 302 && legacy.request.session.containsKey("LOGIN_USER"),
                "existing plaintext seed credentials remain compatible");

        Fixture empty = new Fixture(user(true, password));
        empty.submit("", "");
        empty.rejected("Incorrect username or password.");
        check(empty.users.lookups == 0, "empty credentials rejected before querying database");

        Fixture database = new Fixture(user(true, password));
        database.users.failure = new SQLException("INTERNAL_SQL_DETAIL", "08006");
        database.submit("account", password);
        check(database.request.status == 503 && database.request.forwards == 1
                && URLMap.getERROR_PAGE().equals(database.request.forward), "database failure renders one error page");
        check(database.logs == 1 && database.users.closed && database.request.attrs.get("ERROR") != null
                && !database.request.attrs.toString().contains("INTERNAL_SQL_DETAIL"),
                "database error is logged, closed and hidden from UI");

        System.out.println("PASS: " + checks + " inactive/active/null/password/login lifecycle checks. No real database writes.");
    }
}
