package com.github.deripas.tgbot.app;

import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.services.CommandDispatcher;
import com.github.deripas.tgbot.app.telegram.TelegramUserService;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"tgbot.token=", "tgbot.admin=100"})
@Import(TestDatabase.class)
class TgBotApplicationTest {

    @Autowired
    CommandDispatcher dispatcher;

    @Autowired
    TelegramUserService telegramUsers;

    @Autowired
    UserService users;

    @Autowired
    SmartInitializingSingleton adminInitializer;

    @Test
    void builtInAndSkillCommandsAreRegistered() {
        assertThat(dispatcher.getCommands())
                .extracting(CommandInfo::name)
                .containsExactlyInAnyOrder("ping", "help", "login", "user", "bill");
    }

    @Test
    void adminIsCreatedOnceAndLinkedToTelegram() {
        final User admin = users.findAdmin().orElseThrow();

        // the next start
        adminInitializer.afterSingletonsInstantiated();

        assertThat(users.findAdmin()).contains(admin);
        assertThat(telegramUsers.findUserId(100)).contains(admin.id());
    }
}
