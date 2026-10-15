package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.api.Reply;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandDispatcherTest {

    private static final long USER = 42;

    private final List<String> handled = new ArrayList<>();

    private final Command echo = new Command() {
        @Override
        public CommandInfo info() {
            return new CommandInfo("echo", "echo");
        }

        @Override
        public void handle(CommandContext context) {
            handled.add(context.args());
        }
    };

    @Test
    void dispatchesByName() throws Exception {
        final FakeContext context = new FakeContext(USER, "hi");
        new CommandDispatcher(List.of(echo)).handle("echo", context);
        assertThat(handled).containsExactly("hi");
        assertThat(context.replies).isEmpty();
    }

    @Test
    void unknownCommandRepliesWithHint() throws Exception {
        final FakeContext context = new FakeContext(USER, "");
        new CommandDispatcher(List.of(echo)).handle("nope", context);
        assertThat(context.replies).containsExactly(CommandDispatcher.UNKNOWN_COMMAND.formatted("nope"));
    }

    @Test
    void duplicateNamesAreRejected() {
        assertThatThrownBy(() -> new CommandDispatcher(List.of(echo, echo)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate command name 'echo'");
    }

    private record FakeContext(long userId, String args, List<String> replies) implements CommandContext {

        FakeContext(long userId, String args) {
            this(userId, args, new ArrayList<>());
        }

        @Override
        public boolean isPrivateChat() {
            return true;
        }

        @Override
        public long chatId() {
            return 1;
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
