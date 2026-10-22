package battleship.server;

public final class ServerMain {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5051;
        BattleShipServer server = new BattleShipServer(new MySqlUserDAO());
        MatchDAO matchDAO = new MySqlMatchDAO();
        MatchManager matches = new MatchManager(server, matchDAO);
        ChallengeManager challenges = new ChallengeManager(server);
        server.setMatches(matches);
        MatchRoute.register(server, challenges, matches);
        GameRoute.register(server, matches);
        server.start(port);
    }
}
