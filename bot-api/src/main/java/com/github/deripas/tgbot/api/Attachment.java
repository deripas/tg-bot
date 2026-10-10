package com.github.deripas.tgbot.api;

/**
 * File attached to the message that triggered a command (photo, document, etc.).
 */
public interface Attachment {

    /**
     * @return MIME type of the file, never {@code null}; {@code application/octet-stream} if unknown
     */
    String mimeType();

    /**
     * @return original file name, never {@code null}
     */
    String fileName();

    /**
     * Returns file content. The first call may download the file, so it can be slow;
     * subsequent calls return cached bytes.
     *
     * @return file content
     * @throws IllegalStateException if the file cannot be downloaded
     */
    byte[] content();

    /**
     * @return {@code true} if the attachment is an image
     */
    default boolean isImage() {
        return mimeType().startsWith("image/");
    }
}
