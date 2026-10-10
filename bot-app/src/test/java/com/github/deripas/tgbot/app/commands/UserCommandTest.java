package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.Reply;
import com.github.deripas.tgbot.app.telegram.TelegramUsers;
import com.github.deripas.tgbot.app.users.InMemoryUserRepository;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserCommandTest {

    private final UserRepository users = new InMemoryUserRepository();
    private final TelegramUsers telegramUsers = new TelegramUsers();
    private final UserCommand command = new UserCommand(users, telegramUsers);
    private final User admin = users.create(true);

    @Test
    void adminAddsUserLinkedToTelegram() {
        final FakeContext context = new FakeContext(admin.id(), "add 200");

        command.handle(context);

        assertThat(context.replies).containsExactly("User 2 is added for Telegram user 200");
        assertThat(telegramUsers.findUserId(200)).contains(2L);
        assertThat(users.findById(2)).contains(new User(2, false, null, null));
    }

    @Test
    void linkedAccountIsNotAddedAgain() {
        command.handle(new FakeContext(admin.id(), "add 200"));
        final FakeContext context = new FakeContext(admin.id(), "add 200");

        command.handle(context);

        assertThat(context.replies).containsExactly("Telegram user 200 is already user 2");
        assertThat(users.findById(3)).isEmpty();
    }

    @Test
    void notAdminIsRefused() {
        final User user = users.create(false);
        final FakeContext context = new FakeContext(user.id(), "add 200");

        command.handle(context);

        assertThat(context.replies).containsExactly(UserCommand.ADMIN_ONLY);
        assertThat(telegramUsers.findUserId(200)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "add", "add x", "add -1", "add 1 2", "remove 200"})
    void otherArgumentsShowUsage(String args) {
        final FakeContext context = new FakeContext(admin.id(), args);

        command.handle(context);

        assertThat(context.replies).containsExactly(UserCommand.USAGE);
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
