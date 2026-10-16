package battleship.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.*;

public final class BattleShipServer {
    private final UserDAO dao; private final UserManager users; private final ExecutorService pool=Executors.newCachedThreadPool();
    private final ConcurrentMap<String,MessageRoute> routes=new ConcurrentHashMap<>();
    public BattleShipServer(UserDAO dao){this.dao=dao;this.users=new UserManager(dao);}
    public UserManager getUsers(){return users;} public UserDAO getDao(){return dao;}
    public void registerRoute(String messageType,MessageRoute route){routes.put(messageType,route);}
    boolean dispatch(String type,ClientHandler client,String requestId,Map<String,Object> payload){MessageRoute route=routes.get(type);if(route==null)return false;route.handle(client,requestId,payload);return true;}
    public void broadcast(String username,String type,Map<String,Object> payload){ClientHandler client=users.session(username);if(client!=null)client.send(type,null,payload);}
    public void broadcastLobby(){Map<String,Object> payload=Map.of("users",users.lobby());for(ClientHandler c:users.clients())c.send("LOBBY_UPDATE",null,payload);}
    public void start(int port)throws IOException{try(ServerSocket server=new ServerSocket(port)){System.out.println("Battle Ship server listening on port "+port);while(true)pool.execute(new ClientHandler(server.accept(),this));}}
}
