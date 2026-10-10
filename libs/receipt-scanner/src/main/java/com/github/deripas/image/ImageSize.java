package com.github.deripas.image;

/**
 * Size of an image in pixels; named so to differ from {@code org.opencv.core.Size} and the {@code @Size} constraint.
 */
public record ImageSize(int width, int height) {

    public long area() {
        return (long) width * height;
    }
}
