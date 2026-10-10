package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /help}: lists available commands.
 */
@RequiredArgsConstructor
public class HelpCommand implements Command {

    public static final CommandInfo INFO = new CommandInfo("help", "list available commands");

    private final List<CommandInfo> commands;

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public void handle(CommandContext context) {
        final String text = commands.stream()
                .map(command -> "/" + command.name() + " - " + command.description())
                .collect(Collectors.joining("\n", "Commands:\n", ""));
        context.reply(text);
    }
}
