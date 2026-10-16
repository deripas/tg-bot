package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.TextMask;
import lombok.experimental.UtilityClass;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Cuts a scanned receipt into strips of lines along the gaps between them, on a skewed or curved receipt too,
 * and tells which cuts lie between items.
 * <p>
 * A straight cut needs a row of pixels without text; a curved receipt may have none, but it has a path from
 * the left edge to the right one along the gap between two lines. The path crossing the least text is found
 * by dynamic programming, as in seam carving, on the {@link TextMask}, where the letters of a line are merged
 * into a solid band. The path is kept nearly straight: a steeper one would dive through the gap between the
 * columns of a line and cut it in two. Paths crossing no text are the cuts.
 * <p>
 * A cut between items has an indented line right above it, the numbers of an item, and a line starting
 * at the left margin right below it, the name of the next item; a line starting left of the margin, e.g. at
 * a speck of the background, counts as starting at the margin. Only the lines next to the cut are looked at:
 * some gaps have no cut, so a strip may hold two lines.
 * <p>
 * A receipt skewed more than a cut may be, and a strip of many lines where it is, e.g. at the bent end of
 * a long receipt, are cut along their skew, see {@link SkewedStrips}.
 */
@UtilityClass
public class ReceiptStrips {

    /**
     * Steepest cut: one row up or down per this many columns. A receipt is skewed and curved by a few degrees.
     */
    private static final int STEP_RUN = 20;
    /**
     * Cost of a step of a path up or down, in pixels of text: keeps the path straight where it can be;
     * the steps of a path add up to less than one pixel.
     */
    private static final double STEP_COST = 0.02;
    /**
     * A line starting further right than the left margin of its neighbours by this share of the width
     * is indented.
     */
    private static final double INDENT = 0.05;
    /**
     * Cuts on each side whose lines give the left margin near a cut: the margin drifts on a curved receipt.
     */
    private static final int MARGIN_NEIGHBOURS = 6;
    /**
     * Text this close to the left edge of the scan, as a share of the width, is the background left by the crop.
     */
    private static final double EDGE = 0.01;
    /**
     * A receipt skewed by more than this, in radians, is cut along its skew as a whole: a cut is a path at most
     * about 3 degrees steep, which on a more skewed receipt runs across the lines.
     */
    private static final double SKEW = Math.toRadians(1.5);
    /**
     * A strip taller than this many lines is cut again along its own skew, see {@link SkewedStrips}.
     */
    private static final int TALL_LINES = 4;
    /**
     * A strip taller than this share of the width is cut in the end even across text: a frame of 4:3 holds
     * 0.75 of the width, a taller strip would be shrunk.
     */
    private static final double MAX_HEIGHT = 0.6;
    /**
     * Height of the line next to a cut, as a share of the distance between neighbouring lines.
     */
    private static final double LINE = 0.8;

    /**
     * @param scan receipt cut out of the photo and turned upright
     * @return strips top to bottom, covering the whole scan
     */
    public static List<Strip> cut(ImageData scan) {
        final TextMask mask = TextMask.of(scan);
        final int width = scan.getWidth();
        final int height = scan.getHeight();
        final List<int[]> cuts = cuts(mask);

        final int[] top = new int[width];
        final int[] bottom = new int[width];
        Arrays.fill(bottom, height);
        final List<int[]> found = new ArrayList<>();
        found.add(top);
        found.addAll(cuts);
        found.add(bottom);

        // a strip of many lines is where the lines are skewed more than a cut may be: it is cut again along them;
        // the starts of the lines next to those cuts are taken along the skew too
        final int pitch = linePitch(mask, referenceColumn(mask));
        final int line = (int) (LINE * pitch);
        final int maxHeight = (int) (MAX_HEIGHT * width);
        final List<SkewedStrips.Cut> bounds = new ArrayList<>();
        bounds.add(new SkewedStrips.Cut(top, false, -1, -1));
        if (Math.abs(SkewedStrips.skew(scan, top, bottom)) > SKEW) {
            // the whole receipt is skewed more than a cut may be: it is cut along its skew
            bounds.addAll(SkewedStrips.cuts(scan, top, bottom, maxHeight, line));
            bounds.add(new SkewedStrips.Cut(bottom, false, -1, -1));
            return strips(bounds, width);
        }
        for (int k = 1; k < found.size(); k++) {
            final int[] upper = found.get(k - 1);
            final int[] lower = found.get(k);
            if (height(upper, lower) > TALL_LINES * pitch) {
                bounds.addAll(SkewedStrips.cuts(scan, upper, lower, maxHeight, line));
            }
            if (k < found.size() - 1) {
                final int[] starts = lineStarts(mask, lower, line);
                bounds.add(new SkewedStrips.Cut(lower, false, starts[0], starts[1]));
            } else {
                bounds.add(new SkewedStrips.Cut(lower, false, -1, -1));
            }
        }
        return strips(bounds, width);
    }

