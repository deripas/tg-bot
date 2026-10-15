package com.github.deripas.receipt.parse.qr;

import com.github.deripas.receipt.Receipt;

import java.io.IOException;

/**
 * Loads a receipt from the tax service of a country by its fiscal QR code.
 */
public interface QrReceiptLoader {

    /**
     * @param code text of a QR code {@link #isSupported(String) supported} by the loader
     * @throws IOException if the service does not give the receipt, e.g. it is not available
     */
    Receipt load(String code) throws IOException;

    /**
     * @param code text of any QR code in the photo
     * @return whether the code is a receipt link of the service
     */
    boolean isSupported(String code);
}
