package com.github.deripas.tesseract;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.image.Rotate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOrientationTest {

    private final TesseractOrientation orientation = TesseractOrientation.shared();

    /**
     * The receipt is cut out by the reference corners (top-left, top-right, bottom-right, bottom-left) as the scanner
     * does; the rotation that makes it {@code upright} is known from the photo. The receipt turned by any quarter
     * must give the rotation that undoes it.
     */
    @ParameterizedTest
    @CsvSource({
            "1/IMG_4177.jpg,               0, 197,  1533, 199,  1538, 1004,  0, 1062,  ROTATE_90_CLOCKWISE",
            "2/IMG_4189.jpg,               2640, 985,  14780, 940,  14862, 2590,  2665, 2950,  ROTATE_90_CLOCKWISE",
            "3/PXL_20261011_095819059.jpg, 1185, 0,  1755, 0,  1845, 4031,  1165, 4031,  ROTATE_NONE",
    })
    void testUprightTurnsUndoRotation(
            String path,
            int ltx, int lty,
            int rtx, int rty,
            int rbx, int rby,
            int lbx, int lby,
            Rotate upright
    ) {
        final Polygon corners = new Polygon(new int[]{ltx, rtx, rbx, lbx}, new int[]{lty, rty, rby, lby}, 4);

        final ImageData receipt = TestImages.readData(path).transform()
                .crop(corners)
                .grayscale()
                .asImage();
        for (Rotate rotation : Rotate.values()) {
            final ImageData rotated = receipt.transform().rotate(rotation).asImage();
            assertThat(orientation.uprightTurns(rotated))
                    .as("turned by %s", rotation)
                    .contains(Rotate.fromQuarterTurns(upright.quarterTurns() - rotation.quarterTurns()));
        }
    }
}
