package com.github.deripas.tgbot.app.telegram;

import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * Telegram accounts of the users.
 */
@RequiredArgsConstructor
public class TelegramUserService {

    private final TelegramUserRepository accounts;

    /**
     * Links the Telegram account to the user; linking it again to the same user does nothing.
     *
     * @throws IllegalArgumentException if the Telegram account is linked to another user
     */
    public void add(long userId, long tgUserId) {
        accounts.insertIfAbsent(tgUserId, userId);
        final long linked = findUserId(tgUserId).orElseThrow();
        if (linked != userId) {
            throw new IllegalArgumentException("Telegram user " + tgUserId + " is linked to user " + linked);
        }
    }

    /**
     * @return our user who sent the message, empty if the sender is unknown
     */
    public Optional<Long> findUserId(long tgUserId) {
        return accounts.findByTgUserId(tgUserId).map(TelegramUser::userId);
    }
}
