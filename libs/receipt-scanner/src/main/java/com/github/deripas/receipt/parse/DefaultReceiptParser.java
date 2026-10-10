package com.github.deripas.receipt.parse;

import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.receipt.read.ReceiptReader;
import com.github.deripas.receipt.read.ReceiptText;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Parses the text read by the {@link ReceiptReader}: numbers and dates, merges overlapping windows
 * and checks that the items sum up to the printed total. The text is kept in {@link Receipt#json()},
 * so that a receipt can be parsed again without reading the photo.
 */
@RequiredArgsConstructor
public class DefaultReceiptParser implements ReceiptParser {

    private final ReceiptReader reader;
    private final ReceiptParserFunction parser;

    public DefaultReceiptParser(ReceiptReader reader) {
        this(reader, new DefaultReceiptParserFunction());
    }

    @Override
    public Receipt parse(BufferedImage image, Country country) throws IOException {
        final ReceiptText text = reader.read(ImageData.of(image), country);
        return parser.apply(text, country);
    }
}
