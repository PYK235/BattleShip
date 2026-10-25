package battleship.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Db {
    private Db() {}
    public static Connection getConnection() throws SQLException {
        String url = System.getenv().getOrDefault("BATTLESHIP_DB_URL",
                "jdbc:mysql://localhost:3306/battleship?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh");
        String user = System.getenv().getOrDefault("BATTLESHIP_DB_USER", "root");
        String password = System.getenv().getOrDefault("BATTLESHIP_DB_PASSWORD", "phamdaiphat");
        return DriverManager.getConnection(url, user, password);
    }
}
