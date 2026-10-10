package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.authentication.ott.OneTimeTokenService;

/**
 * {@code /login}: one-time link to log in to the Web UI as the user who sent the command; also the way
 * to choose the login and the password for the first time or to reset a forgotten password.
 * Only in the private chat with the bot: in a group the link would let others in.
 */
@RequiredArgsConstructor
public class LoginCommand implements Command {

    public static final CommandInfo INFO = new CommandInfo("login", "link to log in to the Web UI");
    static final String PRIVATE_ONLY = "Send /login in the private chat with the bot.";

    private final OneTimeTokenService tokens;
    /**
     * Address of the Web UI, without the trailing slash.
     */
    private final String publicUrl;

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public void handle(CommandContext context) {
        if (!context.isPrivateChat()) {
            context.reply(PRIVATE_ONLY);
            return;
        }
        final OneTimeToken token = tokens.generate(new GenerateOneTimeTokenRequest(String.valueOf(context.userId())));
        context.reply("Log in within 5 minutes, the link works once; on the first login choose a login and a password:\n"
                + publicUrl + "/login/ott?token=" + token.getTokenValue());
    }
}
