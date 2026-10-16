package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.TextMask;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Cuts a strip of many lines found where the lines are skewed more than a cut may be, e.g. on a photo of a long
 * receipt bent at its end: the merged lines of the {@link TextMask} touch each other along the skew, and no path
 * crosses no text.
 * <ol>
 *     <li>The skew of the lines is found by the projection profile: along the right direction the dark pixels
 *     gather into the sharpest peaks.</li>
 *     <li>The columns of the strip are shifted against the skew, a shear: for a few degrees it is as good as
 *     a rotation, and a straight cut of the sheared strip is a path of the scan, so the strips stay cuts of the
 *     scan.</li>
 *     <li>The sheared strip is cut as a scan; the cuts are shifted back.</li>
 *     <li>A part still too tall for a frame is cut by force: along the path crossing the least text near its
 *     middle.</li>
 * </ol>
 */
@Slf4j
@UtilityClass
class SkewedStrips {

    private static final double MAX_ANGLE = Math.toRadians(8);
    private static final double ANGLE_STEP = Math.toRadians(0.25);
    /**
     * Columns sampled for the projection profile.
     */
    private static final int SAMPLE = 2;

    /**
     * @param path   the rows of the cut in the scan
     * @param forced the cut crosses text
     * @param above  the start of the line right above the cut, along the skew; -1 if not known
     * @param below  the start of the line right below it
     */
    record Cut(int[] path, boolean forced, int above, int below) {
    }

    /**
     * @param upper     the top cut of the strip in the scan
     * @param lower     the bottom cut
     * @param maxHeight parts of the strip taller than this are cut by force
     * @param line      height of the line next to a cut, for the starts of the lines
     * @return cuts strictly inside the strip, top to bottom
     */
    static List<Cut> cuts(ImageData scan, int[] upper, int[] lower, int maxHeight, int line) {
        final int width = scan.getWidth();
        final byte[] gray = gray(scan);
        final double slope = Math.tan(skew(gray, width, upper, lower));
        final Sheared sheared = shear(gray, width, upper, lower, slope);
        final TextMask mask = TextMask.of(sheared.image());

        final ReceiptStrips.Paths paths = ReceiptStrips.paths(mask);
        final List<int[]> clean = ReceiptStrips.cuts(mask, paths);
        // the bounds of the strip in the sheared image, and the cuts between them
        final List<int[]> bounds = new ArrayList<>();
        bounds.add(sheared.toSheared(upper));
        bounds.addAll(clean);
        bounds.add(sheared.toSheared(lower));
        final List<Boolean> forced = new ArrayList<>(Collections.nCopies(bounds.size(), false));
        int k = 0;
        while (k + 1 < bounds.size()) {
            if (ReceiptStrips.height(bounds.get(k), bounds.get(k + 1)) <= maxHeight
                    || ReceiptStrips.leftmostText(mask, bounds.get(k), bounds.get(k + 1)) < 0) {
                k++;
                continue;
            }
            final int[] cut = forcedCut(paths, bounds.get(k), bounds.get(k + 1));
            if (cut == null) {
                k++;
                continue;
            }
            bounds.add(k + 1, cut);
            forced.add(k + 1, true);
        }

        final List<Cut> cuts = new ArrayList<>();
        int[] previous = upper;
        for (int i = 1; i + 1 < bounds.size(); i++) {
            // the shear keeps the columns: the starts of the lines are columns of the scan
            final int[] starts = ReceiptStrips.lineStarts(mask, bounds.get(i), line);
            final int[] path = sheared.toScan(bounds.get(i));
            for (int x = 0; x < width; x++) {
                path[x] = Math.clamp(path[x], previous[x], lower[x]);
            }
            if (ReceiptStrips.height(previous, path) > 0 && ReceiptStrips.height(path, lower) > 0) {
                cuts.add(new Cut(path, forced.get(i), starts[0], starts[1]));
                previous = path;
            }
        }
        log.debug("Strip of {} rows, skewed by {} degrees: {} cuts, {} by force",
                ReceiptStrips.height(upper, lower), Math.round(Math.toDegrees(Math.atan(slope)) * 100) / 100.0,
                cuts.size(), cuts.stream().filter(Cut::forced).count());
        return cuts;
    }

    /**
     * The path crossing the least text whose right end is in the middle third between the two paths.
     *
     * @return the path, or null if the paths are too close
     */
    private static int[] forcedCut(ReceiptStrips.Paths paths, int[] upper, int[] lower) {
        final int last = upper.length - 1;
        final int from = upper[last] + (lower[last] - upper[last]) / 3;
        final int to = lower[last] - (lower[last] - upper[last]) / 3;
        final double[] costs = paths.costs();
        int best = -1;
        for (int end = Math.max(0, from); end <= Math.min(costs.length - 1, to); end++) {
            if (best < 0 || costs[end] < costs[best]) {
                best = end;
            }
        }
        if (best < 0) {
            return null;
        }
        final int[] path = paths.path(best);
        for (int x = 0; x <= last; x++) {
            path[x] = Math.clamp(path[x], upper[x] + 1, Math.max(upper[x] + 1, lower[x] - 1));
        }
        return path;
    }

