package com.github.deripas.receipt;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Gets the receipt from its photo. Implementations: by the fiscal QR code from the tax service,
 * with a vision model, or one of them with the other as a fallback; see {@link ReceiptParsers}.
 */
@FunctionalInterface
public interface ReceiptParser {

    /**
     * @param image   photo of a receipt
     * @param country country of the receipt: its currency and the words its totals are printed with
     * @return parsed receipt, with {@link Receipt#errors()} if something does not add up
     * @throws IOException if the receipt cannot be read, e.g. the model is not available
     */
    Receipt parse(BufferedImage image, Country country) throws IOException;
}
