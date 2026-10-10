package com.github.deripas.receipt.read;

import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.Country;

import java.io.IOException;

/**
 * Reads the text of a receipt from a photo as printed, without parsing it.
 */
public interface ReceiptReader {

    /**
     * @param image   photo of a receipt
     * @param country country of the receipt
     * @throws IOException if the text cannot be read, e.g. the model is not available
     */
    ReceiptText read(ImageData image, Country country) throws IOException;
}
