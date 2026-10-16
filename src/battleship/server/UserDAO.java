package battleship.server;

import java.util.Collection;

public interface UserDAO {
    boolean create(String username, String password);
    boolean authenticate(String username, String password);
    User find(String username);
    Collection<User> all();
}
