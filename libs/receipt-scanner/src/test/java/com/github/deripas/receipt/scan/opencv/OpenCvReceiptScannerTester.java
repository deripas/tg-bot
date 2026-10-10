package com.github.deripas.receipt.scan.opencv;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;

public class OpenCvReceiptScannerTester {

    @SneakyThrows
    static void main() {
        final ReceiptScanner scanner = new DefaultReceiptScanner(new OpenCvReceiptDetector());
        final ImageData result = scanner.scan(TestImages.readData("/3/PXL_20261011_095819059.jpg"));
        ImageIO.write(result.toImage(), "jpg", new java.io.File("result.jpg"));
    }
}
