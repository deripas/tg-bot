package com.github.deripas;

import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.GThresholdImageOps;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayS32;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.ConfigLength;
import boofcv.struct.image.GrayU8;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import com.github.deripas.receipt.scan.boofcv.BoofCvReceiptDetector;
import com.github.deripas.tesseract.TesseractLineSegmenter;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;

/**
 * Looks for zones where a receipt can be cut without cutting a line of text, on a curved or skewed receipt too.
 * <p>
 * A straight cut needs a row of pixels without ink. A curved receipt may have none between its lines, but it has
 * a path from the left edge to the right one along the gap between two lines: the path that crosses the least ink,
 * found by dynamic programming as in seam carving. Before that, letters and words are merged into solid bands
 * of lines, so that the path does not slip between letters through a line.
 * <p>
 * A cut between two items is a cut with an indented line right above it (the numbers of an item) and a line
 * starting at the left margin right below it (the name of the next item). Only the lines next to the cut are
 * looked at, since some gaps have no cut and the lines between neighbouring cuts may be two.
 * <p>
 * Draws in {@code target/lines-*.png}: green - cuts along the gaps, blue - cuts between items;
 * with {@code -Dmask=true} also the ink merged into lines in {@code target/mask-*.png}.
 */
public class LineSegmenterTester {

    /**
     * Width of the local threshold window, as a share of the receipt width: a few characters.
     */
    private static final double THRESHOLD_WINDOW = 0.05;
    /**
     * Sensitivity of the Sauvola threshold: lower finds fainter print, e.g. the dashes around the headings.
     */
    private static final float SAUVOLA_K = 0.2f;
    /**
     * Gaps between letters and words narrower than this share of the receipt width are filled.
     */
    private static final double WORD_GAP = 0.2;
    /**
     * Ink taller than this share of the receipt width is not text: shadows and background along the edges, folds.
     */
    private static final double MAX_INK_HEIGHT = 0.1;
    /**
     * Ink with an area smaller than the square of this share of the receipt width is not text: specks of the paper
     * and of the background. Commas and dots may go too, the rest of their line stays.
     */
    private static final double MIN_INK_SIZE = 0.006;
    /**
     * Steepest path: one row up or down per this many columns. A receipt is skewed and curved by a few degrees,
     * while a steeper path would dive through a gap between the columns of a line and cut it in two.
     */
    private static final int STEP_RUN = 20;
    /**
     * A line starting further right than the left margin of its neighbours by this share of the receipt width
     * is indented: the line of the numbers of an item.
     */
    private static final double INDENT = 0.05;
    /**
     * Neighbours on each side that give the left margin of a line: the margin drifts on a curved receipt.
     */
    private static final int MARGIN_NEIGHBOURS = 6;
    /**
     * Cost of a step of the path one pixel up or down, in pixels of ink: keeps the path straight where it can be.
     */
    private static final double STEP_COST = 0.02;

    @SneakyThrows
    static void main() {
        final ReceiptScanner scanner = new DefaultReceiptScanner(new BoofCvReceiptDetector());
        try (TesseractLineSegmenter segmenter = new TesseractLineSegmenter()) {
            for (String path : new String[]{"1/IMG_4177.jpg", "2/IMG_4189.jpg", "3/PXL_20261011_095819059.jpg"}) {
                final ImageData image = scanner.scan(TestImages.readData(path))
                        .transform()
                        .vertical()
                        .grayscale()
                        .asImage();
                final List<int[]> items = experiment(path, image, segmenter);
                final ImageData straightened = straighten(image, items);
                experiment("s" + path, straightened, segmenter);
            }
        }
    }

