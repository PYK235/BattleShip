package src.battleship.server;

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
            m.setStatus(MatchStatus.FINISHED);
            dao.save(m);
            users.setStatus(username, User.Status.IDLE);
            if (opponent != null) {
                users.setStatus(opponent, User.Status.IDLE);
                server.broadcast(opponent, "OPPONENT_LEFT",
                        Map.of("matchId", m.getId(), "opponent", username, "reason", reason));
            }
            active.remove(m.getId());
        }
    }

    public synchronized void finish(String id, String reason) {
        Match m = find(id);
        if (m == null) return;
        m.setStatus(MatchStatus.FINISHED);
        dao.save(m);
        users.setStatus(m.getPlayer1(), User.Status.IDLE);
        users.setStatus(m.getPlayer2(), User.Status.IDLE);
        server.broadcast(m.getPlayer1(), "MATCH_END", Map.of("matchId", id, "reason", reason));
        server.broadcast(m.getPlayer2(), "MATCH_END", Map.of("matchId", id, "reason", reason));
        active.remove(id);
        server.broadcastLobby();
    }

    private Match requirePlayer(String id, String username) {
        Match m = find(id);
        if (m == null || !m.contains(username))
            throw new IllegalArgumentException("Trận đấu không tồn tại hoặc bạn không thuộc trận.");
        return m;
    }
}
