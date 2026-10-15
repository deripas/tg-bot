package com.github.deripas.tgbot.app.telegram;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Telegram account of a user.
 *
 * @param tgUserId Telegram user id, also the id of the private chat with the bot
 * @param userId   our user
 */
@Table(schema = "app")
public record TelegramUser(@Id long tgUserId, long userId) {
}
