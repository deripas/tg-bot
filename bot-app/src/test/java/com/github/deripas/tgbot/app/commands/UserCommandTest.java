package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.Reply;
import com.github.deripas.tgbot.app.TestDatabase;
import com.github.deripas.tgbot.app.telegram.TelegramUserService;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"tgbot.token=", "tgbot.admin=100"})
@Import(TestDatabase.class)
class UserCommandTest {

    @Autowired
    UserCommand command;

    @Autowired
    UserService users;

    @Autowired
    TelegramUserService telegramUsers;

    @Test
    void adminAddsUserLinkedToTelegram() {
        final FakeContext context = new FakeContext(admin(), "add 200");

        command.handle(context);

        final long userId = telegramUsers.findUserId(200).orElseThrow();
        assertThat(context.replies).containsExactly("User " + userId + " is added for Telegram user 200");
        assertThat(users.findById(userId)).contains(new User(userId, false, null, null));
    }

    @Test
    void linkedAccountIsNotAddedAgain() {
        command.handle(new FakeContext(admin(), "add 201"));
        final long userId = telegramUsers.findUserId(201).orElseThrow();
        final FakeContext context = new FakeContext(admin(), "add 201");

        command.handle(context);

        assertThat(context.replies).containsExactly("Telegram user 201 is already user " + userId);
    }

    @Test
    void notAdminIsRefused() {
        final FakeContext context = new FakeContext(users.create(false).id(), "add 202");

        command.handle(context);

        assertThat(context.replies).containsExactly(UserCommand.ADMIN_ONLY);
        assertThat(telegramUsers.findUserId(202)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "add", "add x", "add -1", "add 1 2", "remove 200"})
    void otherArgumentsShowUsage(String args) {
        final FakeContext context = new FakeContext(admin(), args);

        command.handle(context);

        assertThat(context.replies).containsExactly(UserCommand.USAGE);
    }

    private long admin() {
        return users.findAdmin().orElseThrow().id();
    }

    private record FakeContext(long userId, String args, List<String> replies) implements CommandContext {

        FakeContext(long userId, String args) {
            this(userId, args, new ArrayList<>());
        }

        @Override
        public long chatId() {
            return 1;
        }

        @Override
        public boolean isPrivateChat() {
            return true;
        }

        @Override
        public List<Attachment> attachments() {
            return List.of();
        }

        @Override
        public Reply reply(String text) {
            replies.add(text);
            return replies::add;
        }
    }
}
