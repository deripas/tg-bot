package com.github.deripas.image;

import com.github.deripas.TestImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTransformTest {

    private static final ImageData IMAGE = ImageData.of(TestImages.read("/1/IMG_4177.jpg"));
    private static final int IMAGE_WIDTH = IMAGE.getWidth();
    private static final int IMAGE_HEIGHT = IMAGE.getHeight();

    @Test
    void testOrig() {
        final ImageData image = IMAGE;

        assertFalse(image.isVertical());
        assertFalse(image.isGray());
        assertEquals(IMAGE_WIDTH, image.getWidth());
        assertEquals(IMAGE_HEIGHT, image.getHeight());
    }

    @Test
    void testCopy() {
        final ImageData image = IMAGE.transform().asImage();

        assertFalse(image.isVertical());
        assertFalse(image.isGray());
        assertEquals(IMAGE_WIDTH, image.getWidth());
        assertEquals(IMAGE_HEIGHT, image.getHeight());
    }

    @Test
    void testNoStepsKeepsSource() {
        assertSame(IMAGE, IMAGE.transform().asImage());
    }

    @Test
    void testPixelsAreCopy() {
        final ImageData image = IMAGE.transform().resize(4, 2).grayscale().asImage();
        final byte[] pixels = image.pixels();

        assertEquals(8, pixels.length);
        pixels[0]++;
        assertNotEquals(pixels[0], image.pixels()[0]);
    }

    @Test
    void testVertical() {
        final ImageData image = IMAGE.transform()
                .vertical()
                .asImage();

        assertTrue(image.isVertical());
        assertFalse(image.isGray());
        assertEquals(IMAGE_HEIGHT, image.getWidth());
        assertEquals(IMAGE_WIDTH, image.getHeight());
    }

    @Test
    void testGray() {
        final ImageData image = IMAGE.transform()
                .grayscale()
                .asImage();

        assertFalse(image.isVertical());
        assertTrue(image.isGray());
        assertEquals(IMAGE_WIDTH, image.getWidth());
        assertEquals(IMAGE_HEIGHT, image.getHeight());
    }

    @Test
    void testGrayAndVertical() {
        final ImageData image = IMAGE.transform()
                .grayscale()
                .vertical()
                .asImage();

        assertTrue(image.isVertical());
        assertTrue(image.isGray());
        assertEquals(IMAGE_HEIGHT, image.getWidth());
        assertEquals(IMAGE_WIDTH, image.getHeight());
    }

    @Test
    void testResizeAndGray() {
        final ImageData image = IMAGE.transform()
                .resize(64, 64)
                .grayscale()
                .asImage();

        assertTrue(image.isGray());
        assertEquals(64, image.getWidth());
        assertEquals(64, image.getHeight());
    }

    @Test
    void testCrop_1() {
        final ImageData image = IMAGE.transform()
                .cropRectangle(10, 10, 100, 100)
                .asImage();

        assertFalse(image.isGray());
        assertEquals(100, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    @Test
    void testCrop_2() {
        final ImageData image = IMAGE.transform()
                .cropRows(10, 110)
                .asImage();

        assertFalse(image.isGray());
        assertEquals(IMAGE_WIDTH, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    @Test
    void testStepsRunInOrder_1() {
        // rows of the image turned vertical: the full width of the turned image is the height of the source
        final ImageData image = IMAGE.transform()
                .vertical()
                .cropRows(10, 110)
                .asImage();

        assertFalse(image.isVertical());
        assertEquals(IMAGE_HEIGHT, image.getWidth());
        assertEquals(100, image.getHeight());
    }

    @Test
    void testStepsRunInOrder_2() {
        // rows of the image turned vertical: the full width of the turned image is the height of the source
        final ImageData image = IMAGE.transform()
                .vertical()
                .cropRows(10, 110)
                .vertical()
                .asImage();

        assertTrue(image.isVertical());
        assertEquals(IMAGE_HEIGHT, image.getHeight());
        assertEquals(100, image.getWidth());
    }

    @Test
    void testScaleTakesSizeOfPreviousStep() {
        final ImageData image = IMAGE.transform()
                .cropRectangle(0, 0, 200, 100)
                .scale(0.5)
                .asImage();

        assertEquals(100, image.getWidth());
        assertEquals(50, image.getHeight());
    }

    @Test
    void testVerticalTakesOrientationOfPreviousStep() {
        // the source is horizontal, the turned image is vertical and must not be turned again
        final ImageData image = IMAGE.transform()
                .vertical()
                .vertical()
                .asImage();

        assertEquals(IMAGE_HEIGHT, image.getWidth());
        assertEquals(IMAGE_WIDTH, image.getHeight());
    }

    @Test
    void testGrayOfGrayKeepsPixels() {
        final ImageData gray = IMAGE.transform().grayscale().asImage();

        assertArrayEquals(gray.pixels(), gray.transform().grayscale().asImage().pixels());
    }

    @Test
    void testTransformIsImmutable() {
        final ImageTransform base = IMAGE.transform().resize(80, 60);
        final ImageData vertical = base.vertical().asImage();
        final ImageData gray = base.grayscale().asImage();

        assertTrue(vertical.isVertical());
        assertFalse(vertical.isGray());
        assertFalse(gray.isVertical());
        assertTrue(gray.isGray());
    }

    @Test
    void testRotateMovesPixels() {
        final ImageData image = ImageData.of(marked(BufferedImage.TYPE_3BYTE_BGR));

        final BufferedImage clockwise = image.transform().rotate(Rotate.ROTATE_90_CLOCKWISE).asImage().toImage();
        final BufferedImage upsideDown = image.transform().rotate(Rotate.ROTATE_180).asImage().toImage();

        // the mark in the top left corner moves to the top right one, then to the bottom right one
        assertTrue(isWhite(clockwise, clockwise.getWidth() - 1, 0));
        assertFalse(isWhite(clockwise, 0, 0));
        assertTrue(isWhite(upsideDown, upsideDown.getWidth() - 1, upsideDown.getHeight() - 1));
        assertFalse(isWhite(upsideDown, 0, 0));
    }

    @Test
    void testCropRectangleKeepsPixels() {
        final BufferedImage source = TestImages.read("/1/IMG_4177.jpg");

        final ImageData image = IMAGE.transform()
                .cropRectangle(30, 20, 50, 40)
                .asImage();

        assertImage(source.getSubimage(30, 20, 50, 40), image.toImage());
    }

    @Test
    void testCropRectangleOutsideFails() {
        final ImageTransform transform = IMAGE.transform().cropRows(IMAGE_HEIGHT - 10, IMAGE_HEIGHT + 10);

        assertThrows(IllegalArgumentException.class, transform::asImage);
    }

    @Test
    void testCropTakesCornersInAnyOrder() {
        final Polygon ordered = new Polygon(new int[]{100, 600, 620, 90}, new int[]{50, 60, 900, 880}, 4);
        final Polygon shuffled = new Polygon(new int[]{620, 100, 90, 600}, new int[]{900, 50, 880, 60}, 4);

        final ImageData expected = IMAGE.transform().crop(ordered).asImage();
        final ImageData actual = IMAGE.transform().crop(shuffled).asImage();

        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        assertArrayEquals(expected.pixels(), actual.pixels());
    }

    @Test
    void testOfCopiesPixels() {
        final BufferedImage source = marked(BufferedImage.TYPE_BYTE_GRAY);
        final ImageData image = ImageData.of(source);

        source.getGraphics().fillRect(0, 0, source.getWidth(), source.getHeight());

        assertTrue(image.isGray());
        assertTrue(isWhite(image.toImage(), 0, 0));
        assertFalse(isWhite(image.toImage(), source.getWidth() - 1, source.getHeight() - 1));
    }

    @Test
    void testOfSubimage() {
        final BufferedImage source = TestImages.read("/1/IMG_4177.jpg");
        final BufferedImage subimage = source.getSubimage(30, 20, 50, 40);

        final ImageData image = ImageData.of(subimage);

        assertEquals(50, image.getWidth());
        assertEquals(40, image.getHeight());
        assertImage(subimage, image.toImage());
    }

    @Test
    void testRoundTripKeepsPixels() {
        final BufferedImage source = TestImages.read("/1/IMG_4177.jpg");

        assertImage(source, IMAGE.transform().asImage().toImage());
    }

    @Test
    void testToJpeg() throws IOException {
        final byte[] jpeg = IMAGE.transform()
                .grayscale()
                .scale(0.5)
                .toJpeg();

        final BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        assertNotNull(decoded);
        assertEquals(Math.round(IMAGE_WIDTH * 0.5), decoded.getWidth());
        assertEquals(Math.round(IMAGE_HEIGHT * 0.5), decoded.getHeight());
    }

    /**
     * @return black 40x30 image with a white 10x10 mark in the top left corner
     */
    private static BufferedImage marked(int type) {
        final BufferedImage image = new BufferedImage(40, 30, type);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 10, 10);
        graphics.dispose();
        return image;
    }

    private static boolean isWhite(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) & 0xFF) > 128;
    }

    private static void assertImage(BufferedImage expected, BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y += Math.max(1, expected.getHeight() / 50)) {
            for (int x = 0; x < expected.getWidth(); x += Math.max(1, expected.getWidth() / 50)) {
                assertEquals(expected.getRGB(x, y) & 0xFFFFFF, actual.getRGB(x, y) & 0xFFFFFF,
                        "pixel " + x + ", " + y);
            }
        }
    }
}
