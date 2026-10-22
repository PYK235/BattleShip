package battleship.server;

import battleship.part3.server.Board;
import battleship.part3.server.TurnManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bridges TV3 game rules into the TV1/TV2 JSON-lines server.
 * Client messages: PLACE_BOARD {matchId, setup}, READY {matchId}, SHOT {matchId,row,col}.
 * setup format: "row,col,H;row,col,V;row,col,H;row,col,V".
 */
public final class GameRoute {
    private static final class GameState {
        final String matchId;
        final String[] players = new String[2];
        final Board[] boards = new Board[2];
        TurnManager turns;
        GameState(Match m) { matchId=m.getId(); players[0]=m.getPlayer1(); players[1]=m.getPlayer2(); }
        int indexOf(String username) {
            if (players[0].equalsIgnoreCase(username)) return 0;
            if (players[1].equalsIgnoreCase(username)) return 1;
            return -1;
        }
    }
    private final BattleShipServer server;
    private final MatchManager matches;
    private final Map<String,GameState> games = new ConcurrentHashMap<>();
    private final ScheduledExecutorService timeoutWatcher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "battleship-turn-timeout");
        t.setDaemon(true);
        return t;
    });

    private GameRoute(BattleShipServer server, MatchManager matches) {
        this.server=server; this.matches=matches;
    }
    public static void register(BattleShipServer server, MatchManager matches) {
        GameRoute route=new GameRoute(server,matches);
        server.registerRoute("PLACE_BOARD",route::placeBoard);
        // Replace TV2's READY route so both players must submit a valid board first.
        server.registerRoute("READY",route::ready);
        server.registerRoute("SHOT",route::shot);
        route.timeoutWatcher.scheduleAtFixedRate(route::checkTurnTimeouts, 250, 250, TimeUnit.MILLISECONDS);
    }

    private void checkTurnTimeouts() {
        for (GameState g : games.values()) {
            synchronized (g) {
                if (g.turns == null || g.turns.expired()) {
                    if (g.turns == null) continue;
                    g.turns.miss();
                    Map<String,Object> turn = Map.of("matchId", g.matchId,
                            "currentPlayer", g.players[g.turns.current()],
                            "secondsLeft", g.turns.secondsLeft(), "reason", "TIMEOUT");
                    server.broadcast(g.players[0], "TURN_CHANGED", turn);
                    server.broadcast(g.players[1], "TURN_CHANGED", turn);
                }
            }
        }
    }

    private void placeBoard(ClientHandler client, String requestId, Map<String,Object> p) {
        try {
            String id=str(p,"matchId");
            Match m=requireMatch(id,client.getUser().getUsername());
            if(m.getStatus()!=MatchStatus.PREPARATION) throw new IllegalStateException("Trận đấu không ở giai đoạn chuẩn bị.");
            Board board=Board.parse(str(p,"setup"));
            GameState g=games.computeIfAbsent(id,k->new GameState(m));
            synchronized(g) {
                int i=g.indexOf(client.getUser().getUsername());
                if(i<0) throw new IllegalArgumentException("Bạn không thuộc trận đấu.");
                if(g.boards[i]!=null) throw new IllegalStateException("Bạn đã gửi bàn cờ.");
                g.boards[i]=board;
            }
            client.send("PLACE_BOARD_RESULT",requestId,Map.of("ok",true,"matchId",id,"board",board.encode(true)));
        } catch(RuntimeException e) {
            client.send("PLACE_BOARD_RESULT",requestId,Map.of("ok",false,"message",safe(e)));
        }
    }

    private void ready(ClientHandler client, String requestId, Map<String,Object> p) {
        String id=str(p,"matchId");
        try {
            Match m=requireMatch(id,client.getUser().getUsername());
            GameState g=games.get(id);
            if(g==null) throw new IllegalStateException("Hãy gửi bàn cờ bằng PLACE_BOARD trước khi sẵn sàng.");
            synchronized(g) {
                int i=g.indexOf(client.getUser().getUsername());
                if(i<0 || g.boards[i]==null) throw new IllegalStateException("Bạn chưa đặt đủ 4 tàu.");
                matches.ready(id,client.getUser().getUsername());
                if(m.bothReady() && g.turns==null) {
                    g.turns=new TurnManager(0);
                    server.broadcast(m.getPlayer1(),"GAME_STARTED",Map.of("matchId",id,"yourTurn",true,"secondsPerTurn",15,"board",g.boards[0].encode(true),"opponentBoard",g.boards[1].encode(false)));
                    server.broadcast(m.getPlayer2(),"GAME_STARTED",Map.of("matchId",id,"yourTurn",false,"secondsPerTurn",15,"board",g.boards[1].encode(true),"opponentBoard",g.boards[0].encode(false)));
                }
            }
            client.send("READY_ACK",requestId,Map.of("ok",true,"matchId",id));
        } catch(RuntimeException e) {
            client.send("READY_ACK",requestId,Map.of("ok",false,"message",safe(e)));
        }
    }

    private void shot(ClientHandler client, String requestId, Map<String,Object> p) {
        String id=str(p,"matchId");
        try {
            Match m=requireMatch(id,client.getUser().getUsername());
            GameState g=games.get(id);
            if(g==null || g.turns==null || m.getStatus()!=MatchStatus.PLAYING)
                throw new IllegalStateException("Trận đấu chưa bắt đầu.");
            synchronized(g) {
                int shooter=g.indexOf(client.getUser().getUsername());
                if(shooter<0) throw new IllegalArgumentException("Bạn không thuộc trận đấu.");
                if(g.turns.current()!=shooter) throw new IllegalStateException("Chưa đến lượt của bạn.");
                if(g.turns.expired()) {
                    g.turns.miss();
                    server.broadcast(g.players[0],"TURN_CHANGED",Map.of("matchId",id,"currentPlayer",g.players[g.turns.current()],"reason","TIMEOUT","secondsLeft",g.turns.secondsLeft()));
                    server.broadcast(g.players[1],"TURN_CHANGED",Map.of("matchId",id,"currentPlayer",g.players[g.turns.current()],"reason","TIMEOUT","secondsLeft",g.turns.secondsLeft()));
                    throw new IllegalStateException("Hết 15 giây; lượt đã chuyển cho đối thủ.");
                }
                int row=integer(p,"row"), col=integer(p,"col");
                String result=g.boards[1-shooter].shoot(row,col);
                boolean won=g.boards[1-shooter].allSunk();
                if("MISS".equals(result)) g.turns.miss(); else g.turns.hit();
                Map<String,Object> data=new LinkedHashMap<>();
                data.put("ok",true); data.put("matchId",id); data.put("row",row); data.put("col",col);
                data.put("result",result); data.put("yourTurn",g.turns.current()==shooter);
                data.put("currentPlayer",g.players[g.turns.current()]); data.put("secondsLeft",g.turns.secondsLeft());
                String sunkCells = "SUNK".equals(result) ? g.boards[1-shooter].sunkShipCellsAt(row,col) : "";
                if (!sunkCells.isEmpty()) {
                    data.put("sunkCells", sunkCells);
                    data.put("shipLength", g.boards[1-shooter].shipLengthAt(row,col));
                }
                client.send("SHOT_RESULT",requestId,data);
                Map<String,Object> opponentShot = new LinkedHashMap<>();
                opponentShot.put("matchId",id); opponentShot.put("row",row); opponentShot.put("col",col);
                opponentShot.put("result",result); opponentShot.put("yourTurn",g.turns.current()==1-shooter);
                if (!sunkCells.isEmpty()) {
                    opponentShot.put("sunkCells", sunkCells);
                    opponentShot.put("shipLength", g.boards[1-shooter].shipLengthAt(row,col));
                }
                server.broadcast(g.players[1-shooter],"OPPONENT_SHOT",opponentShot);
                if(won) {
                    server.broadcast(g.players[shooter],"GAME_OVER",Map.of("matchId",id,"result","WIN","winner",g.players[shooter]));
                    server.broadcast(g.players[1-shooter],"GAME_OVER",Map.of("matchId",id,"result","LOSE","winner",g.players[shooter]));
                    matches.finish(id,g.players[shooter],"ALL_SHIPS_SUNK");
                    games.remove(id);
                } else {
                    Map<String,Object> turn=Map.of("matchId",id,"currentPlayer",g.players[g.turns.current()],"secondsLeft",g.turns.secondsLeft());
                    server.broadcast(g.players[0],"TURN_CHANGED",turn);
                    server.broadcast(g.players[1],"TURN_CHANGED",turn);
                }
            }
        } catch(RuntimeException e) {
            client.send("SHOT_RESULT",requestId,Map.of("ok",false,"message",safe(e)));
        }
    }

    private Match requireMatch(String id,String username) {
        Match m=matches.find(id);
        if(m==null || !m.contains(username)) throw new IllegalArgumentException("Trận đấu không tồn tại hoặc bạn không thuộc trận.");
        return m;
    }
    private static String str(Map<String,Object> p,String k){Object v=p.get(k);return v==null?"":String.valueOf(v);}
    private static int integer(Map<String,Object> p,String k){Object v=p.get(k);if(v instanceof Number)return ((Number)v).intValue();return Integer.parseInt(String.valueOf(v));}
    private static String safe(RuntimeException e){return e.getMessage()==null?"Yêu cầu không hợp lệ.":e.getMessage();}
}
