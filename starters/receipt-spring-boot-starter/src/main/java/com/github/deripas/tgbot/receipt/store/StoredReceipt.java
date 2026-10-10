package com.github.deripas.tgbot.receipt.store;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Uploaded receipt.
 *
 * @param id          number of the receipt to check its status later
 * @param userId      user who uploaded the receipt; {@code null} if uploaded without login
 * @param createdAt   upload time
 * @param purchasedAt date and time printed on the receipt, {@code null} until the receipt is read
 * @param status      processing status
 * @param reason      human-readable explanation of the status, {@code null} for {@link ReceiptStatus#CREATED}
 * @param storeName   seller from the receipt, {@code null} until the receipt is read
 */
public record StoredReceipt(
        long id,
        Long userId,
        Instant createdAt,
        LocalDateTime purchasedAt,
        ReceiptStatus status,
        String reason,
        String storeName
) {
}
