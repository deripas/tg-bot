package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.image.TextMask;
import com.github.deripas.receipt.parse.ollama.model.ReceiptStrips.Strip;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.boofcv.BoofCvReceiptDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptStripsTest {

    private static final int ITEMS = 8;

    /**
     * A receipt printed as the Serbian ones: the name of an item from the left margin, its numbers indented below;
     * turned by 2 degrees, so that no row of pixels between the lines is blank.
     */
    @Test
    void cutsBetweenItemsAreFoundOnSkewedReceipt() {
        final ImageData receipt = receipt(Math.toRadians(2));

        final List<Strip> strips = ReceiptStrips.cut(receipt);

        assertCovers(strips, receipt);
        assertCutsCrossNoText(strips, TextMask.of(receipt));
        // between the items and the bottom of the scan
        assertThat(strips.stream().filter(Strip::itemEnd)).hasSize(ITEMS);
    }

    @Test
    void straightenedStripHasWidthOfScanAndHeightOfStrip() {
        final ImageData receipt = receipt(Math.toRadians(2));
        final Strip strip = ReceiptStrips.cut(receipt).get(1);

        final ImageData straight = strip.straighten(receipt);

        assertThat(straight.getWidth()).isEqualTo(receipt.getWidth());
        assertThat(straight.getHeight()).isEqualTo(strip.height());
        assertThat(straight.isGray()).isTrue();
    }

    @Test
    void blankScanIsOneStrip() {
        final ImageData blank = blank(400, 600);

        final List<Strip> strips = ReceiptStrips.cut(blank);

        assertThat(strips).singleElement().satisfies(strip -> {
            assertThat(strip.height()).isEqualTo(600);
            assertThat(strip.itemEnd()).isTrue();
        });
    }

    /**
     * Lines turned by 6 degrees, more than a cut may be skewed: the strip of all of them is cut again along
     * its skew, between the lines.
     */
    @Test
    void stripOfSkewedLinesIsCutAlongThem() {
        final ImageData receipt = receipt(Math.toRadians(6));

        final List<Strip> strips = ReceiptStrips.cut(receipt);

        assertCovers(strips, receipt);
        assertThat(strips).noneMatch(Strip::forced);
        assertThat(strips.stream().filter(Strip::itemEnd)).hasSize(ITEMS);
        // the mask of the scan joins skewed lines along its rows, so the cuts are checked on the pixels
        final BufferedImage image = receipt.toImage();
        for (Strip strip : strips.subList(0, strips.size() - 1)) {
            for (int x = 0; x < receipt.getWidth(); x++) {
                assertThat(image.getRaster().getSample(x, strip.bottom()[x], 0))
                        .as("dark pixel at %d, %d", x, strip.bottom()[x])
                        .isGreaterThan(128);
            }
        }
    }

    /**
     * Lines of a staircase, on the left and on the right in turn, overlapping a little in height: the letters
     * do not touch, but the merged lines do, as on the bent end of a long receipt. No gap is found, a part too
     * tall for a frame is cut across the text.
     */
    @Test
    void stripWithoutGapsIsCutByForce() {
        final BufferedImage image = new BufferedImage(600, 1200, BufferedImage.TYPE_BYTE_GRAY);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        boolean left = true;
        for (int y = 40; y < 1180; y += 12) {
            graphics.drawString("Kefir 2,8%, 1kg/KOM", left ? 20 : 320, y);
            left = !left;
        }
        graphics.dispose();
        final ImageData receipt = ImageData.of(image);

        final List<Strip> strips = ReceiptStrips.cut(receipt);

        assertCovers(strips, receipt);
        assertThat(strips).anyMatch(Strip::forced);
        // 0.6 of the width
        assertThat(strips).allSatisfy(strip -> assertThat(strip.height()).isLessThanOrEqualTo(360));
    }

    /**
     * Items of receipt 3 and of its panorama 2: 52; receipt 1: 2. The panorama keeps a strip of the background
     * along its left edge, which hides the indents of many lines. No strip is too tall for a frame.
     */
    @ParameterizedTest
    @CsvSource({
            "1/IMG_4177.jpg,               2,  12",
            "2/IMG_4189.jpg,               30, 56",
            "3/PXL_20261011_095819059.jpg, 44, 56",
    })
    void realReceiptsAreCutBetweenItems(String path, int minItemEnds, int maxItemEnds) {
        final ImageData scan = new DefaultReceiptScanner(new BoofCvReceiptDetector()).scan(TestImages.readData(path));

        final List<Strip> strips = ReceiptStrips.cut(scan);

        assertCovers(strips, scan);
        assertThat(strips.stream().filter(Strip::itemEnd).count()).isBetween((long) minItemEnds, (long) maxItemEnds);
        assertThat(strips).allSatisfy(strip -> assertThat(strip.height()).isLessThanOrEqualTo(scan.getWidth() * 6 / 10));
    }

    private static void assertCovers(List<Strip> strips, ImageData scan) {
        assertThat(strips.getFirst().top()).containsOnly(0);
        assertThat(strips.getLast().bottom()).containsOnly(scan.getHeight());
        for (int k = 1; k < strips.size(); k++) {
            assertThat(strips.get(k).top()).isEqualTo(strips.get(k - 1).bottom());
        }
        assertThat(strips).allSatisfy(strip -> {
            for (int x = 0; x < scan.getWidth(); x++) {
                assertThat(strip.bottom()[x]).isGreaterThanOrEqualTo(strip.top()[x]);
            }
        });
    }

    private static void assertCutsCrossNoText(List<Strip> strips, TextMask mask) {
        for (int k = 1; k < strips.size(); k++) {
            final Strip strip = strips.get(k);
            if (strips.get(k - 1).forced()) {
                // cut across text on purpose
                continue;
            }
            for (int x = 0; x < mask.width(); x++) {
                assertThat(mask.isText(x, strip.top()[x])).as("text at %d, %d", x, strip.top()[x]).isFalse();
            }
        }
    }

    private static ImageData receipt(double angle) {
        return SyntheticReceipts.items(ITEMS, angle);
    }

    private static ImageData blank(int width, int height) {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return ImageData.of(image);
    }
}
