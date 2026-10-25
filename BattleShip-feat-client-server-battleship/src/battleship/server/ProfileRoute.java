package battleship.server;

import java.util.*;

/** Public ranking and player profile/history requests for authenticated clients. */
public final class ProfileRoute {
    private ProfileRoute() {}

    public static void register(BattleShipServer server, MatchDAO matches) {
        server.registerRoute("GET_RANKING", (client, requestId, payload) -> {
            List<User> users = new ArrayList<>(server.getDao().all());
            users.sort(Comparator.comparingInt(User::getTotalScore).reversed()
                    .thenComparing(Comparator.comparingInt(User::getWins).reversed())
                    .thenComparing(User::getUsername, String.CASE_INSENSITIVE_ORDER));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (User user : users) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("username", user.getUsername());
                row.put("totalScore", user.getTotalScore());
                row.put("wins", user.getWins());
                row.put("losses", user.getLosses());
                row.put("matches", user.getMatches());
                rows.add(row);
            }
            client.send("RANKING_RESULT", requestId, Map.of("users", rows));
        });

        server.registerRoute("GET_PROFILE", (client, requestId, payload) -> {
            String username = String.valueOf(payload.getOrDefault("username", "")).trim();
            User user = server.getDao().find(username);
            if (user == null) {
                client.send("PROFILE_RESULT", requestId, Map.of("ok", false, "message", "Không tìm thấy tài khoản này."));
                return;
            }
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("ok", true);
            profile.put("username", user.getUsername());
            profile.put("totalScore", user.getTotalScore());
            profile.put("wins", user.getWins());
            profile.put("losses", user.getLosses());
            profile.put("matches", user.getMatches());
            profile.put("history", matches.history(user.getUsername()));
            client.send("PROFILE_RESULT", requestId, profile);
        });
    }
}
