package utills;
import adminaudit.AdminFixture;
import java.sql.Connection;
/** Test-only replacement, compiled exclusively into the localhost audit app. */
public class DBConnection {
    public static Connection getConnection() { return AdminFixture.connection(); }
}
