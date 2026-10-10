package com.github.deripas.tgbot.api;

/**
 * Bot command such as {@code /ping}. Register an implementation as a Spring bean to make it available.
 */
public interface Command {

    /**
     * @return command name and description shown in the bot menu
     */
    CommandInfo info();

    /**
     * Handles the command. Called on a virtual thread, so blocking and long-running work is allowed.
     *
     * @param context message the command came from and a way to reply to it
     * @throws Exception any error; it is logged and the user gets a generic error reply
     */
    void handle(CommandContext context) throws Exception;
}
