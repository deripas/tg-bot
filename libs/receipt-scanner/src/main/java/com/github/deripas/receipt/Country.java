package com.github.deripas.receipt;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Country of a receipt: its currency and the words its receipts are printed with.
 */
@Getter
@RequiredArgsConstructor
public enum Country {

    SERBIA("Serbia", Currency.RSD, ',', new Keywords(
            List.of("укупан износ", "укупно", "ukupan iznos", "ukupno", "za uplatu", "total"),
            // "ореза" also matches "нореза": the model misreads п as н
            List.of("ореза", "порез", "пдв", "porez", "pdv", "tax"),
            List.of("картица", "kartica", "готовина", "gotovina", "card", "cash"))),
    ;

    private final String name;
    private final Currency currency;
    /**
     * Decimal separator of amounts, used when it cannot be detected from the amounts printed on the receipt.
     */
    private final char decimalSeparator;
    private final Keywords keywords;

    /**
     * Lower case words that tell the lines after the items apart; a line matches if it contains a word.
     *
     * @param total   total amount to pay; a line that also matches {@code tax} is the total tax amount
     * @param tax     taxes
     * @param payment payment method
     */
    public record Keywords(
            List<String> total,
            List<String> tax,
            List<String> payment
    ) {
    }
}
