package com.github.deripas.tgbot.receipt.store;

/**
 * Receipt taken for processing.
 *
 * @param id       receipt id
 * @param mimeType type of the photo
 * @param photo    photo file
 */
public record PendingReceipt(long id, String mimeType, byte[] photo) {
}
