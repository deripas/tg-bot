package com.github.deripas.tgbot.app.users;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;

public interface UserRepository extends ListCrudRepository<User, Long> {

    Optional<User> findByLogin(String login);

    @Query("select * from app.users where admin")
    Optional<User> findAdmin();

    /**
     * @return {@code false} if there is no such user
     */
    @Modifying
    @Query("update app.users set login = :login, password_hash = :passwordHash where id = :id")
    boolean setCredentials(long id, String login, String passwordHash);
}
