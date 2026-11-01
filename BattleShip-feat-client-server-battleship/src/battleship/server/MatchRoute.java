package battleship.server;

import java.util.Map;

public final class MatchRoute {
    private MatchRoute() {}

    public static void register(BattleShipServer server, ChallengeManager challenges,
            MatchManager matches, QuickMatchManager quickMatches) {
        server.registerRoute("QUICK_MATCH", (client, id, p) -> {
            try {
                boolean queued = quickMatches.join(client.getUser().getUsername());
                client.send("QUICK_MATCH_RESULT", id, Map.of("ok", true, "queued", queued));
            } catch (RuntimeException e) {
                client.send("QUICK_MATCH_RESULT", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
        server.registerRoute("QUICK_MATCH_CANCEL", (client, id, p) -> {
            try {
                boolean cancelled = quickMatches.leave(client.getUser().getUsername());
                client.send("QUICK_MATCH_CANCEL_RESULT", id,
                        Map.of("ok", true, "cancelled", cancelled));
            } catch (RuntimeException e) {
                client.send("QUICK_MATCH_CANCEL_RESULT", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
        server.registerRoute("INVITE", (client, id, p) -> {
            try {
                Challenge c = challenges.create(client.getUser().getUsername(), str(p, "targetUsername"));
                client.send("INVITE_RESULT", id, Map.of("ok", true, "challengeId", c.getId()));
            } catch (RuntimeException e) {
                client.send("INVITE_RESULT", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
        server.registerRoute("ACCEPT", (client, id, p) -> {
            try {
                Match m = challenges.accept(str(p, "challengeId"), client.getUser().getUsername());
                client.send("ACCEPT_RESULT", id, Map.of("ok", true, "matchId", m.getId()));
            } catch (RuntimeException e) {
                client.send("ACCEPT_RESULT", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
        server.registerRoute("REJECT", (client, id, p) -> {
            try {
                challenges.reject(str(p, "challengeId"), client.getUser().getUsername());
                client.send("REJECT_RESULT", id, Map.of("ok", true));
            } catch (RuntimeException e) {
                client.send("REJECT_RESULT", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
        server.registerRoute("READY", (client, id, p) -> {
            try {
                matches.ready(str(p, "matchId"), client.getUser().getUsername());
                client.send("READY_ACK", id, Map.of("ok", true));
            } catch (RuntimeException e) {
                client.send("READY_ACK", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
        server.registerRoute("MATCH_EXIT", (client, id, p) -> {
            try {
                matches.playerLeft(client.getUser().getUsername(), "PLAYER_EXIT");
                client.send("MATCH_EXIT_RESULT", id, Map.of("ok", true));
                server.broadcastLobby();
            } catch (RuntimeException e) {
                client.send("MATCH_EXIT_RESULT", id, Map.of("ok", false, "message", e.getMessage()));
            }
        });
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
