package com.github.deripas.image;

import com.github.deripas.image.function.CompositeMatFunction;
import com.github.deripas.image.function.CropMatFunction;
import com.github.deripas.image.function.GrayMatFunction;
import com.github.deripas.image.function.MatFunction;
import com.github.deripas.image.function.RectangleMatFunction;
import com.github.deripas.image.function.ResizeMatFunction;
import com.github.deripas.image.function.RotateMatFunction;
import com.github.deripas.image.function.VerticalMatFunction;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;

import java.awt.Polygon;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Deferred transformation of an {@link ImageData}: the steps are only collected, and run in the order they were
 * added when the result is taken by {@link #asImage()}, {@link #toJpeg()} or {@link #as(Function)}. OpenCV images
 * exist only during that call and are released at its end, so callers never manage native memory.
 * <p>
 * A step decides by the image it gets from the step before, e.g. {@link #vertical()} after a crop looks at the
 * cropped image. A transform is immutable and can be continued in several ways; every result runs all its steps
 * again from the source, so a result needed several times should be taken once with {@link #asImage()}.
 */
@ToString
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ImageTransform {

    /**
     * Quality of the ImageIO JPEG writer by default, kept from the first version.
     */
    private static final int JPEG_QUALITY = 75;

    static {
        Loader.load(opencv_java.class);
    }

    private final ImageData image;
    private final List<MatFunction> steps;

    ImageTransform(ImageData image) {
        this(image, List.of());
    }

    /**
     * Turns a horizontal image clockwise; a vertical one is kept as is.
     */
    public ImageTransform vertical() {
        return then(new VerticalMatFunction());
    }

    public ImageTransform rotate(Rotate rotate) {
        if (rotate == Rotate.ROTATE_NONE) {
            return this;
        }
        return then(new RotateMatFunction(rotate));
    }

    /**
     * Converts a BGR image to gray; a gray one is kept as is.
     */
    public ImageTransform grayscale() {
        return then(new GrayMatFunction());
    }

    /**
     * Scales the image keeping the aspect ratio, by the size it has at this step.
     */
    public ImageTransform scale(double scale) {
        if (scale == 1) {
            return this;
        }
        return then(ResizeMatFunction.scale(scale));
    }

    /**
     * Area averaging when shrinking keeps thin strokes of small print; bicubic when enlarging.
     */
    public ImageTransform resize(int width, int height) {
        return resize(width, height, ResizeMatFunction.Interpolation.AUTO);
    }

    /**
     * @param interpolation OpenCV {@code Imgproc.INTER_*}
     */
    public ImageTransform resize(int width, int height, ResizeMatFunction.Interpolation interpolation) {
        return then(ResizeMatFunction.size(width, height, interpolation));
    }

    /**
     * Crops the quadrilateral and maps it onto a straight rectangle; the corners may come in any order.
     */
    public ImageTransform crop(Polygon corners) {
        return then(new CropMatFunction(corners));
    }

    /**
     * @return rows {@code [top, bottom)} of the full width, without resampling
     */
    public ImageTransform cropRows(int top, int bottom) {
        return cropRectangle(0, top, -1, bottom - top);
    }

    /**
     * @return the rectangle, without resampling; a negative width or height means up to the edge
     */
    public ImageTransform cropRectangle(int x, int y, int width, int height) {
        return then(new RectangleMatFunction(x, y, width, height));
    }

    /**
     * @return the result; the source itself if there are no steps, since images are immutable
     */
    public ImageData asImage() {
        if (steps.isEmpty()) {
            return image;
        }
        return as(ImageTransform::image);
    }

    public byte[] toJpeg() {
        return as(ImageTransform::jpeg);
    }

    /**
     * Runs the steps and passes the result to the {@code extractor}.
     *
     * @param extractor must not keep, modify or release the image: it is released after the call
     */
    public <T> T as(@NonNull Function<Mat, T> extractor) {
        final CompositeMatFunction function = new CompositeMatFunction(steps);
        final Mat mat = mat(image);
        try {
            return function.apply(mat, extractor);
        } finally {
            mat.release();
        }
    }

    private ImageTransform then(MatFunction function) {
        final List<MatFunction> list = new ArrayList<>(steps.size() + 1);
        list.addAll(steps);
        list.add(function);
        return new ImageTransform(image, List.copyOf(list));
    }

    private static ImageData image(Mat mat) {
        final Mat continuous = mat.isContinuous() ? mat : mat.clone();
        try {
            final byte[] bytes = new byte[(int) (continuous.total() * continuous.channels())];
            continuous.get(0, 0, bytes);
            return new ImageData(new ImageSize(continuous.cols(), continuous.rows()), type(continuous), bytes);
        } finally {
            if (continuous != mat) {
                continuous.release();
            }
        }
    }

    private static byte[] jpeg(Mat mat) {
        final MatOfByte jpeg = new MatOfByte();
        final MatOfInt params = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, JPEG_QUALITY);
        try {
            if (!Imgcodecs.imencode(".jpg", mat, jpeg, params)) {
                throw new UncheckedIOException(new IOException("Failed to encode JPEG"));
            }
            return jpeg.toArray();
        } finally {
            jpeg.release();
            params.release();
        }
    }

    private static Mat mat(ImageData image) {
        final Mat mat = new Mat(image.getHeight(), image.getWidth(), convert(image.getType()));
        mat.put(0, 0, image.getBytes());
        return mat;
    }

    private static int convert(ImageDataType type) {
        return switch (type) {
            case TYPE_3BYTE_BGR -> CvType.CV_8UC3;
            case TYPE_BYTE_GRAY -> CvType.CV_8UC1;
        };
    }

    private static ImageDataType type(Mat mat) {
        if (mat.type() == CvType.CV_8UC3) {
            return ImageDataType.TYPE_3BYTE_BGR;
        }
        if (mat.type() == CvType.CV_8UC1) {
            return ImageDataType.TYPE_BYTE_GRAY;
        }
        throw new IllegalArgumentException("Unsupported Mat type: " + CvType.typeToString(mat.type()));
    }
}
