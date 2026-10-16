package src.battleship.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ChallengeManager {
    private final BattleShipServer server;
    private final UserManager users;
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Map<String, String> pendingByInvitee = new ConcurrentHashMap<>();

    public ChallengeManager(BattleShipServer server) {
        this.server = server;
        this.users = server.getUsers();
    }

    public synchronized Challenge create(String inviter, String invitee) {
        if (inviter == null || invitee == null || inviter.equalsIgnoreCase(invitee))
            throw new IllegalArgumentException("Không thể tự thách đấu chính mình.");
        User source = server.getDao().find(inviter);
        User target = server.getDao().find(invitee);
        if (source == null || source.getStatus() != User.Status.IDLE)
            throw new IllegalStateException("Bạn không ở trạng thái rỗi.");
        if (target == null || target.getStatus() != User.Status.IDLE)
            throw new IllegalStateException("Đối thủ không còn rảnh.");
        String key = target.getUsername().toLowerCase(Locale.ROOT);
        if (pendingByInvitee.containsKey(key))
            throw new IllegalStateException("Đối thủ đang có lời mời khác.");

        String id = UUID.randomUUID().toString();
        Challenge c = new Challenge(id, source.getUsername(), target.getUsername());
        challenges.put(id, c);
        pendingByInvitee.put(key, id);
        server.broadcast(target.getUsername(), "INVITE_RECEIVED",
                Map.of("challengeId", id, "inviter", source.getUsername()));
        return c;
    }

    public synchronized Match accept(String id, String invitee) {
        Challenge c = require(id, invitee);
        User a = server.getDao().find(c.getInviter());
        User b = server.getDao().find(c.getInvitee());
        if (a == null || b == null || a.getStatus() != User.Status.IDLE || b.getStatus() != User.Status.IDLE) {
            remove(c);
            throw new IllegalStateException("Người chơi không còn ở trạng thái rỗi.");
        }
        Match match = server.getMatches().createMatch(a.getUsername(), b.getUsername());
        users.setStatus(a.getUsername(), User.Status.PLAYING);
        users.setStatus(b.getUsername(), User.Status.PLAYING);
        remove(c);
        sendStart(match);
        return match;
    }

    public synchronized void reject(String id, String invitee) {
        Challenge c = require(id, invitee);
        remove(c);
        server.broadcast(c.getInviter(), "INVITE_REJECTED",
                Map.of("challengeId", id, "opponent", invitee));
    }

    private void sendStart(Match m) {
        server.broadcast(m.getPlayer1(), "MATCH_START",
                Map.of("matchId", m.getId(), "opponent", m.getPlayer2(), "status", m.getStatus().name()));
        server.broadcast(m.getPlayer2(), "MATCH_START",
                Map.of("matchId", m.getId(), "opponent", m.getPlayer1(), "status", m.getStatus().name()));
        server.broadcastLobby();
    }

    private Challenge require(String id, String invitee) {
        Challenge c = challenges.get(id);
        if (c == null || !c.getInvitee().equalsIgnoreCase(invitee))
            throw new IllegalArgumentException("Lời mời không hợp lệ hoặc đã hết hiệu lực.");
        return c;
    }

    private void remove(Challenge c) {
        challenges.remove(c.getId());
        pendingByInvitee.remove(c.getInvitee().toLowerCase(Locale.ROOT), c.getId());
    }
}
