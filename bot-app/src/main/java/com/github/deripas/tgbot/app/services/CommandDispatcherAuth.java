package com.github.deripas.tgbot.app.services;

import com.github.deripas.tgbot.api.CommandContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;

/**
 * Lets only whitelisted users run commands. Others get their Telegram id in the reply,
 * so the owner can add it to {@code tgbot.allowed-users}.
 */
@Slf4j
public class CommandDispatcherAuth extends CommandDispatcherDelegate {

    static final String ACCESS_DENIED = "Access denied. Your Telegram id: %d";

    private final Set<Long> allowedUsers;

    /**
     * @param delegate     dispatcher to call for allowed users
     * @param allowedUsers ids of allowed users; if empty, nobody is allowed
     */
    public CommandDispatcherAuth(CommandDispatcher delegate, Set<Long> allowedUsers) {
        super(delegate);
        this.allowedUsers = Set.copyOf(allowedUsers);
        if (this.allowedUsers.isEmpty()) {
            log.warn("tgbot.allowed-users is empty, all commands will be rejected");
        }
    }

    @Override
    public void handle(String command, CommandContext context) throws Exception {
        if (!allowedUsers.contains(context.userId())) {
            log.warn("access denied for user {} to command '{}'", context.userId(), command);
            context.reply(ACCESS_DENIED.formatted(context.userId()));
            return;
        }
        super.handle(command, context);
    }
}
