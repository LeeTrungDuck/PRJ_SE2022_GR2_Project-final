/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package utills;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 *
 * @author ADMIN
 */
public class DBConnection {

    private static final String SERVER = "localhost";
    private static final String PORT = "1433";
    private static final String DB_NAME = "SmartSwitchSystem";
    private static final String USER_NAME = "sa";
    private static final String PASSWORD = "12345";

    public static Connection getConnection() throws ClassNotFoundException, SQLException {
        // 1. Nap driver. Dong nay hong la do thieu sqljdbc4.jar
        Class.forName("com.microsoft.sqlserver.jdbc.SQLServerDriver");

        // 2. Dung chuoi ket noi
        String url = "jdbc:sqlserver://" + SERVER + ":" + PORT + ";databaseName=" + DB_NAME;

        // 3. Mo ket noi. Dong nay hong la do sai tai khoan hoac chua bat cong 1433
        return DriverManager.getConnection(url, USER_NAME, PASSWORD);
    }
}
