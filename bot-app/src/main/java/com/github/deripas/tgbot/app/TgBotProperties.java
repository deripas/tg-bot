package com.github.deripas.tgbot.app;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Bot settings, prefix {@code tgbot}.
 *
 * @param token     bot token from @BotFather; if blank, the bot does not connect to Telegram
 * @param admin     Telegram id of the administrator; {@code null} if nobody may run commands
 * @param publicUrl address of the Web UI for the links sent by the bot
 */
@ConfigurationProperties("tgbot")
public record TgBotProperties(
        String token,

        Long admin,

        @DefaultValue("http://localhost:8080")
        String publicUrl
) {
}
