package com.github.deripas.receipt.scan;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.Rotate;
import com.github.deripas.tesseract.TesseractOrientation;
import lombok.extern.slf4j.Slf4j;

import java.awt.Polygon;
import java.util.Optional;

/**
 * Scans a receipt found by the {@link ReceiptDetector}:
 * <ol>
 *     <li>turns the photo vertical: a receipt is shot on a vertical frame or as a panorama along it, so on a vertical
 *     frame the receipt is vertical too, and the detector gets only vertical frames;</li>
 *     <li>cuts the receipt out with a perspective transform and converts it to grayscale;</li>
 *     <li>turns it by 180 degrees if Tesseract finds the text upside down.</li>
 * </ol>
 * A horizontal receipt on a vertical frame is not expected: it is logged, and Tesseract chooses any quarter turn.
 * Detectors differ in the way the receipt borders are found: OpenCV is fast and offline and straightens a tilted
 * receipt by its 4 corners, a vision model works on a busy background but finds an axis-aligned box.
 */
@Slf4j
public class DefaultReceiptScanner implements ReceiptScanner {

    private final ReceiptDetector detector;
    private final TesseractOrientation orientation;

    public DefaultReceiptScanner(ReceiptDetector detector) {
        this(detector, TesseractOrientation.shared());
    }

    public DefaultReceiptScanner(ReceiptDetector detector, TesseractOrientation orientation) {
        this.detector = detector;
        this.orientation = orientation;
    }

    @Override
    public ImageData scan(ImageData image) {
        try {
            final ImageData frame = image.transform()
                    .vertical()
                    .asImage();
            final Optional<Polygon> corners = detector.detect(frame);
            if (corners.isEmpty()) {
                log.warn("Receipt not found");
                return image;
            }
            final ImageData receipt = frame.transform()
                    .crop(corners.get())
                    .grayscale()
                    .asImage();
            return receipt.transform()
                    .rotate(uprightTurns(receipt))
                    .asImage();
        } catch (Exception e) {
            log.error("Error scanning receipt", e);
            return image;
        }
    }

    /**
     * @return clockwise rotation that makes the text upright
     */
    private Rotate uprightTurns(ImageData receipt) {
        final Optional<Rotate> turns = orientation.uprightTurns(receipt);
        if (!receipt.isVertical()) {
            log.warn("Receipt {}x{} is horizontal on a vertical frame", receipt.getWidth(), receipt.getHeight());
            if (turns.isEmpty()) {
                log.warn("Text orientation is not found, turning the receipt vertical");
                return Rotate.ROTATE_90_CLOCKWISE;
            }
            return turns.get();
        }
        if (turns.isEmpty()) {
            log.warn("Text orientation is not found, keeping the receipt as is");
            return Rotate.ROTATE_NONE;
        }
        // A vertical receipt cannot have text on its side; such an answer is a mistake
        if (turns.get() == Rotate.ROTATE_90_CLOCKWISE || turns.get() == Rotate.ROTATE_90_COUNTERCLOCKWISE) {
            log.warn("Vertical receipt is reported to have text on its side, keeping it as is");
            return Rotate.ROTATE_NONE;
        }
        return turns.get();
    }
}