    private static List<Strip> strips(List<SkewedStrips.Cut> bounds, int width) {
        final boolean[] itemEnds = itemEnds(bounds, width);
        final List<Strip> strips = new ArrayList<>();
        for (int k = 0; k + 1 < bounds.size(); k++) {
            final boolean byForce = bounds.get(k + 1).forced();
            strips.add(new Strip(bounds.get(k).path(), bounds.get(k + 1).path(), itemEnds[k + 1] && !byForce, byForce));
        }
        return strips;
    }

    /**
     * Cuts without text, one per gap, top to bottom; a cut lies in the gap at each column.
     */
    static List<int[]> cuts(TextMask mask) {
        if (mask.width() == 0 || mask.height() == 0) {
            return List.of();
        }
        return cuts(mask, paths(mask));
    }

    /**
     * The cheapest paths from the left edge to every pixel, by dynamic programming.
     */
    static Paths paths(TextMask mask) {
        final int width = mask.width();
        final int height = mask.height();
        // the cheapest path from the left edge to every pixel; the step to the previous column
        final byte[][] steps = new byte[width][height];
        double[] costs = new double[height];
        for (int y = 0; y < height; y++) {
            costs[y] = mask.isText(0, y) ? 1 : 0;
        }
        for (int x = 1; x < width; x++) {
            final boolean step = x % STEP_RUN == 0;
            final double[] next = new double[height];
            for (int y = 0; y < height; y++) {
                byte best = 0;
                double cost = costs[y];
                if (step && y > 0 && costs[y - 1] + STEP_COST < cost) {
                    best = -1;
                    cost = costs[y - 1] + STEP_COST;
                }
                if (step && y < height - 1 && costs[y + 1] + STEP_COST < cost) {
                    best = 1;
                    cost = costs[y + 1] + STEP_COST;
                }
                steps[x][y] = best;
                next[y] = cost + (mask.isText(x, y) ? 1 : 0);
            }
            costs = next;
        }
        return new Paths(steps, costs);
    }

    /**
     * @return the paths without text, one per gap, top to bottom
     */
    static List<int[]> cuts(TextMask mask, Paths paths) {
        final int width = mask.width();
        final int height = mask.height();
        final double[] costs = paths.costs();
        // Paths are grouped by the gap they pass: by their row in the column with the most text in the middle,
        // which nearly every line crosses. By the row they end at they would not be: a short line does not reach
        // the right edge, and paths ending beside it slip into the next gap.
        final int reference = referenceColumn(mask);
        final TreeMap<Integer, int[]> byRow = new TreeMap<>();
        for (int end = 0; end < height; end++) {
            if (costs[end] < 1) {
                final int[] path = paths.path(end);
                byRow.putIfAbsent(path[reference], path);
            }
        }
        final List<int[]> gaps = new ArrayList<>();
        final List<int[]> run = new ArrayList<>();
        int previous = Integer.MIN_VALUE;
        for (Map.Entry<Integer, int[]> entry : byRow.entrySet()) {
            if (entry.getKey() != previous + 1 && !run.isEmpty()) {
                gaps.add(run.get(run.size() / 2));
                run.clear();
            }
            run.add(entry.getValue());
            previous = entry.getKey();
        }
        if (!run.isEmpty()) {
            gaps.add(run.get(run.size() / 2));
        }

        // a cut needs text between it and the previous one, e.g. not two cuts above and below a blank part,
        // and between it and the edges of the scan
        final int[] above = new int[width];
        Arrays.fill(above, -1);
        final int[] below = new int[width];
        Arrays.fill(below, height);
        final List<int[]> cuts = new ArrayList<>();
        for (int[] cut : gaps) {
            if (leftmostText(mask, cuts.isEmpty() ? above : cuts.getLast(), cut) >= 0) {
                cuts.add(cut);
            }
        }
        while (!cuts.isEmpty() && leftmostText(mask, cuts.getLast(), below) < 0) {
            cuts.removeLast();
        }
        // paths may touch; a cut never goes above the previous one
        for (int k = 1; k < cuts.size(); k++) {
            final int[] upper = cuts.get(k - 1);
            final int[] cut = cuts.get(k);
            for (int x = 0; x < width; x++) {
                cut[x] = Math.max(cut[x], upper[x]);
            }
        }
        return cuts;
    }

