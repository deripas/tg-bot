package com.github.deripas.tgbot.app;

import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.services.CommandDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "tgbot.token=")
class TgBotApplicationTest {

    @Autowired
    CommandDispatcher dispatcher;

    @Test
    void builtInCommandsAreRegistered() {
        assertThat(dispatcher.getCommands())
                .extracting(CommandInfo::name)
                .containsExactlyInAnyOrder("ping", "help");
    }
}
