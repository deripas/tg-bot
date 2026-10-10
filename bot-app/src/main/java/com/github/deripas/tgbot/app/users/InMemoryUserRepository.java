package com.github.deripas.tgbot.app.users;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Users in memory, lost on restart: to try out the data model before the database.
 */
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> users = new LinkedHashMap<>();
    private long lastId;

    @Override
    public synchronized User create(boolean admin) {
        final User user = new User(++lastId, admin, null, null);
        users.put(user.id(), user);
        return user;
    }

    @Override
    public synchronized Optional<User> findById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public synchronized Optional<User> findByLogin(String login) {
        return users.values().stream().filter(user -> login.equals(user.login())).findFirst();
    }

    @Override
    public synchronized Optional<User> findAdmin() {
        return users.values().stream().filter(User::admin).findFirst();
    }

    @Override
    public synchronized boolean updateCredentials(long id, String login, String passwordHash) {
        final User user = findById(id).orElseThrow(() -> new IllegalArgumentException("Unknown user " + id));
        if (findByLogin(login).filter(other -> other.id() != id).isPresent()) {
            return false;
        }
        users.put(id, new User(id, user.admin(), login, passwordHash));
        return true;
    }
}
