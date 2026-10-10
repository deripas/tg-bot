package com.github.deripas.tgbot.app.services;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a command by name and calls it. Replies with a hint if the command is unknown.
 */
public class CommandDispatcherImpl implements CommandDispatcher {

    static final String UNKNOWN_COMMAND = "Unknown command /%s. See /help for the list of commands.";

    @Getter
    private final List<CommandInfo> commands;
    private final Map<String, Command> commandsByName;

    /**
     * @param commands commands to dispatch to
     * @throws IllegalArgumentException if a command name is used by more than one command
     */
    public CommandDispatcherImpl(List<Command> commands) {
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

    @Override
    public void handle(String command, CommandContext context) throws Exception {
        final Command handler = commandsByName.get(command);
        if (handler == null) {
            context.reply(UNKNOWN_COMMAND.formatted(command));
            return;
        }
        handler.handle(context);
    }
}
