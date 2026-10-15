package com.github.deripas.tgbot.app.config;

import com.github.deripas.tgbot.app.TgBotProperties;
import com.github.deripas.tgbot.app.telegram.TelegramUserRepository;
import com.github.deripas.tgbot.app.telegram.TelegramUserService;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserRepository;
import com.github.deripas.tgbot.app.users.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Users and their Telegram accounts in the {@code app} schema; the repositories are Spring Data JDBC.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class UsersConfig {

    @Bean
    TelegramUserService telegramUsers(TelegramUserRepository accounts) {
        return new TelegramUserService(accounts);
    }

    @Bean
    UserService users(UserRepository users) {
        return new UserService(users);
    }

    /**
     * Creates the administrator on the first start and links it to the Telegram account {@code tgbot.admin};
     * before the bot starts taking commands.
     */
    @Bean
    SmartInitializingSingleton adminInitializer(
            UserService users,
            TelegramUserService telegramUsers,
            TgBotProperties properties,
            TransactionOperations transactions
    ) {
        return () -> transactions.executeWithoutResult(status -> {
            final User admin = users.findAdmin().orElseGet(() -> users.create(true));
            if (properties.admin() == null) {
                log.warn("tgbot.admin is not set, all commands will be rejected");
            } else {
                telegramUsers.add(admin.id(), properties.admin());
            }
        });
    }
}