    /**
     * @param bounds the top of the scan, the cuts and the bottom of the scan
     * @return for every bound whether it lies between items; the bottom of the scan does
     */
    private static boolean[] itemEnds(List<SkewedStrips.Cut> bounds, int width) {
        final boolean[] ends = new boolean[bounds.size()];
        ends[bounds.size() - 1] = true;
        final List<SkewedStrips.Cut> cuts = bounds.subList(1, bounds.size() - 1);
        final int[] above = cuts.stream().mapToInt(SkewedStrips.Cut::above).toArray();
        final int[] below = cuts.stream().mapToInt(SkewedStrips.Cut::below).toArray();
        final int indent = (int) (INDENT * width);
        for (int k = 0; k < cuts.size(); k++) {
            if (above[k] < 0 || below[k] < 0) {
                continue;
            }
            final int margin = margin(above, below, k);
            ends[k + 1] = above[k] - margin > indent && below[k] - margin <= indent;
        }
        return ends;
    }

    /**
     * @param line height of the line next to the cut
     * @return the starts of the lines right above the cut and right below it, -1 if not known
     */
    static int[] lineStarts(TextMask mask, int[] cut, int line) {
        final int edge = (int) Math.ceil(EDGE * mask.width());
        return new int[]{
                lineStart(leftmostText(mask, shift(cut, -line), cut), edge),
                lineStart(leftmostText(mask, cut, shift(cut, line)), edge)
        };
    }

    /**
     * @return the start of a line, -1 if it is not known: no text, or "text" at the edge of the scan, which is
     * the background left by the crop; printed lines keep off the edge of the paper
     */
    private static int lineStart(int leftmost, int edge) {
        return leftmost < edge ? -1 : leftmost;
    }

    static int referenceColumn(TextMask mask) {
        final int width = mask.width();
        // not near the edges: the background left along them may have more "text" than the text
        int reference = width / 2;
        int most = -1;
        for (int x = width / 5; x < width - width / 5; x++) {
            int text = 0;
            for (int y = 0; y < mask.height(); y++) {
                text += mask.isText(x, y) ? 1 : 0;
            }
            if (text > most) {
                most = text;
                reference = x;
            }
        }
        return reference;
    }

    /**
     * @param steps for each column and row the step of the cheapest path to the previous column: [x][y]
     * @param costs the cost of the cheapest path ending at each row of the right edge, in pixels of text
     */
    record Paths(byte[][] steps, double[] costs) {

        /**
         * @return the cheapest path ending at the row of the right edge: its row in every column
         */
        int[] path(int end) {
            final int[] path = new int[steps.length];
            path[steps.length - 1] = end;
            for (int x = steps.length - 1; x > 0; x--) {
                path[x - 1] = path[x] + steps[x][path[x]];
            }
            return path;
        }
    }

    static int height(int[] upper, int[] lower) {
        int height = 0;
        for (int x = 0; x < upper.length; x++) {
            height = Math.max(height, lower[x] - upper[x]);
        }
        return height;
    }

