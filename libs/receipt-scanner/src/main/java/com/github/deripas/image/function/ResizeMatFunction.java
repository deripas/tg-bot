package com.github.deripas.image.function;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.function.Function;

/**
 * Resizes to a fixed size or by a factor of the size the image has at this step.
 */
@ToString
@RequiredArgsConstructor
public class ResizeMatFunction implements MatFunction {

    /**
     * Interpolation chosen by the direction: area averaging when shrinking keeps thin strokes of small print,
     * bicubic when enlarging.
     */
    public static final int AUTO = -1;

    private final int width;
    private final int height;
    private final double scale;
    private final Interpolation interpolation;

    public static ResizeMatFunction size(int width, int height, Interpolation interpolation) {
        return new ResizeMatFunction(width, height, 0, interpolation);
    }

    public static ResizeMatFunction scale(double scale) {
        return new ResizeMatFunction(0, 0, scale, Interpolation.AUTO);
    }

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        final int targetWidth = scale > 0 ? Math.max(1, (int) Math.round(in.cols() * scale)) : width;
        final int targetHeight = scale > 0 ? Math.max(1, (int) Math.round(in.rows() * scale)) : height;
        if (targetWidth == in.cols() && targetHeight == in.rows()) {
            return response(in, function);
        }

        final Interpolation auto = (long) targetWidth * targetHeight < in.total()
                ? Interpolation.INTER_AREA
                : Interpolation.INTER_CUBIC;
        final Interpolation mode = interpolation == Interpolation.AUTO
                ? auto
                : interpolation;
        final int method = code(mode);

        return withMat(out -> {
            Imgproc.resize(in, out, new Size(targetWidth, targetHeight), 0, 0, method);
            return response(out, function);
        });
    }

    private int code(Interpolation interpolation) {
        return switch (interpolation) {
            case INTER_AREA -> Imgproc.INTER_AREA;
            case INTER_CUBIC -> Imgproc.INTER_CUBIC;
            default -> throw new IllegalArgumentException("Unknown interpolation mode: " + interpolation);
        };
    }

    public enum Interpolation {
        AUTO,
        INTER_AREA,
        INTER_CUBIC
    }
}
