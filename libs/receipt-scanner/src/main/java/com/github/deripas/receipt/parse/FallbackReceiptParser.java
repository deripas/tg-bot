package com.github.deripas.receipt.parse;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Parses the receipt the reliable way if it can, the general way otherwise: e.g. by the fiscal QR code,
 * and with a model if the code is not readable in the photo.
 */
@Slf4j
@RequiredArgsConstructor
public class FallbackReceiptParser implements ReceiptParser {

    private final ReceiptParser primary;
    private final ReceiptParser fallback;

    @Override
    public Receipt parse(BufferedImage image, Country country) throws IOException {
        try {
            return primary.parse(image, country);
        } catch (IOException e) {
            log.info("Parsing the receipt the general way: {}", e.getMessage());
            return fallback.parse(image, country);
        }
    }
}
