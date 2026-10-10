package com.github.deripas.tgbot.app.telegram;

/**
 * Telegram account of a user.
 *
 * @param userId   our user
 * @param tgUserId Telegram user id, also the id of the private chat with the bot
 */
public record TelegramUser(long userId, long tgUserId) {
}
