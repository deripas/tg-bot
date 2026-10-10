package com.github.deripas.tgbot.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandInfoTest {

    @Test
    void validInfo() {
        assertThatCode(() -> new CommandInfo("add_bill2", "add a bill")).doesNotThrowAnyException();
    }

    @Test
    void upperCaseNameIsRejected() {
        assertThatThrownBy(() -> new CommandInfo("Help", "help"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid command name 'Help'");
    }

    @Test
    void hyphenAndTooLongNamesAreRejected() {
        assertThatThrownBy(() -> new CommandInfo("add-bill", "add a bill"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandInfo("a".repeat(33), "too long"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blankOrTooLongDescriptionIsRejected() {
        assertThatThrownBy(() -> new CommandInfo("help", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CommandInfo("help", "x".repeat(257)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
