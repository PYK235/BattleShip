package battleship.server;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface MatchDAO {
    void save(Match match);
    void finishMatch(Match match, String winnerUsername, String reason);
    Match find(String matchId);
    Collection<Match> all();
    List<Map<String, Object>> history(String username);
}
