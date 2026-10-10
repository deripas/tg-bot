package com.github.deripas.tgbot.receipt.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Чек из БД; {@code items} пустой в списках, заполнен в карточке. */
public record StoredReceipt(
        long id,
        String store,
        String taxId,
        String dateTimeRaw,
        LocalDateTime purchasedAt,
        BigDecimal total,
        String paymentMethod,
        BigDecimal vatAmount,
        String receiptNumber,
        String model,
        Instant createdAt,
        List<Item> items) {

    public record Item(int position, String name, BigDecimal price, BigDecimal quantity, BigDecimal total) {
    }
}
