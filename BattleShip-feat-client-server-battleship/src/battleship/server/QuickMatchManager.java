package battleship.server;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;

public final class QuickMatchManager {
    private final BattleShipServer server;
    private final UserManager users;
    private final MatchManager matches;
    private final Deque<String> waitingPlayers = new ArrayDeque<>();

    public QuickMatchManager(BattleShipServer server, MatchManager matches) {
        this.server = server;
        this.users = server.getUsers();
        this.matches = matches;
    }

    public synchronized boolean join(String username) {
        User user = users.currentUser(username);
        if (user == null || server.getUsers().session(username) == null
                || !users.transitionStatus(user.getUsername(), User.Status.IDLE, User.Status.QUEUED)) {
            throw new IllegalStateException("Bạn không ở trạng thái rỗi để tìm trận.");
        }

        waitingPlayers.addLast(user.getUsername());
        while (waitingPlayers.size() >= 2) {
            String player1 = pollAvailablePlayer();
            if (player1 == null) break;
            String player2 = pollAvailablePlayer();
            if (player2 == null) {
                waitingPlayers.addFirst(player1);
                break;
            }

            if (!users.transitionStatus(player1, User.Status.QUEUED, User.Status.PLAYING)) continue;
            if (!users.transitionStatus(player2, User.Status.QUEUED, User.Status.PLAYING)) {
                users.transitionStatus(player1, User.Status.PLAYING, User.Status.QUEUED);
                waitingPlayers.addFirst(player1);
                continue;
            }

            Match match;
            try {
                match = matches.createMatch(player1, player2);
            } catch (RuntimeException e) {
                users.transitionStatus(player2, User.Status.PLAYING, User.Status.QUEUED);
                users.transitionStatus(player1, User.Status.PLAYING, User.Status.QUEUED);
                waitingPlayers.addFirst(player2);
                waitingPlayers.addFirst(player1);
                throw e;
            }
            sendStart(match);
            server.broadcastLobby();
            return false;
        }

        server.broadcastLobby();
        return true;
    }

    public synchronized boolean leave(String username) {
        boolean removed = false;
        for (Iterator<String> iterator = waitingPlayers.iterator(); iterator.hasNext();) {
            if (iterator.next().equalsIgnoreCase(username)) {
                iterator.remove();
                removed = true;
                break;
            }
        }
        if (removed) {
            users.transitionStatus(username, User.Status.QUEUED, User.Status.IDLE);
            server.broadcastLobby();
        }
        return removed;
    }

    private String pollAvailablePlayer() {
        while (!waitingPlayers.isEmpty()) {
            String username = waitingPlayers.removeFirst();
            User user = users.currentUser(username);
            if (user != null && server.getUsers().session(username) != null
                    && user.getStatus() == User.Status.QUEUED) {
                return user.getUsername();
            }
            users.transitionStatus(username, User.Status.QUEUED, User.Status.IDLE);
        }
        return null;
    }

    private void sendStart(Match match) {
        server.broadcast(match.getPlayer1(), "MATCH_START",
                Map.of("matchId", match.getId(), "opponent", match.getPlayer2(),
                        "status", match.getStatus().name()));
        server.broadcast(match.getPlayer2(), "MATCH_START",
                Map.of("matchId", match.getId(), "opponent", match.getPlayer1(),
                        "status", match.getStatus().name()));
    }
}
