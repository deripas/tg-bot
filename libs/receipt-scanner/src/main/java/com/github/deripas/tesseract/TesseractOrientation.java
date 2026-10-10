package com.github.deripas.tesseract;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.Rotate;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.PointerPointer;
import org.bytedeco.tesseract.TessBaseAPI;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static org.bytedeco.tesseract.global.tesseract.PSM_OSD_ONLY;

/**
 * Finds the direction of the text with Tesseract orientation and script detection (OSD). Works on the receipt
 * cut out of the photo, in any of the 4 quarter turns; on a whole photo the background confuses it.
 * <p>
 * Calls are synchronized: Tesseract is not thread-safe, and a call takes tens to hundreds of milliseconds.
 */
@Slf4j
public class TesseractOrientation implements AutoCloseable {

    /**
     * Lower confidence is treated as not found. All receipts we tested scored from 1.8;
     * a wrong answer on a small part of a receipt scored 1.3.
     */
    static final float MIN_CONFIDENCE = 1.5f;

    /**
     * Photos carry no resolution, and Tesseract falls back to 70 dpi with a warning; set it to avoid the warning.
     */
    private static final int RESOLUTION = 70;

    /**
     * The image is scaled so that its shorter side has this size: on a receipt cut out of a photo the characters
     * then have the size Tesseract reads best. Small photos get more confident answers, large ones a faster one.
     */
    static final int SHORT_SIDE = 1500;

    private static final String TESSDATA = "/tessdata/osd.traineddata";

    private final TessBaseAPI api = new TessBaseAPI();

    /**
     * @param tessdata directory with osd.traineddata
     */
    public TesseractOrientation(Path tessdata) {
        if (api.Init(tessdata.toString(), "osd") != 0) {
            api.close();
            throw new IllegalStateException("Failed to load Tesseract OSD data from " + tessdata);
        }
        api.SetPageSegMode(PSM_OSD_ONLY);
    }

    /**
     * @return instance shared by the whole application, with OSD data from the library resources
     */
    public static TesseractOrientation shared() {
        return Shared.INSTANCE;
    }

    /**
     * @return clockwise rotation that makes the text upright; empty if the text is not found or not sure
     */
    public synchronized Optional<Rotate> uprightTurns(ImageData image) {
        final double scale = (double) SHORT_SIDE / Math.min(image.getWidth(), image.getHeight());
        final ImageData gray = image.transform()
                .grayscale()
                .scale(scale)
                .asImage();
        final int width = gray.getWidth();
        try (
                IntPointer degrees = new IntPointer(1);
                FloatPointer confidence = new FloatPointer(1);
                PointerPointer<BytePointer> script = new PointerPointer<>(1);
                FloatPointer scriptConfidence = new FloatPointer(1)
        ) {
            // Tesseract copies the pixels
            api.SetImage(gray.pixels(), width, gray.getHeight(), 1, width);
            api.SetSourceResolution(RESOLUTION);

            if (!api.DetectOrientationScript(degrees, confidence, script, scriptConfidence)) {
                log.info("Text orientation is not detected");
                return Optional.empty();
            }
            log.info("Text orientation: {} degrees, confidence {}, script {}",
                    degrees.get(), confidence.get(), script.get(BytePointer.class, 0).getString());

            if (confidence.get() < MIN_CONFIDENCE) {
                return Optional.empty();
            }
            // Tesseract reports how far the image is turned clockwise; turning it back is the rest of the circle
            return Optional.of(Rotate.fromDegrees(-degrees.get()));
        } finally {
            api.Clear();
        }
    }

    @Override
    public synchronized void close() {
        api.End();
        api.close();
    }

    /**
     * Tesseract reads its data from a directory, so the resource is copied to a temporary one.
     */
    private static Path extractTessdata() {
        try {
            final Path dir = Files.createTempDirectory("tessdata");
            dir.toFile().deleteOnExit();
            final Path file = dir.resolve("osd.traineddata");
            try (InputStream stream = TesseractOrientation.class.getResourceAsStream(TESSDATA)) {
                Files.copy(requireNonNull(stream, TESSDATA + " not found"), file);
            }
            file.toFile().deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to extract " + TESSDATA, e);
        }
    }

    /**
     * Created on first use: loading the data takes time and memory.
     */
    private static final class Shared {
        static final TesseractOrientation INSTANCE = new TesseractOrientation(extractTessdata());
    }
}
