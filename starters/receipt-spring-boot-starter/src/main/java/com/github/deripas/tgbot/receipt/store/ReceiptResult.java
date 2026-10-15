package com.github.deripas.tgbot.receipt.store;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Result of processing a receipt.
 *
 * @param status      {@link ReceiptStatus#SUCCESS}, {@link ReceiptStatus#WARNING} or {@link ReceiptStatus#ERROR}
 * @param reason      what a human should check, {@code null} if nothing
 * @param storeName   seller, {@code null} if not read
 * @param purchasedAt date and time printed on the receipt, {@code null} if not read
 * @param total       total amount, {@code null} if not read
 */
public record ReceiptResult(
        ReceiptStatus status,
        String reason,
        String storeName,
        LocalDateTime purchasedAt,
        BigDecimal total
) {

    public static ReceiptResult error(String reason) {
        return new ReceiptResult(ReceiptStatus.ERROR, reason, null, null, null);
    }
}
