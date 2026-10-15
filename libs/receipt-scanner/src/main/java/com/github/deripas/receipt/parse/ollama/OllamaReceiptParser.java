package com.github.deripas.receipt.parse.ollama;

import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.receipt.parse.ollama.model.OllamaReceipt;
import com.github.deripas.receipt.parse.ollama.model.OllamaReceiptConverter;
import com.github.deripas.receipt.parse.ollama.model.OllamaReceiptReader;
import lombok.RequiredArgsConstructor;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Reads the receipt with a vision model, see {@link OllamaReceiptReader}, and converts what was read into
 * a receipt, see {@link OllamaReceiptConverter}: parses numbers and dates, merges overlapping windows
 * and checks that the items sum up to the printed total. What was read is kept in {@link Receipt#json()},
 * so that a receipt can be converted again without reading the photo.
 */
@RequiredArgsConstructor
public class OllamaReceiptParser implements ReceiptParser {

    private final OllamaReceiptReader reader;
    private final OllamaReceiptConverter converter;

    public OllamaReceiptParser(OllamaReceiptReader reader) {
        this(reader, new OllamaReceiptConverter());
    }

    @Override
    public Receipt parse(BufferedImage image, Country country) throws IOException {
        final OllamaReceipt read = reader.read(ImageData.of(image), country);
        return converter.apply(read, country);
    }
}
