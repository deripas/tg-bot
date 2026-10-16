package com.github.deripas.image;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.ConfigLength;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayS32;
import boofcv.struct.image.GrayU8;

import java.util.Arrays;
import java.util.List;

/**
 * Text of a scanned document merged into solid bands of lines: the letters and words of a line are joined,
 * so that the gaps between the lines are what is left blank. Made for a single column of short lines,
 * like a receipt.
 * <ol>
 *     <li>The ink is found with a local threshold (Sauvola): the light varies along a photographed receipt.</li>
 *     <li>Ink that is not text is removed: specks of the paper and of the background, and ink much taller than
 *     a line, e.g. shadows and background along the edges or folds.</li>
 *     <li>Gaps along a row between letters, words and the columns of a line are filled.</li>
 * </ol>
 * Sizes are shares of the image width, which is about the width of the printed lines.
 */
public final class TextMask {

    /**
     * Width of the local threshold window: a few characters.
     */
    private static final double THRESHOLD_WINDOW = 0.05;
    /**
     * Sensitivity of the threshold: lower finds fainter print, e.g. the dashes around the headings,
     * and more specks too.
     */
    private static final float SAUVOLA_K = 0.2f;
    /**
     * Ink taller than this is not text.
     */
    private static final double MAX_INK_HEIGHT = 0.1;
    /**
     * Ink with an area less than the square of this is not text; commas and dots may go too,
     * the rest of their line stays.
     */
    private static final double MIN_INK_SIZE = 0.006;
    /**
     * Gaps along a row up to this are filled: the columns of a line are a few characters apart.
     */
    private static final double LINE_GAP = 0.2;

    /**
     * [y][x]
     */
    private final boolean[][] text;

    private TextMask(boolean[][] text) {
        this.text = text;
    }

    public static TextMask of(ImageData image) {
        final GrayU8 gray = ConvertBufferedImage.convertFrom(image.toImage(), (GrayU8) null);
        final int window = Math.max(15, (int) (THRESHOLD_WINDOW * gray.width)) | 1;
        final GrayU8 ink = GThresholdImageOps.localSauvola(gray, null, ConfigLength.fixed(window), SAUVOLA_K, true);
        removeNotText(ink, (int) (MAX_INK_HEIGHT * gray.width), (int) Math.pow(MIN_INK_SIZE * gray.width, 2));
        return new TextMask(fillGaps(ink, (int) (LINE_GAP * gray.width)));
    }

    public int width() {
        return text.length == 0 ? 0 : text[0].length;
    }

    public int height() {
        return text.length;
    }

    public boolean isText(int x, int y) {
        return text[y][x];
    }

    private static void removeNotText(GrayU8 ink, int maxHeight, int minArea) {
        final GrayS32 labels = new GrayS32(ink.width, ink.height);
        final List<Contour> contours = BinaryImageOps.contour(ink, ConnectRule.EIGHT, labels);
        final int[] area = new int[contours.size() + 1];
        final int[] top = new int[contours.size() + 1];
        final int[] bottom = new int[contours.size() + 1];
        Arrays.fill(top, Integer.MAX_VALUE);
        Arrays.fill(bottom, Integer.MIN_VALUE);
        for (int y = 0; y < ink.height; y++) {
            for (int x = 0; x < ink.width; x++) {
                final int label = labels.unsafe_get(x, y);
                area[label]++;
                top[label] = Math.min(top[label], y);
                bottom[label] = Math.max(bottom[label], y);
            }
        }
        for (int y = 0; y < ink.height; y++) {
            for (int x = 0; x < ink.width; x++) {
                final int label = labels.unsafe_get(x, y);
                if (label > 0 && (bottom[label] - top[label] > maxHeight || area[label] < minArea)) {
                    ink.unsafe_set(x, y, 0);
                }
            }
        }
    }

    private static boolean[][] fillGaps(GrayU8 ink, int gap) {
        final boolean[][] text = new boolean[ink.height][ink.width];
        for (int y = 0; y < ink.height; y++) {
            int last = -1;
            for (int x = 0; x < ink.width; x++) {
                if (ink.unsafe_get(x, y) == 0) {
                    continue;
                }
                text[y][x] = true;
                if (last >= 0 && x - last <= gap) {
                    Arrays.fill(text[y], last + 1, x, true);
                }
                last = x;
            }
        }
        return text;
    }
}
