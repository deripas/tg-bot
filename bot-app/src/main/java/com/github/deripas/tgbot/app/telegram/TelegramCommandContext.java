package com.github.deripas.tgbot.app.telegram;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.Reply;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;

/**
 * {@link CommandContext} for a Telegram message; replies are sent as replies to that message.
 */
@RequiredArgsConstructor
final class TelegramCommandContext implements CommandContext {

    private final TelegramClient client;
    private final Message message;
    private final String args;
    private final long userId;
    private List<Attachment> attachments;

    @Override
    public long chatId() {
        return message.getChatId();
    }

    @Override
    public long userId() {
        return userId;
    }

    @Override
    public boolean isPrivateChat() {
        return message.getChat().isUserChat();
    }

    @Override
    public String args() {
        return args;
    }

    @Override
    public List<Attachment> attachments() {
        if (attachments == null) {
            attachments = TelegramAttachment.of(client, message);
        }
        return attachments;
    }

    @Override
    public Reply reply(String text) {
        try {
            final Message sent = client.execute(SendMessage.builder()
                    .chatId(chatId())
                    .replyToMessageId(message.getMessageId())
                    .text(text)
                    .build());
            return newText -> edit(sent.getMessageId(), newText);
        } catch (TelegramApiException e) {
            throw new IllegalStateException("failed to send reply to chat " + chatId(), e);
        }
    }

    private void edit(Integer messageId, String text) {
        try {
            client.execute(EditMessageText.builder()
                    .chatId(chatId())
                    .messageId(messageId)
                    .text(text)
                    .build());
        } catch (TelegramApiException e) {
            throw new IllegalStateException("failed to edit message " + messageId, e);
        }
    }
}
