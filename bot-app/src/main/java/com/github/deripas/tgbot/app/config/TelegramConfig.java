package com.github.deripas.tgbot.app.config;

import com.github.deripas.tgbot.app.TgBotProperties;
import com.github.deripas.tgbot.app.commands.CommandDispatcher;
import com.github.deripas.tgbot.app.telegram.TelegramConsumer;
import com.github.deripas.tgbot.app.telegram.TelegramUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

/**
 * Telegram connection. Disabled when {@code tgbot.token} is blank, so the Web UI can run without a bot.
 */
@RequiredArgsConstructor
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${tgbot.token:}'.isBlank()")
class TelegramConfig {

    private final TgBotProperties properties;

    @Bean
    TelegramClient telegramClient() {
        return new OkHttpTelegramClient(properties.token());
    }

    @Bean
    TelegramConsumer telegramConsumer(
            TelegramClient telegramClient,
            TelegramUserService telegramUsers,
            CommandDispatcher commandDispatcher
    ) {
        return new TelegramConsumer(properties.token(), telegramClient, telegramUsers, commandDispatcher);
    }
}
