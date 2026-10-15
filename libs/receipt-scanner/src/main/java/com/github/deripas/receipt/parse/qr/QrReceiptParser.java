package com.github.deripas.receipt.parse.qr;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.QrCodes;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.io.InterruptedIOException;
import java.util.ArrayList;

/**
 * Loads the receipt by its fiscal QR code from the tax service, see {@link QrReceiptLoader}: the items, the total
 * and the time exactly as the service has them, nothing is read from the photo but the codes. The first code
 * a loader supports and loads the receipt by wins, so other codes in the photo, e.g. of the shop, do not matter.
 * <p>
 * Fails if no receipt is loaded: the photo has no readable fiscal code, e.g. the code is too small or blurred,
 * or the service does not give the receipt; the reasons of the loaders are in the message.
 */
public class QrReceiptParser implements ReceiptParser {

    private final List<QrReceiptLoader> loaders;

    /**
     * @param loaders loaders of the tax services, e.g. of the countries the receipts come from
     */
    public QrReceiptParser(List<QrReceiptLoader> loaders) {
        this.loaders = loaders;
    }

    @Override
    public Receipt parse(BufferedImage image, Country country) throws IOException {
        final List<String> codes = QrCodes.decode(ImageData.of(image));
        final List<IOException> failures = new ArrayList<>();
        for (String code : codes) {
            for (QrReceiptLoader loader : loaders) {
                if (!loader.isSupported(code)) {
                    continue;
                }
                try {
                    return loader.load(code);
                } catch (InterruptedIOException e) {
                    throw e;
                } catch (IOException e) {
                    failures.add(e);
                }
            }
        }
        throw failure(codes.size(), failures);
    }

    private static IOException failure(int codes, List<IOException> failures) {
        final StringBuilder message = new StringBuilder("No receipt loaded by the QR codes in the photo: ").append(codes);
        failures.forEach(failure -> message.append("; ").append(failure.getMessage()));
        final IOException exception = new IOException(message.toString());
        failures.forEach(exception::addSuppressed);
        return exception;
    }
}
