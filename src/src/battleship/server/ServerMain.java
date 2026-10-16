package src.battleship.server;

public final class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5050;
        BattleShipServer server = new BattleShipServer(new InMemoryUserDAO());
        MatchDAO matchDAO = new InMemoryMatchDAO();
        MatchManager matches = new MatchManager(server, matchDAO);
        ChallengeManager challenges = new ChallengeManager(server);
        server.setMatches(matches);
        MatchRoute.register(server, challenges, matches);
        server.start(port);
    }
}
