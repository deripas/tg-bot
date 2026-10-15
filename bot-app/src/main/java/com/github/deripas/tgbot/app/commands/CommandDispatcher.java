package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routes a parsed command to its {@link Command} by name. Replies with a hint if the command is unknown.
 */
public class CommandDispatcher {

    static final String UNKNOWN_COMMAND = "Unknown command /%s. See /help for the list of commands.";

    /**
     * Registered commands in registration order.
     */
    @Getter
    private final List<CommandInfo> commands;
    private final Map<String, Command> commandsByName;

    /**
     * @param commands commands to dispatch to
     * @throws IllegalArgumentException if a command name is used by more than one command
     */
    public CommandDispatcher(List<Command> commands) {
        final Map<String, Command> byName = new LinkedHashMap<>();
        for (Command command : commands) {
            final String name = command.info().name();
            final Command duplicate = byName.putIfAbsent(name, command);
            if (duplicate != null) {
                throw new IllegalArgumentException("duplicate command name '" + name + "' in "
                        + duplicate.getClass().getName() + " and " + command.getClass().getName());
            }
        }
        this.commandsByName = Map.copyOf(byName);
        this.commands = byName.values().stream()
                .map(Command::info)
                .toList();
    }

    /**
     * Handles the command or replies to the user if it cannot be handled.
     *
     * @param command command name without the leading slash, lower case
     * @param context invocation context
     * @throws Exception error thrown by the command
     */
    public void handle(String command, CommandContext context) throws Exception {
        final Command handler = commandsByName.get(command);
        if (handler == null) {
            context.reply(UNKNOWN_COMMAND.formatted(command));
            return;
        }
        handler.handle(context);
    }
}
