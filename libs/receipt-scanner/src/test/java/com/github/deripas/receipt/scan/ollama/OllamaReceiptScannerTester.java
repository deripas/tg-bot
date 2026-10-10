package com.github.deripas.receipt.scan.ollama;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;

public class OllamaReceiptScannerTester {

    @SneakyThrows
    static void main() {
        final ReceiptScanner scanner = new DefaultReceiptScanner(new OllamaReceiptDetector());
        final ImageData scan = scanner.scan(TestImages.readData("/1/IMG_4177.jpg"));
        ImageIO.write(scan.toImage(), "jpg", new java.io.File("result.jpg"));
    }
}
