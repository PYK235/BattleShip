package battleship.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class UserManager {
    private final UserDAO dao; private final Map<String,ClientHandler> sessions=new ConcurrentHashMap<>();
    public UserManager(UserDAO dao){this.dao=dao;}
    public synchronized String login(String username,String password,ClientHandler handler){
        User u=dao.find(username); if(!dao.authenticate(username,password)||u==null)return "Tên đăng nhập hoặc mật khẩu không đúng.";
        String key=u.getUsername().toLowerCase(Locale.ROOT);if(sessions.containsKey(key))return "Tài khoản đang được đăng nhập ở nơi khác.";
        sessions.put(key,handler);u.setStatus(User.Status.IDLE);handler.setUser(u);return null;
    }
    public synchronized void logout(ClientHandler h){User u=h.getUser();if(u==null)return;sessions.remove(u.getUsername().toLowerCase(Locale.ROOT),h);u.setStatus(User.Status.OFFLINE);h.setUser(null);}
    public synchronized boolean setStatus(String username,User.Status status){User u=dao.find(username);if(u==null||u.getStatus()==User.Status.OFFLINE&&status!=User.Status.OFFLINE)return false;u.setStatus(status);return true;}
    public synchronized ClientHandler session(String username){return sessions.get(username==null?"":username.toLowerCase(Locale.ROOT));}
    public synchronized List<Map<String,Object>> lobby(){List<Map<String,Object>> out=new ArrayList<>();for(User u:allUsers())if(u.getStatus()!=User.Status.OFFLINE){Map<String,Object> x=new LinkedHashMap<>();x.put("username",u.getUsername());x.put("status",u.getStatus().name());x.put("totalScore",u.getTotalScore());out.add(x);}out.sort(Comparator.comparing(x->x.get("username").toString(),String.CASE_INSENSITIVE_ORDER));return out;}
    private Collection<User> allUsers(){return dao.all();}
    public synchronized List<ClientHandler> clients(){return new ArrayList<>(sessions.values());}
}
