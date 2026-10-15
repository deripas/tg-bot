package com.github.deripas.tgbot.app.users;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;

/**
 * Users of the bot; the other packages use them only through this service.
 */
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;

    public User create(boolean admin) {
        return users.save(new User(null, admin, null, null));
    }

    public Optional<User> findByLogin(String login) {
        return users.findByLogin(login);
    }

    public Optional<User> findById(long id) {
        return users.findById(id);
    }

    public Optional<User> findAdmin() {
        return users.findAdmin();
    }

    /**
     * @param login        login in lower case, unique
     * @param passwordHash encoded password
     * @return {@code false} if the login belongs to another user
     */
    public boolean updateCredentials(long id, String login, String passwordHash) {
        try {
            if (!users.setCredentials(id, login, passwordHash)) {
                throw new IllegalArgumentException("Unknown user " + id);
            }
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
