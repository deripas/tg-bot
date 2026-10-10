package com.github.deripas.receipt.scan;

import com.github.deripas.image.ImageData;

@FunctionalInterface
public interface ReceiptScanner {

    /**
     * @param image photo
     * @return the receipt cut out of the photo, straightened, upright and in grayscale;
     * the photo itself if the receipt is not found
     */
    ImageData scan(ImageData image);
}
