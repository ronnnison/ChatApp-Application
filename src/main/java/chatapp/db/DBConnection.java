package chatapp.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC connection factory. Defaults match XAMPP (localhost:3306, user root, empty password).
 * When the server is hosted somewhere else, override without recompiling, using either
 * environment variables or -D options:
 *   DB_HOST / -Ddb.host, DB_PORT / -Ddb.port, DB_NAME / -Ddb.name,
 *   DB_USER / -Ddb.user, DB_PASSWORD / -Ddb.password
 * A fresh connection per call keeps the code thread-safe (every client thread has its own).
 */
public final class DBConnection {
    private DBConnection() { }

    private static final String URL = "jdbc:mysql://" + cfg("db.host", "DB_HOST", "localhost")
            + ":" + cfg("db.port", "DB_PORT", "3306")
            + "/" + cfg("db.name", "DB_NAME", "chatapp_db")
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&useUnicode=true&characterEncoding=UTF-8";
    private static final String USER = cfg("db.user", "DB_USER", "root");
    private static final String PASSWORD = cfg("db.password", "DB_PASSWORD", "");

    private static String cfg(String property, String env, String fallback) {
        String v = System.getProperty(property);
        if (v == null || v.isEmpty()) v = System.getenv(env);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** @return null when the database is reachable, otherwise the error text. */
    public static String testConnection() {
        try (Connection c = getConnection()) {
            return null;
        } catch (SQLException e) {
            return e.getMessage();
        }
    }
}
