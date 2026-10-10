package com.github.deripas.image.function;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.opencv.core.Mat;

import java.util.function.Function;

/**
 * Cuts out an axis-aligned rectangle without resampling; the result shares the pixels with the input.
 * A negative width or height means up to the right or bottom edge.
 */
@ToString
@RequiredArgsConstructor
public class RectangleMatFunction implements MatFunction {

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        final int right = width < 0 ? in.cols() : x + width;
        final int bottom = height < 0 ? in.rows() : y + height;
        validate(in, right, bottom);

        // The submat holds a reference to the pixels of the input, so it is released as well
        return withMat(in.submat(y, bottom, x, right), submat -> response(submat, function));
    }

    private void validate(Mat in, int right, int bottom) {
        if (x < 0 || y < 0 || right > in.cols() || bottom > in.rows() || right <= x || bottom <= y) {
            throw new IllegalArgumentException("Rectangle [" + x + ", " + y + ", " + right + ", " + bottom
                    + ") is outside of the image " + in.cols() + "x" + in.rows());
        }
    }
}
