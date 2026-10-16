package src.battleship.server;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMatchDAO implements MatchDAO {
    private final ConcurrentHashMap<String, Match> matches = new ConcurrentHashMap<>();

    public void save(Match match) { matches.put(match.getId(), match); }
    public Match find(String matchId) { return matches.get(matchId); }
    public Collection<Match> all() { return matches.values(); }
}
