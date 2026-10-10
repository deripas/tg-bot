package com.github.deripas.receipt.scan;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.Rotate;
import com.github.deripas.tesseract.TesseractOrientation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Checks how the scanner turns the receipt by the answer of Tesseract, with the detector and Tesseract mocked;
 * scanning real photos is checked by the tests of the detectors.
 */
@ExtendWith(MockitoExtension.class)
class DefaultReceiptScannerTest {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 400;
    private static final int MARK = 20;

    @Mock
    private ReceiptDetector detector;
    @Mock
    private TesseractOrientation orientation;
    @InjectMocks
    private DefaultReceiptScanner scanner;

    @Test
    void photoIsKeptWhenReceiptNotFound() {
        final ImageData photo = photo();
        when(detector.detect(any())).thenReturn(Optional.empty());

        final ImageData result = scanner.scan(photo);

        assertThat(result).isSameAs(photo);
        verifyNoInteractions(orientation);
    }

    @Test
    void upsideDownReceiptIsTurned() {
        detect(rectangle(WIDTH, HEIGHT));
        when(orientation.uprightTurns(any())).thenReturn(Optional.of(Rotate.ROTATE_180));

        final BufferedImage receipt = scanner.scan(photo()).toImage();

        assertThat(receipt.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
        assertThat(markedAtTopLeft(receipt)).isFalse();
        assertThat(isWhite(receipt, receipt.getWidth() - MARK / 2, receipt.getHeight() - MARK / 2)).isTrue();
    }

    @Test
    void verticalReceiptIsNotTurnedOnItsSide() {
        detect(rectangle(WIDTH, HEIGHT));
        when(orientation.uprightTurns(any())).thenReturn(Optional.of(Rotate.ROTATE_90_CLOCKWISE));

        final BufferedImage receipt = scanner.scan(photo()).toImage();

        assertThat(receipt.getHeight()).isGreaterThan(receipt.getWidth());
        assertThat(markedAtTopLeft(receipt)).isTrue();
    }

    @Test
    void receiptIsKeptWhenOrientationNotFound() {
        detect(rectangle(WIDTH, HEIGHT));
        when(orientation.uprightTurns(any())).thenReturn(Optional.empty());

        final BufferedImage receipt = scanner.scan(photo()).toImage();

        assertThat(markedAtTopLeft(receipt)).isTrue();
    }

    @Test
    void horizontalReceiptIsTurnedVertical() {
        detect(rectangle(WIDTH, HEIGHT / 4));
        when(orientation.uprightTurns(any())).thenReturn(Optional.empty());

        final BufferedImage receipt = scanner.scan(photo()).toImage();

        assertThat(receipt.getHeight()).isGreaterThan(receipt.getWidth());
    }

    @Test
    void horizontalReceiptWithUprightTextIsKept() {
        detect(rectangle(WIDTH, HEIGHT / 4));
        when(orientation.uprightTurns(any())).thenReturn(Optional.of(Rotate.ROTATE_NONE));

        final BufferedImage receipt = scanner.scan(photo()).toImage();

        assertThat(receipt.getWidth()).isGreaterThan(receipt.getHeight());
    }

    private void detect(Polygon corners) {
        when(detector.detect(any())).thenReturn(Optional.of(corners));
    }

    /**
     * @return black vertical photo with a white mark in the top left corner
     */
    private static ImageData photo() {
        final BufferedImage photo = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        final Graphics2D graphics = photo.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, MARK, MARK);
        graphics.dispose();
        return ImageData.of(photo);
    }

    private static Polygon rectangle(int width, int height) {
        return new Polygon(new int[]{0, width - 1, width - 1, 0}, new int[]{0, 0, height - 1, height - 1}, 4);
    }

    private static boolean markedAtTopLeft(BufferedImage image) {
        return isWhite(image, MARK / 2, MARK / 2);
    }

    private static boolean isWhite(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) & 0xFF) > 128;
    }
}
