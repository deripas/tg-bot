package com.github.deripas.receipt.parse;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.read.ReceiptText;

import java.util.function.BiFunction;

/**
 * Parses the text of a receipt into a {@link Receipt}; works without the image, so a stored text can be parsed again.
 */
@FunctionalInterface
public interface ReceiptParserFunction extends BiFunction<ReceiptText, Country, Receipt> {
}
