package com.github.deripas.tgbot.app.telegram;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramParsedCommandTest {

    @Test
    void commandWithoutArguments() {
        assertThat(TelegramParsedCommand.parse("/add_bill"))
                .contains(new TelegramParsedCommand("add_bill", null, ""));
    }

    @Test
    void commandWithArguments() {
        assertThat(TelegramParsedCommand.parse("  /add_bill  corner shop "))
                .contains(new TelegramParsedCommand("add_bill", null, "corner shop"));
    }

    @Test
    void botMentionIsParsed() {
        assertThat(TelegramParsedCommand.parse("/add_bill@my_tg_bot milk"))
                .contains(new TelegramParsedCommand("add_bill", "my_tg_bot", "milk"));
    }

    @Test
    void nameIsCaseInsensitive() {
        assertThat(TelegramParsedCommand.parse("/Add_Bill"))
                .contains(new TelegramParsedCommand("add_bill", null, ""));
    }

    @Test
    void plainTextIsNotACommand() {
        assertThat(TelegramParsedCommand.parse("hello")).isEmpty();
        assertThat(TelegramParsedCommand.parse("bought /add_bill")).isEmpty();
        assertThat(TelegramParsedCommand.parse(null)).isEmpty();
    }

    @Test
    void hyphenIsNotAllowedInCommandName() {
        assertThat(TelegramParsedCommand.parse("/add-bill")).isEmpty();
    }

    @Test
    void tooLongNameIsNotACommand() {
        assertThat(TelegramParsedCommand.parse("/" + "a".repeat(33))).isEmpty();
    }

    @Test
    void commandWithoutMentionIsAddressedToAnyBot() {
        assertThat(TelegramParsedCommand.parse("/help").orElseThrow().isAddressedTo("my_tg_bot")).isTrue();
    }

    @Test
    void mentionIsComparedIgnoringCase() {
        assertThat(TelegramParsedCommand.parse("/help@My_TG_Bot").orElseThrow().isAddressedTo("my_tg_bot")).isTrue();
    }

    @Test
    void commandForAnotherBotIsIgnored() {
        assertThat(TelegramParsedCommand.parse("/help@other_bot").orElseThrow().isAddressedTo("my_tg_bot")).isFalse();
    }
}
