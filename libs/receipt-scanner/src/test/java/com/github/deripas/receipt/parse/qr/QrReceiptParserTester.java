package com.github.deripas.receipt.parse.qr;

import com.github.deripas.image.ImageUtils;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.parse.qr.rs.SerbijaQrReceiptLoader;
import lombok.SneakyThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads a receipt from the real tax service by the link of its QR code or by a photo with the code.
 */
public class QrReceiptParserTester {

    @SneakyThrows
    static void main(String[] args) {
        final SerbijaQrReceiptLoader loader = new SerbijaQrReceiptLoader();
        final QrReceiptParser parser = new QrReceiptParser(List.of(loader));
        for (String arg : args) {
            final Receipt receipt = arg.startsWith("https://")
                    ? loader.load(arg)
                    : parser.parse(ImageUtils.load(Files.readAllBytes(Path.of(arg))), Country.SERBIA);
            System.out.printf("%s, %s, total %s, valid %s, errors %s%n", receipt.store(), receipt.dateTime(),
                    receipt.total(), receipt.valid(), receipt.errors());
            receipt.items().forEach(item -> System.out.printf("  %s: %s x %s = %s%n",
                    item.name(), item.price(), item.quantity(), item.total()));
        }
    }
}
