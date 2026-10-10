package com.github.deripas.tgbot.app.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * Bot settings, prefix {@code tgbot}.
 *
 * @param token        bot token from @BotFather; if blank, the bot does not connect to Telegram
 * @param allowedUsers Telegram ids of users allowed to run commands
 */
@ConfigurationProperties("tgbot")
public record BotProperties(
        String token,
        Set<Long> allowedUsers
) {

    public BotProperties {
        allowedUsers = allowedUsers == null
                ? Set.of()
                : Set.copyOf(allowedUsers);
    }
}
