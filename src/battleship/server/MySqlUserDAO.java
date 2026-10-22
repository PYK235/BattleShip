package battleship.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

public final class MySqlUserDAO implements UserDAO {
    public MySqlUserDAO() {
        try (Connection c = Db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "username VARCHAR(20) PRIMARY KEY, password_hash CHAR(64) NOT NULL," +
                    "total_score INT NOT NULL DEFAULT 0, wins INT NOT NULL DEFAULT 0," +
                    "losses INT NOT NULL DEFAULT 0, matches_played INT NOT NULL DEFAULT 0," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể khởi tạo bảng users trong MySQL: " + e.getMessage(), e);
        }
    }

    @Override public boolean create(String username, String password) {
        if (!valid(username, password)) return false;
        String sql = "INSERT INTO users(username,password_hash) VALUES(?,?)";
        try (Connection c = Db.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username.trim());
            p.setString(2, hash(password));
            p.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) { return false; }
          catch (SQLException e) { throw new IllegalStateException("Lỗi tạo tài khoản MySQL: " + e.getMessage(), e); }
    }

    @Override public boolean authenticate(String username, String password) {
        String sql = "SELECT password_hash FROM users WHERE LOWER(username)=LOWER(?)";
        try (Connection c = Db.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username == null ? "" : username.trim());
            try (ResultSet r = p.executeQuery()) {
                return r.next() && MessageDigest.isEqual(r.getString(1).getBytes(StandardCharsets.UTF_8),
                        hash(password == null ? "" : password).getBytes(StandardCharsets.UTF_8));
            }
        } catch (SQLException e) { throw new IllegalStateException("Lỗi xác thực MySQL: " + e.getMessage(), e); }
    }

    @Override public User find(String username) {
        String sql = "SELECT username,total_score,wins,losses,matches_played FROM users WHERE LOWER(username)=LOWER(?)";
        try (Connection c = Db.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username == null ? "" : username.trim());
            try (ResultSet r = p.executeQuery()) {
                return r.next() ? new User(r.getString("username"), r.getInt("total_score"),
                        r.getInt("wins"), r.getInt("losses"), r.getInt("matches_played")) : null;
            }
        } catch (SQLException e) { throw new IllegalStateException("Lỗi đọc tài khoản MySQL: " + e.getMessage(), e); }
    }

    @Override public Collection<User> all() {
        List<User> result = new ArrayList<>();
        String sql = "SELECT username,total_score,wins,losses,matches_played FROM users";
        try (Connection c = Db.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            while (r.next()) result.add(new User(r.getString("username"), r.getInt("total_score"),
                    r.getInt("wins"), r.getInt("losses"), r.getInt("matches_played")));
            return result;
        } catch (SQLException e) { throw new IllegalStateException("Lỗi đọc danh sách tài khoản MySQL: " + e.getMessage(), e); }
    }

    private static boolean valid(String u, String p) {
        return u != null && p != null && u.trim().matches("[A-Za-z0-9_]{3,20}") && p.length() >= 6 && p.length() <= 64;
    }
    private static String hash(String p) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
