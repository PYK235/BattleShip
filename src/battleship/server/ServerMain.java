package battleship.server;

public final class ServerMain {
    public static void main(String[] args)throws Exception{int port=args.length>0?Integer.parseInt(args[0]):5050;new BattleShipServer(new InMemoryUserDAO()).start(port);}
}
