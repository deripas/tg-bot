package com.github.deripas.tgbot.app.services;

import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;

import java.util.List;

/**
 * Routes a parsed command to its {@link com.github.deripas.tgbot.api.Command} implementation.
 * Decorators such as {@link CommandDispatcherAuth} add cross-cutting behaviour on top of {@link CommandDispatcherImpl}.
 */
public interface CommandDispatcher {

    /**
     * @return registered commands in registration order
     */
    List<CommandInfo> getCommands();

    /**
     * Handles the command or replies to the user if it cannot be handled.
     *
     * @param command command name without the leading slash, lower case
     * @param context invocation context
     * @throws Exception error thrown by the command
     */
    void handle(String command, CommandContext context) throws Exception;
}
