package com.github.deripas.tgbot.app.users;

import java.util.Optional;

public interface UserRepository {

    User create(boolean admin);

    Optional<User> findById(long id);

    Optional<User> findByLogin(String login);

    Optional<User> findAdmin();

    /**
     * @param login        login in lower case, unique
     * @param passwordHash encoded password
     * @return {@code false} if the login belongs to another user
     */
    boolean updateCredentials(long id, String login, String passwordHash);
}
