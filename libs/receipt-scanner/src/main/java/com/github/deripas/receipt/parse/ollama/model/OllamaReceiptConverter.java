package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Converts what the model read from the windows into a receipt: parses numbers and dates, merges overlapping
 * windows and checks that the items sum up to the printed total; what does not add up goes to
 * {@link Receipt#errors()}. Works without the model, so a receipt kept in {@link Receipt#json()} can be
 * converted again.
 */
public class OllamaReceiptConverter {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public Receipt apply(OllamaReceipt text, Country country) {
        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(text.windows(), country);
        final List<String> errors = assembly.warnings();
        final List<Receipt.Item> items = assembly.items().stream()
                .map(OllamaReceiptConverter::convertItem)
                .toList();
        return new Receipt(
                assembly.balanced() && errors.isEmpty(),
                assembly.store(),
                assembly.dateTime(),
                items,
                money(assembly.total()),
                country.getCurrency(),
                MAPPER.writeValueAsString(text),
                errors
        );
    }

    private static Receipt.Item convertItem(ParsedItem item) {
        return new Receipt.Item(item.name(), money(item.price()), quantity(item.quantity()), money(item.total()));
    }

    private static BigDecimal money(Double value) {
        return value == null
                ? null
                : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal quantity(Double value) {
        return value == null
                ? null
                : BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }
}
