package battleship.server;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class InMemoryMatchDAO implements MatchDAO {
    private final ConcurrentHashMap<String, Match> matches = new ConcurrentHashMap<>();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override public void save(Match match) { matches.put(match.getId(), match); }

    @Override public void finishMatch(Match match, String winnerUsername, String reason) {
        matches.put(match.getId(), match);
    }

    @Override public Match find(String matchId) { return matches.get(matchId); }

    @Override public Collection<Match> all() { return matches.values(); }

    @Override public List<Map<String, Object>> history(String username) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Match match : matches.values()) {
            if (match.getStatus() != MatchStatus.FINISHED || !match.contains(username)) continue;
            String winner = match.getWinnerUsername();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("matchId", match.getId());
            row.put("opponent", match.opponentOf(username));
            row.put("result", winner == null ? "ABANDONED" : winner.equalsIgnoreCase(username) ? "WIN" : "LOSS");
            row.put("winner", winner == null ? "" : winner);
            row.put("reason", match.getEndReason() == null ? "" : match.getEndReason());
            row.put("playedAt", DATE.format(Instant.ofEpochMilli(match.getEndedAt() > 0 ? match.getEndedAt() : match.getCreatedAt())));
            row.put("scoreDelta", winner != null && winner.equalsIgnoreCase(username) ? 3 : 0);
            rows.add(row);
        }
        rows.sort((a, b) -> String.valueOf(b.get("playedAt")).compareTo(String.valueOf(a.get("playedAt"))));
        return rows;
    }
}
