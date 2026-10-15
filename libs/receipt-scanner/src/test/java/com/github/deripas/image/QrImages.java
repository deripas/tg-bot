package com.github.deripas.image;

import lombok.experimental.UtilityClass;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.QRCodeEncoder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Images of QR codes for tests.
 */
@UtilityClass
public class QrImages {

    static {
        Loader.load(opencv_java.class);
    }

    /**
     * @return the code of the text, 8 px per module, on a white field
     */
    public static BufferedImage of(String text) {
        final Mat code = new Mat();
        final Mat scaled = new Mat();
        final Mat framed = new Mat();
        try {
            QRCodeEncoder.create().encode(text, code);
            Imgproc.resize(code, scaled, new Size(), 8, 8, Imgproc.INTER_NEAREST);
            Core.copyMakeBorder(scaled, framed, 200, 200, 200, 200, Core.BORDER_CONSTANT, new Scalar(255));
            final MatOfByte png = new MatOfByte();
            Imgcodecs.imencode(".png", framed, png);
            return ImageUtils.load(png.toArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            code.release();
            scaled.release();
            framed.release();
        }
    }
}
