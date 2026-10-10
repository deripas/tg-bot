package com.github.deripas.tgbot.app.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramUsersTest {

    private final TelegramUsers users = new TelegramUsers();

    @Test
    void userIsFoundByTelegramId() {
        users.add(1, 100);

        assertThat(users.findUserId(100)).contains(1L);
        assertThat(users.findUserId(200)).isEmpty();
    }

    @Test
    void accountIsLinkedToOneUser() {
        users.add(1, 100);
        users.add(1, 100);

        assertThatThrownBy(() -> users.add(2, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Telegram user 100 is linked to user 1");
    }
}
