package src.battleship.server;

import java.util.Collection;

public interface MatchDAO {
    void save(Match match);
    Match find(String matchId);
    Collection<Match> all();
}
