package com.github.deripas.receipt;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Receipt(
        @JsonPropertyDescription("Items sum up to the printed total and no problems were found")
        boolean valid,
        @JsonPropertyDescription("Seller name exactly as printed on the receipt")
        String store,
        @JsonPropertyDescription("Receipt date and time")
        LocalDateTime dateTime,
        @JsonPropertyDescription("Purchased items")
        List<Item> items,
        @JsonPropertyDescription("Total amount paid")
        BigDecimal total,
        @JsonPropertyDescription("Pricing currency")
        Currency currency,
        @JsonPropertyDescription("What the receipt was made from: the text read by the model or the receipt of the tax service")
        String json,
        @JsonPropertyDescription("Problems a human should check")
        List<String> errors
) {
    public record Item(
            @JsonPropertyDescription("Item name as printed")
            String name,
            @JsonPropertyDescription("Unit price")
            BigDecimal price,
            @JsonPropertyDescription("Quantity")
            BigDecimal quantity,
            @JsonPropertyDescription("Line total")
            BigDecimal total
    ) {
    }
}
