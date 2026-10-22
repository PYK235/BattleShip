package battleship.server;

import java.sql.*;
import java.util.*;

public final class MySqlMatchDAO implements MatchDAO {
    public MySqlMatchDAO() {
        try (Connection c = Db.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS matches (" +
                    "match_id VARCHAR(36) PRIMARY KEY, player1 VARCHAR(20) NOT NULL, player2 VARCHAR(20) NOT NULL," +
                    "status ENUM('WAITING','PREPARATION','PLAYING','FINISHED','CANCELLED') NOT NULL DEFAULT 'WAITING'," +
                    "winner_username VARCHAR(20) NULL, end_reason VARCHAR(40) NULL," +
                    "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, started_at DATETIME NULL, ended_at DATETIME NULL," +
                    "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "CONSTRAINT fk_matches_player1 FOREIGN KEY (player1) REFERENCES users(username)," +
                    "CONSTRAINT fk_matches_player2 FOREIGN KEY (player2) REFERENCES users(username)," +
                    "CONSTRAINT fk_matches_winner FOREIGN KEY (winner_username) REFERENCES users(username)," +
                    "INDEX idx_matches_status_created (status,created_at), INDEX idx_matches_winner (winner_username)) ENGINE=InnoDB");
            ensureColumn(c, "winner_username", "VARCHAR(20) NULL");
            ensureColumn(c, "end_reason", "VARCHAR(40) NULL");
            ensureColumn(c, "started_at", "DATETIME NULL");
            ensureColumn(c, "ended_at", "DATETIME NULL");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS match_players (" +
                    "match_id VARCHAR(36) NOT NULL, username VARCHAR(20) NOT NULL, player_no TINYINT UNSIGNED NOT NULL," +
                    "result ENUM('WIN','LOSS','DRAW','ABANDONED') NULL, score_delta INT NOT NULL DEFAULT 0," +
                    "joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(match_id,username)," +
                    "UNIQUE KEY uq_match_player_no(match_id,player_no)," +
                    "CONSTRAINT fk_match_players_match FOREIGN KEY(match_id) REFERENCES matches(match_id) ON DELETE CASCADE," +
                    "CONSTRAINT fk_match_players_user FOREIGN KEY(username) REFERENCES users(username)," +
                    "CHECK(player_no IN (1,2))) ENGINE=InnoDB");
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể khởi tạo bảng trận đấu trong MySQL: " + e.getMessage(), e);
        }
    }

