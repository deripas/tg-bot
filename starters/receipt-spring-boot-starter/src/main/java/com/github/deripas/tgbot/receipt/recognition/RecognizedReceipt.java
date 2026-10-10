package com.github.deripas.tgbot.receipt.recognition;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Ответ модели: из этого record строится JSON-схема для Ollama {@code format}. */
public record RecognizedReceipt(
        @JsonPropertyDescription("Seller name exactly as printed on the receipt")
        String store,
        @JsonPropertyDescription("Seller tax name (INN, PIB, VAT number)")
        String taxId,
        @JsonPropertyDescription("Receipt date and time as printed")
        String dateTime,
        @JsonPropertyDescription("Purchased items")
        List<Item> items,
        @JsonPropertyDescription("Total amount paid")
        Double total,
        @JsonPropertyDescription("Payment method: cash, card, etc.")
        String paymentMethod,
        @JsonPropertyDescription("Total tax amount (VAT, PDV)")
        Double vatAmount,
        @JsonPropertyDescription("Fiscal receipt number")
        String receiptNumber) {

    public List<Item> items() {
        return items == null ? List.of() : items;
    }

    public record Item(
            @JsonPropertyDescription("Item name without barcode")
            String name,
            @JsonPropertyDescription("Unit price")
            Double price,
            @JsonPropertyDescription("Quantity")
            Double quantity,
            @JsonPropertyDescription("Line total")
            Double total) {
    }
}
