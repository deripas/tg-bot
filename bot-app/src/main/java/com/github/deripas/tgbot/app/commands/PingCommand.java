package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;

/**
 * {@code /ping}: checks that the bot is alive.
 */
public class PingCommand implements Command {

    public static final CommandInfo INFO = new CommandInfo("ping", "check that the bot is alive");

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public void handle(CommandContext context) {
        context.reply("pong");
    }
}
