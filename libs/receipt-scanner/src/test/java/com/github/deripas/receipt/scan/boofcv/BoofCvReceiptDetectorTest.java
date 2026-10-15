package com.github.deripas.receipt.scan.boofcv;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Point;
import java.awt.Polygon;

import static com.github.deripas.image.Rotate.ROTATE_180;
import static org.assertj.core.api.Assertions.assertThat;

class BoofCvReceiptDetectorTest {

    private static final double TOLERANCE = 0.02;

    private final BoofCvReceiptDetector detector = new BoofCvReceiptDetector();

    /**
     * Reference corners are marked by hand on the images turned vertical, as the scanner passes them to the detector:
     * top-left, top-right, bottom-right, bottom-left.
     * Receipt edges are uneven, so a corner may deviate by {@link #TOLERANCE} of the larger image side.
     * <p>
     * The image turned by 180 degrees is checked too: a receipt shot upside down must be found the same way.
     */
    @ParameterizedTest
    @CsvSource({
            "1/IMG_4177.jpg,             137, 0,  1002, 0,  1000, 1533,  195, 1538",
            "2/IMG_4189.jpg,            847, 2665,  2812, 2640,  2857, 14780,  1207, 14862",
            "3/PXL_20261011_095819059.jpg, 1185, 0,  1755, 0,  1845, 4031,  1165, 4031",
    })
    void testDetectFindsReceiptCorners(String path,
                                       int tlx, int tly, int trx, int try_, int brx, int bry, int blx, int bly) {
        final ImageData image = TestImages.readVertical(path);
        final Polygon expected = new Polygon(new int[]{tlx, trx, brx, blx}, new int[]{tly, try_, bry, bly}, 4);

        assertCorners(image, expected, "as is");
        final ImageData rotated = image.transform().rotate(ROTATE_180).asImage();
        assertCorners(rotated, rotate180(expected, image.getWidth(), image.getHeight()), "turned by 180 degrees");
    }

    private void assertCorners(ImageData image, Polygon expected, String description) {
        final double tolerance = TOLERANCE * Math.max(image.getWidth(), image.getHeight());

        final Polygon actual = detector.detect(image).orElseThrow();

        assertThat(actual.npoints).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            final double distance = Point.distance(
                    actual.xpoints[i], actual.ypoints[i], expected.xpoints[i], expected.ypoints[i]);
            assertThat(distance)
                    .as("%s, corner %d: expected (%d, %d), actual (%d, %d)", description, i,
                            expected.xpoints[i], expected.ypoints[i], actual.xpoints[i], actual.ypoints[i])
                    .isLessThanOrEqualTo(tolerance);
        }
    }

    /**
     * Corners of the image turned by 180 degrees: each point is mirrored through the center, and the top-left
     * corner becomes the bottom-right one, the top-right becomes the bottom-left.
     */
    private static Polygon rotate180(Polygon corners, int width, int height) {
        final Polygon rotated = new Polygon();
        for (int i = 0; i < 4; i++) {
            final int j = (i + 2) % 4;
            rotated.addPoint(width - 1 - corners.xpoints[j], height - 1 - corners.ypoints[j]);
        }
        return rotated;
    }
}
