package com.github.deripas.tgbot.app.config;

import com.github.deripas.tgbot.app.props.BotProperties;
import com.github.deripas.tgbot.app.telegram.TelegramUsers;
import com.github.deripas.tgbot.app.users.InMemoryUserRepository;
import com.github.deripas.tgbot.app.users.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Users and their Telegram accounts. The administrator is created on startup and linked to the Telegram
 * account {@code tgbot.admin}.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
class UsersConfig {

    @Bean
    UserRepository userRepository() {
        final UserRepository users = new InMemoryUserRepository();
        users.create(true);
        return users;
    }

    @Bean
    TelegramUsers telegramUsers(UserRepository users, BotProperties properties) {
        final TelegramUsers telegramUsers = new TelegramUsers();
        if (properties.admin() == null) {
            log.warn("tgbot.admin is not set, all commands will be rejected");
        } else {
            telegramUsers.add(users.findAdmin().orElseThrow().id(), properties.admin());
        }
        return telegramUsers;
    }
}
