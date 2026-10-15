package com.github.deripas.receipt.parse.ollama.model;


import com.github.deripas.receipt.Country;

import java.util.List;
import java.util.Locale;

/**
 * Finds the total, the tax total and the payment method among the printed totals lines
 * by the keywords of the country.
 *
 * @param total         amount to pay; the paid amount if the total line is missing
 * @param taxTotal      total tax amount
 * @param paymentMethod payment line without the amount, e.g. "Платна картица"
 */
record ReceiptTotals(Double total, Double taxTotal, String paymentMethod) {

    static ReceiptTotals parse(List<String> lines, Country.Keywords keywords, char decimalSeparator) {
        Double total = null;
        Double taxTotal = null;
        Double paid = null;
        String paymentMethod = null;

        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            final String lower = line.toLowerCase(Locale.ROOT);
            final Double amount = ReceiptNumbers.lastNumber(line, decimalSeparator).orElse(null);
            final boolean tax = contains(lower, keywords.tax());

            if (contains(lower, keywords.total())) {
                if (tax && taxTotal == null) {
                    taxTotal = amount;
                } else if (!tax && total == null) {
                    total = amount;
                }
            } else if (contains(lower, keywords.payment()) && paymentMethod == null) {
                paymentMethod = line.replaceAll("[\\s:]*-?\\d[\\d.,\\s]*$", "").strip();
                paid = amount;
            }
        }

        return new ReceiptTotals(
                total != null ? total : paid,
                taxTotal,
                paymentMethod == null || paymentMethod.isEmpty() ? null : paymentMethod
        );
    }

    private static boolean contains(String line, List<String> keywords) {
        return keywords.stream().anyMatch(line::contains);
    }
}
