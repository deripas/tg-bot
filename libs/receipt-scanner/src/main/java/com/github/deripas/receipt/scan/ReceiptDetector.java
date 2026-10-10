package com.github.deripas.receipt.scan;

import com.github.deripas.image.ImageData;

import java.awt.Polygon;
import java.util.Optional;

/**
 * Finds the receipt borders on a photo.
 */
@FunctionalInterface
public interface ReceiptDetector {

    /**
     * @return 4 corners ordered as top-left, top-right, bottom-right, bottom-left, or empty if not found
     */
    Optional<Polygon> detect(ImageData image);
}
