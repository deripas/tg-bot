package com.github.deripas.tgbot.app.telegram;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * The id of an account is the Telegram id, not generated: {@code save} would take a new account for an existing one,
 * so accounts are inserted explicitly.
 */
public interface TelegramUserRepository extends Repository<TelegramUser, Long> {

    @Modifying
    @Query("insert into app.telegram_user (tg_user_id, user_id) values (:tgUserId, :userId) on conflict (tg_user_id) do nothing")
    void insertIfAbsent(long tgUserId, long userId);

    Optional<TelegramUser> findByTgUserId(long tgUserId);

}
