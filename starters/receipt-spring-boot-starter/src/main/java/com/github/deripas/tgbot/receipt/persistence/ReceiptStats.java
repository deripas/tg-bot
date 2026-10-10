package com.github.deripas.tgbot.receipt.persistence;

import java.math.BigDecimal;

/** Строка статистики: период или магазин, число чеков и сумма. */
public record ReceiptStats(String label, long count, BigDecimal total) {
}
