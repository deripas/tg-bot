package com.github.deripas.tgbot.app;

import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.services.CommandDispatcher;
import com.github.deripas.tgbot.app.telegram.TelegramUsers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"tgbot.token=", "tgbot.admin=100"})
class TgBotApplicationTest {

    @Autowired
    CommandDispatcher dispatcher;

    @Autowired
    TelegramUsers telegramUsers;

    @Test
    void builtInAndSkillCommandsAreRegistered() {
        assertThat(dispatcher.getCommands())
                .extracting(CommandInfo::name)
                .containsExactlyInAnyOrder("ping", "help", "login", "user", "bill");
    }

    @Test
    void adminIsLinkedToTelegram() {
        assertThat(telegramUsers.findUserId(100)).contains(1L);
        assertThat(telegramUsers.findUserId(200)).isEmpty();
    }
}
