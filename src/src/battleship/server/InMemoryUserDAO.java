package src.battleship.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collection;

public final class InMemoryUserDAO implements UserDAO {
    private final ConcurrentHashMap<String,String> hashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String,User> users = new ConcurrentHashMap<>();
    public InMemoryUserDAO(){ create("alice","123456");create("bob","123456");create("charlie","123456"); }
    public boolean create(String username,String password){String key=normalize(username);if(!valid(username,password))return false;
        String hash=hash(password);if(hashes.putIfAbsent(key,hash)!=null)return false;users.put(key,new User(username.trim(),0,0,0,0));return true;}
    public boolean authenticate(String username,String password){String stored=hashes.get(normalize(username));return stored!=null&&MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8),hash(password).getBytes(StandardCharsets.UTF_8));}
    public User find(String username){return users.get(normalize(username));}
    public Collection<User> all(){return users.values();}
    private static boolean valid(String u,String p){return u!=null&&p!=null&&u.trim().matches("[A-Za-z0-9_]{3,20}")&&p.length()>=6&&p.length()<=64;}
    private static String normalize(String s){return s==null?"":s.trim().toLowerCase();}
    private static String hash(String p){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(p.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
