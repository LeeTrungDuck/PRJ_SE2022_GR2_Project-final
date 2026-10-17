package utills;

import audit.DeviceControlFixture;
import java.sql.Connection;

/** Test-only replacement. Compile into the isolated audit app, NEVER the application. */
public class DBConnection {
    public static Connection getConnection() {
        return DeviceControlFixture.connection();
    }
}