    /**
     * @return the cuts between items
     */
    private static List<int[]> experiment(String path, ImageData image, TesseractLineSegmenter segmenter) throws Exception {
        final long start = System.nanoTime();
        final boolean[][] bands = lineBands(image);
        if (Boolean.getBoolean("mask")) {
            final BufferedImage mask = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    mask.getRaster().setSample(x, y, 0, bands[y][x] ? 0 : 255);
                }
            }
            ImageIO.write(mask, "png", new File("target/mask-" + path.substring(0, path.indexOf('/')) + ".png"));
        }
        final double[] costs = new double[image.getHeight()];
        final int[][] from = seams(bands, costs);
        final List<int[]> curved = cleanSeams(bands, from, costs);
        final List<int[]> straight = zones(straightCosts(bands));

        // the lines between neighbouring cuts; two cuts in one gap leave an empty line, the lower cut is dropped
        final List<int[]> cuts = new ArrayList<>();
        final List<Integer> lefts = new ArrayList<>();
        for (int[] seam : curved) {
            if (cuts.isEmpty()) {
                cuts.add(seam);
                continue;
            }
            final int left = leftmostInk(bands, cuts.getLast(), seam);
            if (left >= 0) {
                cuts.add(seam);
                lefts.add(left);
            }
        }
        final int indent = (int) (INDENT * image.getWidth());
        // the line height: some gaps have no cut, so the lines between neighbouring cuts may be two lines
        final int step = medianStep(cuts, bands[0].length / 2) * 4 / 5;
        final List<Integer> above = new ArrayList<>();
        final List<Integer> below = new ArrayList<>();
        for (int[] seam : cuts) {
            above.add(leftmostInk(bands, shift(seam, -step), seam));
            below.add(leftmostInk(bands, seam, shift(seam, step)));
        }
        final List<int[]> items = new ArrayList<>();
        for (int k = 0; k < cuts.size(); k++) {
            // the line right above the cut is indented: the numbers of an item; the line right below starts
            // at the margin: the name of the next item
            if (above.get(k) >= 0 && below.get(k) >= 0
                    && above.get(k) - margin(above, below, k) > indent
                    && below.get(k) - margin(above, below, k) <= indent) {
                items.add(cuts.get(k));
            }
        }
        final long millis = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%s %dx%d: %d lines, %d cuts between items, %d straight cuts, %d Tesseract lines, %d ms%n",
                path, image.getWidth(), image.getHeight(), lefts.size(), items.size(), straight.size(),
                segmenter.lines(image).size(), millis);

        final BufferedImage canvas = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(image.toImage(), 0, 0, null);
        graphics.setColor(new Color(0, 180, 0, 140));
        graphics.setStroke(new BasicStroke(1));
        cuts.forEach(seam -> drawSeam(graphics, seam));
        graphics.setColor(Color.BLUE);
        graphics.setStroke(new BasicStroke(3));
        items.forEach(seam -> drawSeam(graphics, seam));
        graphics.dispose();
        final File out = new File("target/lines-" + path.substring(0, path.indexOf('/')) + ".png");
        ImageIO.write(canvas, "png", out);
        System.out.println("  " + out.getAbsolutePath());
        return items;
    }

    /**
     * Cuts the receipt into strips along the cuts between items, straightens every strip by shifting each column
     * of it so that its upper cut becomes a straight line, trims the sides to the text and puts the strips together
     * into a synthetic receipt.
     */
    private static ImageData straighten(ImageData image, List<int[]> cuts) {
        final BufferedImage source = image.toImage();
        final int width = image.getWidth();
        final boolean[][] bands = lineBands(image);
        final List<int[]> bounds = new ArrayList<>();
        bounds.add(new int[width]);
        bounds.addAll(cuts);
        final int[] bottom = new int[width];
        Arrays.fill(bottom, image.getHeight() - 1);
        bounds.add(bottom);

        final List<BufferedImage> strips = new ArrayList<>();
        for (int k = 0; k + 1 < bounds.size(); k++) {
            final int[] upper = bounds.get(k);
            final int[] lower = bounds.get(k + 1);
            int height = 0;
            int left = width;
            int right = -1;
            for (int x = 0; x < width; x++) {
                height = Math.max(height, lower[x] - upper[x]);
                for (int y = upper[x] + 1; y < lower[x]; y++) {
                    if (bands[y][x]) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
            }
            if (right < 0 || height <= 0) {
                continue;
            }
            final BufferedImage strip = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    final int from = upper[x] + 1 + y;
                    // below the lower cut of the column: blank paper
                    strip.getRaster().setSample(x, y, 0, from < lower[x] ? source.getRaster().getSample(x, from, 0) : 255);
                }
            }
            final int margin = width / 50;
            final int from = Math.max(0, left - margin);
            final int to = Math.min(width, right + margin);
            strips.add(strip.getSubimage(from, 0, to - from, height));
        }
        // the strips are trimmed differently; put them on one width at their left edges
        final int stripWidth = strips.stream().mapToInt(BufferedImage::getWidth).max().orElse(width);
        final int stripsHeight = strips.stream().mapToInt(BufferedImage::getHeight).sum();
        final BufferedImage result = new BufferedImage(stripWidth, stripsHeight, BufferedImage.TYPE_BYTE_GRAY);
        final Graphics2D graphics = result.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, stripWidth, stripsHeight);
        int y = 0;
        for (BufferedImage strip : strips) {
            graphics.drawImage(strip, 0, y, null);
            y += strip.getHeight();
        }
        graphics.dispose();
        return ImageData.of(result);
    }

    /**
     * Paths without ink grouped by the gap they pass: by their row in the column with the most ink in the middle
     * of the receipt, which nearly every line crosses. Grouping by the row they end at fails: a short line does not reach the right edge,
     * and paths ending beside it slip down into the next gap.
     *
     * @return one path per gap, in the middle of the paths of the gap, top to bottom
     */
    private static List<int[]> cleanSeams(boolean[][] bands, int[][] from, double[] costs) {
        final int width = bands[0].length;
        // not near the edges: the background left along them may have more "ink" than the text
        int reference = width / 2;
        int most = -1;
        for (int x = width / 5; x < width - width / 5; x++) {
            int ink = 0;
            for (boolean[] row : bands) {
                ink += row[x] ? 1 : 0;
            }
            if (ink > most) {
                most = ink;
                reference = x;
            }
        }
        final TreeMap<Integer, int[]> byRow = new TreeMap<>();
        for (int end = 0; end < costs.length; end++) {
            if (costs[end] < 1) {
                final int[] seam = backtrack(from, end);
                byRow.putIfAbsent(seam[reference], seam);
            }
        }
        final List<int[]> seams = new ArrayList<>();
        final List<int[]> run = new ArrayList<>();
        int previous = Integer.MIN_VALUE;
        for (Map.Entry<Integer, int[]> entry : byRow.entrySet()) {
            if (entry.getKey() != previous + 1 && !run.isEmpty()) {
                seams.add(run.get(run.size() / 2));
                run.clear();
            }
            run.add(entry.getValue());
            previous = entry.getKey();
        }
        if (!run.isEmpty()) {
            seams.add(run.get(run.size() / 2));
        }
        return seams;
    }

    private static void drawSeam(Graphics2D graphics, int[] seam) {
        for (int x = 1; x < seam.length; x++) {
            graphics.drawLine(x - 1, seam[x - 1], x, seam[x]);
        }
    }

    /**
     * @return the leftmost column with ink between the two cuts, -1 if there is none
     */
    private static int leftmostInk(boolean[][] bands, int[] upper, int[] lower) {
        for (int x = 0; x < upper.length; x++) {
            for (int y = Math.max(0, upper[x] + 1); y < Math.min(bands.length, lower[x]); y++) {
                if (bands[y][x]) {
                    return x;
                }
            }
        }
        return -1;
    }

    /**
     * The left margin near a cut is the leftmost start of the lines around the neighbouring cuts, so that a curved
     * left edge does not look like an indent.
     */
    private static int margin(List<Integer> above, List<Integer> below, int k) {
        int margin = Integer.MAX_VALUE;
        for (int j = Math.max(0, k - MARGIN_NEIGHBOURS); j <= Math.min(above.size() - 1, k + MARGIN_NEIGHBOURS); j++) {
            if (above.get(j) >= 0) {
                margin = Math.min(margin, above.get(j));
            }
            if (below.get(j) >= 0) {
                margin = Math.min(margin, below.get(j));
            }
        }
        return margin;
    }

    private static int medianStep(List<int[]> cuts, int x) {
        final int[] steps = new int[Math.max(1, cuts.size() - 1)];
        for (int k = 1; k < cuts.size(); k++) {
            steps[k - 1] = cuts.get(k)[x] - cuts.get(k - 1)[x];
        }
        Arrays.sort(steps);
        return steps[steps.length / 2];
    }

    private static int[] shift(int[] seam, int dy) {
        final int[] shifted = new int[seam.length];
        for (int x = 0; x < seam.length; x++) {
            shifted[x] = seam[x] + dy;
        }
        return shifted;
    }

    /**
     * @return ink merged into bands of lines: [y][x]
     */
    private static boolean[][] lineBands(ImageData image) {
        final GrayU8 gray = ConvertBufferedImage.convertFrom(image.toImage(), (GrayU8) null);
        final int window = Math.max(15, (int) (THRESHOLD_WINDOW * gray.width)) | 1;
        final GrayU8 ink = GThresholdImageOps.localSauvola(gray, null, ConfigLength.fixed(window), SAUVOLA_K, true);
        removeNotText(ink, (int) (MAX_INK_HEIGHT * gray.width), (int) Math.pow(MIN_INK_SIZE * gray.width, 2));
        final int gap = (int) (WORD_GAP * gray.width);
        final boolean[][] bands = new boolean[gray.height][gray.width];
        for (int y = 0; y < gray.height; y++) {
            int last = -1;
            for (int x = 0; x < gray.width; x++) {
                if (ink.unsafe_get(x, y) == 0) {
                    continue;
                }
                bands[y][x] = true;
                // fill a gap since the previous ink of the row
                if (last >= 0 && x - last <= gap) {
                    for (int fill = last + 1; fill < x; fill++) {
                        bands[y][fill] = true;
                    }
                }
                last = x;
            }
        }
        return bands;
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

    /**
     * Cheapest paths from the left edge to every pixel, column by column; a path moves at most one row per column.
     *
     * @param costs filled with the cost of the cheapest path ending at each row of the right edge
     * @return for each column and row the row of the path in the previous column: [x][y]
     */
    private static int[][] seams(boolean[][] bands, double[] costs) {
        final int height = bands.length;
        final int width = bands[0].length;
        final int[][] from = new int[width][height];
        double[] previous = new double[height];
        for (int y = 0; y < height; y++) {
            previous[y] = bands[y][0] ? 1 : 0;
        }
        for (int x = 1; x < width; x++) {
            final double[] current = new double[height];
            final boolean step = x % STEP_RUN == 0;
            for (int y = 0; y < height; y++) {
                int best = y;
                double cost = previous[y];
                if (!step) {
                    from[x][y] = best;
                    current[y] = cost + (bands[y][x] ? 1 : 0);
                    continue;
                }
                if (y > 0 && previous[y - 1] + STEP_COST < cost) {
                    best = y - 1;
                    cost = previous[y - 1] + STEP_COST;
                }
                if (y < height - 1 && previous[y + 1] + STEP_COST < cost) {
                    best = y + 1;
                    cost = previous[y + 1] + STEP_COST;
                }
                from[x][y] = best;
                current[y] = cost + (bands[y][x] ? 1 : 0);
            }
            previous = current;
        }
        System.arraycopy(previous, 0, costs, 0, height);
        return from;
    }

    private static int[] backtrack(int[][] from, int end) {
        final int[] seam = new int[from.length];
        seam[from.length - 1] = end;
        for (int x = from.length - 1; x > 0; x--) {
            seam[x - 1] = from[x][seam[x]];
        }
        return seam;
    }

    private static double[] straightCosts(boolean[][] bands) {
        final double[] costs = new double[bands.length];
        for (int y = 0; y < bands.length; y++) {
            for (boolean ink : bands[y]) {
                costs[y] += ink ? 1 : 0;
            }
        }
        return costs;
    }

    /**
     * @return runs of rows whose cut crosses no ink: [first, last]
     */
    private static List<int[]> zones(double[] costs) {
        final List<int[]> zones = new ArrayList<>();
        int start = -1;
        for (int y = 0; y <= costs.length; y++) {
            // the steps add less than one pixel of ink
            final boolean clean = y < costs.length && costs[y] < 1;
            if (clean && start < 0) {
                start = y;
            } else if (!clean && start >= 0) {
                zones.add(new int[]{start, y - 1});
                start = -1;
            }
        }
        return zones;
    }
}
