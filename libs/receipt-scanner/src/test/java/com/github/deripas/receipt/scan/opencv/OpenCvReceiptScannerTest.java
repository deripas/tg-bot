package com.github.deripas.receipt.scan.opencv;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCvReceiptScannerTest {

    private final ReceiptScanner scanner = new DefaultReceiptScanner(new OpenCvReceiptDetector());

    @ParameterizedTest
    @ValueSource(strings = {
            "1/IMG_4177.jpg",
            "2/IMG_4189.jpg",
            "3/PXL_20261011_095819059.jpg"
    })
    void testScanReturnsVerticalReceipt(String path) {
        final ImageData image = TestImages.readData(path);

        final ImageData result = scanner.scan(image);
        assertThat(result.isGray()).isTrue();
        assertThat(result.getHeight()).isGreaterThan(result.getWidth());
        // the receipt is cut out, not the whole photo returned
        assertThat((long) result.getWidth() * result.getHeight()).isLessThan((long) image.getWidth() * image.getHeight());
    }
}
