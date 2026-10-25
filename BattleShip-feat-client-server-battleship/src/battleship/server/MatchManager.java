package battleship.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MatchManager {
    private final BattleShipServer server;
    private final UserManager users;
    private final MatchDAO dao;
    private final Map<String, Match> active = new ConcurrentHashMap<>();

    public MatchManager(BattleShipServer server, MatchDAO dao) {
        this.server = server;
        this.users = server.getUsers();
        this.dao = dao;
        ProfileRoute.register(server, dao);
    }

    public Match createMatch(String player1, String player2) {
        Match m = new Match(UUID.randomUUID().toString(), player1, player2);
        m.setStatus(MatchStatus.PREPARATION);
        active.put(m.getId(), m);
        dao.save(m);
        return m;
    }

    public Match find(String id) {
        Match m = active.get(id);
        return m != null ? m : dao.find(id);
    }

    public synchronized void ready(String id, String username) {
        Match m = requirePlayer(id, username);
        if (m.getStatus() != MatchStatus.PREPARATION)
            throw new IllegalStateException("Trận đấu không ở trạng thái chuẩn bị.");
        m.setReady(username);
        server.broadcast(username, "READY_RESULT", Map.of("matchId", id, "ready", true));
        String opponent = m.opponentOf(username);
        if (opponent != null)
            server.broadcast(opponent, "OPPONENT_READY", Map.of("matchId", id, "opponent", username));
        if (m.bothReady()) {
            m.setStatus(MatchStatus.PLAYING);
            server.broadcast(m.getPlayer1(), "MATCH_PLAYING", Map.of("matchId", id, "status", "PLAYING"));
            server.broadcast(m.getPlayer2(), "MATCH_PLAYING", Map.of("matchId", id, "status", "PLAYING"));
        }
        dao.save(m);
    }

    public synchronized void playerLeft(String username, String reason) {
        for (Match m : new ArrayList<>(active.values())) {
            if (!m.contains(username) || m.getStatus() == MatchStatus.FINISHED) continue;
            String opponent = m.opponentOf(username);
            m.finish(opponent, reason);
            dao.finishMatch(m, opponent, reason);
            users.refreshStats(username);
            if (opponent != null) users.refreshStats(opponent);
            users.setStatus(username, User.Status.IDLE);
            if (opponent != null) {
                users.setStatus(opponent, User.Status.IDLE);
                server.broadcast(opponent, "OPPONENT_LEFT",
                        Map.of("matchId", m.getId(), "opponent", username, "reason", reason));
                server.broadcast(opponent, "MATCH_END", Map.of("matchId", m.getId(),
                        "winner", opponent, "result", "WIN", "reason", reason));
            }
            active.remove(m.getId());
        }
    }

    public synchronized void finish(String id, String winnerUsername, String reason) {
        Match m = find(id);
        if (m == null || m.getStatus() == MatchStatus.FINISHED) return;
        m.finish(winnerUsername, reason);
        dao.finishMatch(m, winnerUsername, reason);
        users.refreshStats(m.getPlayer1());
        users.refreshStats(m.getPlayer2());
        users.setStatus(m.getPlayer1(), User.Status.IDLE);
        users.setStatus(m.getPlayer2(), User.Status.IDLE);
        server.broadcast(m.getPlayer1(), "MATCH_END", Map.of("matchId", id,
                "reason", reason, "winner", winnerUsername == null ? "" : winnerUsername,
                "result", winnerUsername == null ? "DRAW" :
                        (winnerUsername.equalsIgnoreCase(m.getPlayer1()) ? "WIN" : "LOSS")));
        server.broadcast(m.getPlayer2(), "MATCH_END", Map.of("matchId", id,
                "reason", reason, "winner", winnerUsername == null ? "" : winnerUsername,
                "result", winnerUsername == null ? "DRAW" :
                        (winnerUsername.equalsIgnoreCase(m.getPlayer2()) ? "WIN" : "LOSS")));
        active.remove(id);
        server.broadcastLobby();
    }

    public synchronized void finish(String id, String reason) {
        String winner = null;
        if (reason != null && reason.startsWith("WIN:")) winner = reason.substring(4);
        finish(id, winner, winner == null ? reason : "ALL_SHIPS_SUNK");
    }

    private Match requirePlayer(String id, String username) {
        Match m = find(id);
        if (m == null || !m.contains(username))
            throw new IllegalArgumentException("Trận đấu không tồn tại hoặc bạn không thuộc trận.");
        return m;
    }
}