    /**
     * @return the leftmost column with text strictly between the two paths, -1 if there is none
     */
    static int leftmostText(TextMask mask, int[] upper, int[] lower) {
        for (int x = 0; x < upper.length; x++) {
            for (int y = Math.max(0, upper[x] + 1); y < Math.min(mask.height(), lower[x]); y++) {
                if (mask.isText(x, y)) {
                    return x;
                }
            }
        }
        return -1;
    }

    /**
     * The left margin near a cut: the lower quartile of the starts of the lines next to the neighbouring cuts.
     * Not the leftmost one: a speck of the background along the edge would move it; about half of the lines
     * start at the margin, the names, and the rest are indented. Neighbours, so that a curved left edge does not
     * look like an indent.
     */
    private static int margin(int[] above, int[] below, int k) {
        final List<Integer> starts = new ArrayList<>();
        for (int j = Math.max(0, k - MARGIN_NEIGHBOURS); j <= Math.min(above.length - 1, k + MARGIN_NEIGHBOURS); j++) {
            if (above[j] >= 0) {
                starts.add(above[j]);
            }
            if (below[j] >= 0) {
                starts.add(below[j]);
            }
        }
        if (starts.isEmpty()) {
            return 0;
        }
        starts.sort(null);
        return starts.get(starts.size() / 4);
    }

    /**
     * The distance between neighbouring lines: the usual height of a run of text plus the usual gap in the column.
     * Not the distance between the cuts: some gaps have none.
     */
    static int linePitch(TextMask mask, int x) {
        final List<Integer> text = new ArrayList<>();
        final List<Integer> blank = new ArrayList<>();
        int run = 0;
        for (int y = 0; y <= mask.height(); y++) {
            final boolean inText = y < mask.height() && mask.isText(x, y);
            final boolean wasText = y > 0 && mask.isText(x, y - 1);
            if (y > 0 && (y == mask.height() || inText != wasText)) {
                (wasText ? text : blank).add(run);
                run = 0;
            }
            run++;
        }
        return median(text) + median(blank);
    }

    private static int median(List<Integer> values) {
        return values.isEmpty() ? 0 : values.stream().sorted().toList().get(values.size() / 2);
    }

    private static int[] shift(int[] path, int dy) {
        final int[] shifted = new int[path.length];
        for (int x = 0; x < path.length; x++) {
            shifted[x] = path[x] + dy;
        }
        return shifted;
    }

    /**
     * Part of the scan between two cuts.
     *
     * @param top     first row of the strip in every column
     * @param bottom  row after the last one in every column
     * @param itemEnd the bottom cut lies between items, or it is the bottom of the scan: a frame may end here
     * @param forced  the bottom cut was made across text: no gap was found in a part too tall for a frame
     */
    public record Strip(int[] top, int[] bottom, boolean itemEnd, boolean forced) {

        public Strip(int[] top, int[] bottom, boolean itemEnd) {
            this(top, bottom, itemEnd, false);
        }

        /**
         * @return the height of the strip in its tallest column
         */
        public int height() {
            return ReceiptStrips.height(top, bottom);
        }

        /**
         * Straightens the strip: every column is stretched from its top to its bottom over the height of the
         * strip, so that both cuts become straight and the lines between them follow.
         *
         * @param scan the scan the strip was cut from
         * @return the strip of the full width of the scan and of {@link #height()}, gray or BGR as the scan
         */
        public ImageData straighten(ImageData scan) {
            final int width = scan.getWidth();
            final int height = Math.max(1, height());
            final int channels = scan.getType().getChannels();
            final byte[] source = scan.pixels();
            final BufferedImage result = new BufferedImage(width, height,
                    channels == 1 ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR);
            final byte[] target = ((DataBufferByte) result.getRaster().getDataBuffer()).getData();
            for (int x = 0; x < width; x++) {
                final int from = top[x];
                final int rows = Math.max(1, bottom[x] - from);
                for (int y = 0; y < height; y++) {
                    final int row = Math.min(scan.getHeight() - 1, from + y * rows / height);
                    System.arraycopy(source, (row * width + x) * channels, target, (y * width + x) * channels, channels);
                }
            }
            return ImageData.of(result);
        }
    }
}
