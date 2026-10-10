package com.github.deripas.receipt.parse;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.read.ReceiptText;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class DefaultReceiptParserFunction implements ReceiptParserFunction {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Override
    public Receipt apply(ReceiptText text, Country country) {
        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(text.windows(), country);
        final List<String> errors = assembly.warnings();
        final List<Receipt.Item> items = assembly.items().stream()
                .map(DefaultReceiptParserFunction::convertItem)
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
