package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.Reply;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.ott.InMemoryOneTimeTokenService;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenAuthenticationToken;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginCommandTest {

    private final InMemoryOneTimeTokenService tokens = new InMemoryOneTimeTokenService();
    private final LoginCommand command = new LoginCommand(tokens, "https://bot.example");

    @Test
    void repliesWithLinkForSender() {
        final FakeContext context = new FakeContext(true);

        command.handle(context);

        assertThat(context.replies).singleElement().asString().contains("https://bot.example/login/ott?token=");
        final String token = context.replies.getFirst().replaceAll("(?s).*token=", "");
        final OneTimeToken consumed = tokens.consume(new OneTimeTokenAuthenticationToken(token));
        assertThat(consumed.getUsername()).isEqualTo("7");
    }

    @Test
    void refusesInGroup() {
        final FakeContext context = new FakeContext(false);

        command.handle(context);

        assertThat(context.replies).containsExactly(LoginCommand.PRIVATE_ONLY);
    }

    private record FakeContext(boolean isPrivateChat, List<String> replies) implements CommandContext {

        FakeContext(boolean isPrivateChat) {
            this(isPrivateChat, new ArrayList<>());
        }

        @Override
        public long chatId() {
            return 1;
        }

        @Override
        public long userId() {
            return 7;
        }

        @Override
        public String args() {
            return "";
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
