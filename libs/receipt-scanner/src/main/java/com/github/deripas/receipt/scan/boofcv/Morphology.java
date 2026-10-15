package com.github.deripas.receipt.scan.boofcv;

import boofcv.struct.image.GrayU8;

/**
 * Closing and opening of a binary image (pixels 0 and 1) with a square kernel of any size, in time independent
 * of the kernel size: a pixel is set by dilation if any pixel of its window is set, and by erosion if all are,
 * counted with an integral image. The window is clipped by the image border, so pixels outside do not count,
 * as in OpenCV.
 */
class Morphology {

    private final int width;
    private final int height;
    private final int[] integral;
    private final GrayU8 buffer;

    Morphology(int width, int height) {
        this.width = width;
        this.height = height;
        this.integral = new int[(width + 1) * (height + 1)];
        this.buffer = new GrayU8(width, height);
    }

    /**
     * Dilation, then erosion: fills gaps narrower than the kernel.
     *
     * @param size side of the kernel
     */
    void close(GrayU8 image, int size) {
        apply(image, buffer, size, false);
        apply(buffer, image, size, true);
    }

    /**
     * Erosion, then dilation: removes parts narrower than the kernel.
     *
     * @param size side of the kernel
     */
    void open(GrayU8 image, int size) {
        apply(image, buffer, size, true);
        apply(buffer, image, size, false);
    }

    /**
     * The anchor is the center of the kernel, for an even size the window has one pixel more before it.
     */
    private void apply(GrayU8 input, GrayU8 output, int size, boolean erode) {
        final int before = size / 2;
        final int after = size - 1 - before;
        final int stride = width + 1;
        for (int y = 0; y < height; y++) {
            int row = 0;
            for (int x = 0; x < width; x++) {
                row += input.unsafe_get(x, y) != 0 ? 1 : 0;
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1] + row;
            }
        }
        for (int y = 0; y < height; y++) {
            final int top = Math.max(0, y - before);
            final int bottom = Math.min(height, y + after + 1);
            for (int x = 0; x < width; x++) {
                final int left = Math.max(0, x - before);
                final int right = Math.min(width, x + after + 1);
                final int set = integral[bottom * stride + right] - integral[top * stride + right]
                        - integral[bottom * stride + left] + integral[top * stride + left];
                final boolean on = erode ? set == (bottom - top) * (right - left) : set > 0;
                output.unsafe_set(x, y, on ? 1 : 0);
            }
        }
    }
}
