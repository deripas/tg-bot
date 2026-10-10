package com.github.deripas.tgbot.api;

/**
 * Message sent by the bot in reply to a command.
 */
public interface Reply {

    /**
     * Replaces the text of the sent message.
     *
     * @param text new text
     * @throws IllegalStateException if the message cannot be edited
     */
    void edit(String text);
}
