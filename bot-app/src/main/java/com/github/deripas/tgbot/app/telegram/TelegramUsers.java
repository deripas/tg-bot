package com.github.deripas.tgbot.app.telegram;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Telegram accounts of the users, in memory: to try out the data model before the database.
 */
public class TelegramUsers {

    private final Map<Long, TelegramUser> accounts = new HashMap<>();

    /**
     * @throws IllegalArgumentException if the Telegram account is linked to another user
     */
    public synchronized void add(long userId, long tgUserId) {
        final TelegramUser existing = accounts.putIfAbsent(tgUserId, new TelegramUser(userId, tgUserId));
        if (existing != null && existing.userId() != userId) {
            throw new IllegalArgumentException("Telegram user " + tgUserId + " is linked to user " + existing.userId());
        }
    }

    /**
     * @return our user who sent the message, empty if the sender is unknown
     */
    public synchronized Optional<Long> findUserId(long tgUserId) {
        return Optional.ofNullable(accounts.get(tgUserId)).map(TelegramUser::userId);
    }
}