    /**
     * @return the angle of the lines between the two paths, in radians, see {@link #skew(byte[], int, int[], int[])}
     */
    static double skew(ImageData scan, int[] upper, int[] lower) {
        return skew(gray(scan), scan.getWidth(), upper, lower);
    }

    /**
     * The angle of the lines of the strip, clockwise in image coordinates: the row of a line grows with the
     * column by its tangent.
     */
    private static double skew(byte[] gray, int width, int[] upper, int[] lower) {
        long sum = 0;
        long count = 0;
        for (int x = 0; x < width; x += SAMPLE) {
            for (int y = upper[x]; y < lower[x]; y++) {
                sum += gray[y * width + x] & 0xFF;
                count++;
            }
        }
        if (count == 0) {
            return 0;
        }
        // darker than the mean, so that the paper adds nothing
        final int mean = (int) (sum / count);
        final int top = Arrays.stream(upper).min().orElse(0);
        final int bottom = Arrays.stream(lower).max().orElse(0);
        final int margin = (int) Math.ceil(width * Math.tan(MAX_ANGLE)) + 1;
        // no skew unless another angle gives sharper peaks, e.g. on a blank strip
        double bestAngle = 0;
        double bestScore = profileScore(gray, width, upper, lower, 0, mean, top, bottom, margin);
        for (double angle = -MAX_ANGLE; angle <= MAX_ANGLE + 1e-9; angle += ANGLE_STEP) {
            final double score = profileScore(gray, width, upper, lower, angle, mean, top, bottom, margin);
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return bestAngle;
    }

    /**
     * @return the sum of squares of the projection profile of the darkness along the angle
     */
    private static double profileScore(byte[] gray, int width, int[] upper, int[] lower, double angle, int mean,
                                       int top, int bottom, int margin) {
        final double slope = Math.tan(angle);
        final long[] profile = new long[bottom - top + 2 * margin];
        for (int x = 0; x < width; x += SAMPLE) {
            final int shift = (int) Math.round(x * slope);
            for (int y = upper[x]; y < lower[x]; y++) {
                final int dark = mean - (gray[y * width + x] & 0xFF);
                if (dark > 0) {
                    profile[y - top - shift + margin] += dark;
                }
            }
        }
        double score = 0;
        for (long value : profile) {
            score += (double) value * value;
        }
        return score;
    }

    /**
     * The strip with its columns shifted up by their row along the skew; outside the strip blank.
     */
    private static Sheared shear(byte[] gray, int width, int[] upper, int[] lower, double slope) {
        final int top = Arrays.stream(upper).min().orElse(0);
        final int bottom = Arrays.stream(lower).max().orElse(0);
        int minShift = 0;
        int maxShift = 0;
        for (int x = 0; x < width; x++) {
            final int shift = (int) Math.round(x * slope);
            minShift = Math.min(minShift, shift);
            maxShift = Math.max(maxShift, shift);
        }
        // a row of the scan y goes to the row y - offset - shift(x)
        final int offset = top - maxShift;
        final int height = bottom - top + maxShift - minShift;
        final BufferedImage image = new BufferedImage(width, Math.max(1, height), BufferedImage.TYPE_BYTE_GRAY);
        final byte[] target = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        Arrays.fill(target, (byte) 255);
        for (int x = 0; x < width; x++) {
            final int shift = (int) Math.round(x * slope);
            for (int y = upper[x]; y < lower[x]; y++) {
                target[(y - offset - shift) * width + x] = gray[y * width + x];
            }
        }
        return new Sheared(ImageData.of(image), slope, offset);
    }

    private static byte[] gray(ImageData scan) {
        final byte[] pixels = scan.pixels();
        if (scan.isGray()) {
            return pixels;
        }
        final byte[] gray = new byte[scan.getWidth() * scan.getHeight()];
        for (int i = 0; i < gray.length; i++) {
            final int b = pixels[3 * i] & 0xFF;
            final int g = pixels[3 * i + 1] & 0xFF;
            final int r = pixels[3 * i + 2] & 0xFF;
            gray[i] = (byte) ((29 * b + 150 * g + 77 * r) >> 8);
        }
        return gray;
    }

    /**
     * @param offset a row of the scan y is the row y - offset - round(x * slope) of the image
     */
    private record Sheared(ImageData image, double slope, int offset) {

        int[] toSheared(int[] path) {
            final int[] sheared = new int[path.length];
            for (int x = 0; x < path.length; x++) {
                sheared[x] = path[x] - offset - (int) Math.round(x * slope);
            }
            return sheared;
        }

        int[] toScan(int[] path) {
            final int[] scan = new int[path.length];
            for (int x = 0; x < path.length; x++) {
                scan[x] = path[x] + offset + (int) Math.round(x * slope);
            }
            return scan;
        }
    }
}
