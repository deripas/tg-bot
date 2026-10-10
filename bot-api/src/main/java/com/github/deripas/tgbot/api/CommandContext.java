package com.github.deripas.tgbot.api;

import java.util.List;

/**
 * Context of a single command invocation: who sent it, its arguments and attachments.
 */
public interface CommandContext {

    /**
     * @return id of the chat the command was sent to
     */
    long chatId();

    /**
     * @return id of the user who sent the command
     */
    long userId();

    /**
     * @return text after the command name, stripped; empty string if there are no arguments
     */
    String args();

    /**
     * Attachments of the command message or, if it has none, of the message it replies to.
     *
     * @return attachments, possibly empty
     */
    List<Attachment> attachments();

    /**
     * Sends a reply to the command message.
     *
     * @param text reply text
     * @return handle to edit the sent reply later, e.g. to show progress of a long operation
     * @throws IllegalStateException if the reply cannot be sent
     */
    Reply reply(String text);
}
