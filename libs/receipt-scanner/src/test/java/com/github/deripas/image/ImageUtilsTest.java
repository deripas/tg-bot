package com.github.deripas.image;

import com.github.deripas.TestImages;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ImageUtilsTest {

    /**
     * Same picture after another decoder or JPEG compression; a broken decode scores below 26 dB.
     */
    private static final double MIN_PSNR = 35;
    private static final int STEP = 7;

    @Test
    void testUnsupportedFormatIsAnError() {
        assertThatThrownBy(() -> ImageUtils.load("not an image".getBytes()))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testReadJpg() {
        final BufferedImage image = TestImages.read("/1/IMG_4177.jpg");
        assertNotNull(image);
        assertNotEquals(0, image.getWidth());
        assertNotEquals(0, image.getHeight());
    }

    /**
     * The HEIC is compared with the JPEG exported from it by the phone. Pixels cannot match exactly: decoders
     * and JPEG compression differ by a few levels, so the images are compared by PSNR. A correct conversion
     * scores 41-44 dB on this photo; swapped color channels score 25 dB, a shift by 8 pixels 22 dB.
     */
    @Test
    void testReadHeic() {
        final BufferedImage heic = TestImages.read("/2/IMG_4189.HEIC");
        final BufferedImage jpg = TestImages.read("/2/IMG_4189.jpg");

        assertThat(heic.getWidth()).isEqualTo(jpg.getWidth());
        assertThat(heic.getHeight()).isEqualTo(jpg.getHeight());
        assertThat(psnr(heic, jpg)).isGreaterThan(MIN_PSNR);
    }

    @SneakyThrows
    @Test
    void testHeicConvertedToJpeg() {
        final BufferedImage heic = TestImages.read("/2/IMG_4189.HEIC");
        final BufferedImage jpg = ImageUtils.load(ImageUtils.toJpeg(heic));

        assertThat(jpg.getWidth()).isEqualTo(heic.getWidth());
        assertThat(jpg.getHeight()).isEqualTo(heic.getHeight());
        assertThat(psnr(jpg, heic)).isGreaterThan(MIN_PSNR);
    }

    /**
     * Peak signal-to-noise ratio over the color channels of every {@link #STEP}-th pixel, in dB.
     */
    private static double psnr(BufferedImage a, BufferedImage b) {
        double squares = 0;
        long count = 0;
        for (int y = 0; y < a.getHeight(); y += STEP) {
            for (int x = 0; x < a.getWidth(); x += STEP) {
                final int p = a.getRGB(x, y);
                final int q = b.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift += 8) {
                    final int difference = ((p >> shift) & 0xFF) - ((q >> shift) & 0xFF);
                    squares += difference * difference;
                    count++;
                }
            }
        }
        return 10 * Math.log10(255.0 * 255.0 / Math.max(squares / count, 1e-9));
    }
}
