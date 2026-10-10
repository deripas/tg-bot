package com.github.deripas.tgbot.receipt.store;

/**
 * Photo of a receipt, stored for processing.
 *
 * @param mimeType type of the image, e.g. {@code image/jpeg}
 * @param content  image file
 * @param hash     SHA-256 of the content: the same file is not uploaded twice
 */
record ReceiptPhoto(String mimeType, byte[] content, String hash) {
}
