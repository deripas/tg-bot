package com.github.deripas.tgbot.app.telegram;

import com.github.deripas.tgbot.api.Attachment;
import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * {@link Attachment} backed by a Telegram file; content is downloaded lazily on first access.
 */
@RequiredArgsConstructor
final class TelegramAttachment implements Attachment {

    private final TelegramClient client;
    private final String fileId;
    private final String mimeType;
    private final String fileName;

    private byte[] content;

    /**
     * Attachments of the message or, if it has none, of the message it replies to.
     * For a photo only the largest size is taken.
     */
    static List<Attachment> of(TelegramClient client, Message message) {
        final List<Attachment> own = fromMessage(client, message);
        if (!own.isEmpty() || message.getReplyToMessage() == null) {
            return own;
        }
        return fromMessage(client, message.getReplyToMessage());
    }

    private static List<Attachment> fromMessage(TelegramClient client, Message message) {
        if (message.getPhoto() != null && !message.getPhoto().isEmpty()) {
            final PhotoSize largest = message.getPhoto().stream()
                    .max(Comparator.comparingInt(photo -> photo.getWidth() * photo.getHeight()))
                    .orElseThrow();
            return List.of(new TelegramAttachment(client, largest.getFileId(), "image/jpeg", "photo.jpg"));
        }
        final Document document = message.getDocument();
        if (document != null) {
            final String mimeType = Optional.ofNullable(document.getMimeType()).orElse("application/octet-stream");
            final String fileName = Optional.ofNullable(document.getFileName()).orElse("file");
            return List.of(new TelegramAttachment(client, document.getFileId(), mimeType, fileName));
        }
        return List.of();
    }

    @Override
    public String mimeType() {
        return mimeType;
    }

    @Override
    public String fileName() {
        return fileName;
    }

    @Override
    public synchronized byte[] content() {
        if (content == null) {
            try (InputStream input = client.downloadFileAsStream(client.execute(new GetFile(fileId)))) {
                content = input.readAllBytes();
            } catch (Exception e) {
                throw new IllegalStateException("failed to download file from Telegram: " + fileName, e);
            }
        }
        return content;
    }
}