    private static void ensureColumn(Connection c, String column, String definition) throws SQLException {
        boolean exists = false;
        try (ResultSet r = c.getMetaData().getColumns(c.getCatalog(), null, "matches", column)) {
            exists = r.next();
        }
        if (!exists) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("ALTER TABLE matches ADD COLUMN " + column + " " + definition);
            }
        }
    }

    @Override public void save(Match m) {
        String sql = "INSERT INTO matches(match_id,player1,player2,status,started_at) VALUES(?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE status=VALUES(status), started_at=COALESCE(started_at,VALUES(started_at))";
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setString(1,m.getId()); p.setString(2,m.getPlayer1()); p.setString(3,m.getPlayer2());
                p.setString(4,m.getStatus().name());
                if (m.getStatus() == MatchStatus.PLAYING) p.setTimestamp(5,new Timestamp(System.currentTimeMillis()));
                else p.setNull(5,Types.TIMESTAMP);
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT IGNORE INTO match_players(match_id,username,player_no) VALUES(?,?,?)")) {
                p.setString(1,m.getId()); p.setString(2,m.getPlayer1()); p.setInt(3,1); p.addBatch();
                p.setString(1,m.getId()); p.setString(2,m.getPlayer2()); p.setInt(3,2); p.addBatch();
                p.executeBatch();
            }
            c.commit();
        } catch (SQLException e) { throw new IllegalStateException("Lỗi lưu trận đấu MySQL: " + e.getMessage(),e); }
    }

    @Override public void finishMatch(Match m, String winnerUsername, String reason) {
        try (Connection c = Db.getConnection()) {
            c.setAutoCommit(false);
            try {
                boolean alreadyFinished;
                try (PreparedStatement p = c.prepareStatement("SELECT status FROM matches WHERE match_id=? FOR UPDATE")) {
                    p.setString(1,m.getId());
                    try (ResultSet r=p.executeQuery()) {
                        if (!r.next()) throw new SQLException("Không tìm thấy trận đấu " + m.getId());
                        alreadyFinished = "FINISHED".equals(r.getString(1));
                    }
                }
                if (!alreadyFinished) {
                    try (PreparedStatement p=c.prepareStatement(
                            "UPDATE matches SET status='FINISHED',winner_username=?,end_reason=?,ended_at=NOW() WHERE match_id=?")) {
                        if (winnerUsername == null || winnerUsername.isBlank()) p.setNull(1,Types.VARCHAR);
                        else p.setString(1,winnerUsername);
                        p.setString(2,reason == null ? "UNKNOWN" : reason);
                        p.setString(3,m.getId());
                        p.executeUpdate();
                    }
                    try (PreparedStatement p=c.prepareStatement(
                            "UPDATE match_players SET result=CASE WHEN ? IS NULL OR ?='' THEN 'ABANDONED' " +
                            "WHEN username=? THEN 'WIN' ELSE 'LOSS' END," +
                            "score_delta=CASE WHEN ? IS NOT NULL AND ?<>'' AND username=? THEN 3 ELSE 0 END " +
                            "WHERE match_id=?")) {
                        p.setString(1,winnerUsername); p.setString(2,winnerUsername); p.setString(3,winnerUsername);
                        p.setString(4,winnerUsername); p.setString(5,winnerUsername); p.setString(6,winnerUsername);
                        p.setString(7,m.getId());
                        p.executeUpdate();
                    }
                    if (winnerUsername != null && !winnerUsername.isBlank()) {
                        String loser = m.opponentOf(winnerUsername);
                        try (PreparedStatement p=c.prepareStatement(
                                "UPDATE users SET total_score=total_score+3,wins=wins+1,matches_played=matches_played+1 WHERE username=?")) {
                            p.setString(1,winnerUsername); p.executeUpdate();
                        }
                        if (loser != null) try (PreparedStatement p=c.prepareStatement(
                                "UPDATE users SET losses=losses+1,matches_played=matches_played+1 WHERE username=?")) {
                            p.setString(1,loser); p.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement p=c.prepareStatement(
                                "UPDATE users SET matches_played=matches_played+1 WHERE username IN (?,?)")) {
                            p.setString(1,m.getPlayer1()); p.setString(2,m.getPlayer2()); p.executeUpdate();
                        }
                    }
                }
                c.commit();
            } catch (SQLException e) { c.rollback(); throw e; }
        } catch (SQLException e) { throw new IllegalStateException("Lỗi lưu kết quả trận/MySQL: "+e.getMessage(),e); }
    }

    @Override public Match find(String id) {
        try (Connection c=Db.getConnection(); PreparedStatement p=c.prepareStatement(
                "SELECT match_id,player1,player2,status FROM matches WHERE match_id=?")) {
            p.setString(1,id);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) return null;
                Match m=new Match(r.getString(1),r.getString(2),r.getString(3));
                try { m.setStatus(MatchStatus.valueOf(r.getString(4))); } catch(IllegalArgumentException ignored) {}
                return m;
            }
        } catch(SQLException e) { throw new IllegalStateException("Lỗi đọc trận đấu MySQL: "+e.getMessage(),e); }
    }

    @Override public Collection<Match> all() {
        List<Match> out=new ArrayList<>();
        try(Connection c=Db.getConnection(); Statement s=c.createStatement();
            ResultSet r=s.executeQuery("SELECT match_id,player1,player2,status FROM matches ORDER BY created_at DESC")) {
            while(r.next()) {
                Match m=new Match(r.getString(1),r.getString(2),r.getString(3));
                try { m.setStatus(MatchStatus.valueOf(r.getString(4))); } catch(IllegalArgumentException ignored) {}
                out.add(m);
            }
            return out;
        } catch(SQLException e) { throw new IllegalStateException("Lỗi đọc lịch sử trận đấu MySQL: "+e.getMessage(),e); }
    }

    @Override public List<Map<String, Object>> history(String username) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String sql = "SELECT m.match_id, " +
                "CASE WHEN m.player1=? THEN m.player2 ELSE m.player1 END AS opponent, " +
                "COALESCE(mp.result, CASE WHEN m.winner_username IS NULL THEN 'ABANDONED' " +
                "WHEN m.winner_username=? THEN 'WIN' ELSE 'LOSS' END) AS result, " +
                "COALESCE(m.winner_username,'') AS winner, COALESCE(m.end_reason,'') AS reason, " +
                "DATE_FORMAT(COALESCE(m.ended_at,m.created_at),'%Y-%m-%d %H:%i') AS played_at, " +
                "mp.score_delta FROM matches m LEFT JOIN match_players mp " +
                "ON mp.match_id=m.match_id AND mp.username=? " +
                "WHERE (m.player1=? OR m.player2=?) AND m.status='FINISHED' " +
                "ORDER BY COALESCE(m.ended_at,m.created_at) DESC";
        try (Connection c = Db.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, username);
            p.setString(2, username);
            p.setString(3, username);
            p.setString(4, username);
            p.setString(5, username);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("matchId", r.getString("match_id"));
                    row.put("opponent", r.getString("opponent"));
                    row.put("result", r.getString("result"));
                    row.put("winner", r.getString("winner"));
                    row.put("reason", r.getString("reason"));
                    row.put("playedAt", r.getString("played_at"));
                    row.put("scoreDelta", r.getInt("score_delta"));
                    rows.add(row);
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi đọc lịch sử đấu MySQL: " + e.getMessage(), e);
        }
    }
}
