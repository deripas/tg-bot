package com.github.deripas.tesseract;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import com.github.deripas.receipt.scan.boofcv.BoofCvReceiptDetector;
import com.github.deripas.tesseract.TesseractLineSegmenter.TextLine;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Prints the text lines Tesseract finds on the scanned receipts with their baselines, to see what tells
 * the lines of one item apart from the lines of the neighbouring items. Draws in {@code target/text-lines-*.png}:
 * red - boxes, green - baselines.
 */
public class TextLinesTester {

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
     * Lines narrower than this share of the receipt width are noise.
     */
    private static final double MIN_WIDTH = 0.04;
    /**
     * Where the cut runs between two baselines, as a share of the step from the upper one: below its descenders
     * and above the capitals of the lower line.
     */
    private static final double CUT = 0.3;

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
                final double middle = image.getWidth() / 2.0;
                final List<TextLine> lines = segmenter.lines(image).stream()
                        .filter(line -> line.box().width >= MIN_WIDTH * image.getWidth())
                        .sorted(Comparator.comparingDouble(line -> line.baselineAt(middle)))
                        .toList();

                System.out.printf("%n%s %dx%d: %d lines%n", path, image.getWidth(), image.getHeight(), lines.size());
                final boolean[] indented = indented(lines, INDENT * image.getWidth());
                final List<Integer> cuts = new ArrayList<>();
                for (int i = 0; i + 1 < lines.size(); i++) {
                    // the numbers of an item, then the name of the next one
                    if (indented[i] && !indented[i + 1]) {
                        cuts.add(i);
                    }
                }
                System.out.printf("  %d cuts between items%n", cuts.size());
                if (Boolean.getBoolean("verbose")) {
                    for (int i = 0; i < lines.size(); i++) {
                        final TextLine line = lines.get(i);
                        System.out.printf("%4d %5d %s %9.1f %s%n", i, line.box().x, indented[i] ? "  >>" : "    ",
                                line.baselineAt(middle), cuts.contains(i) ? "--- cut" : "");
                    }
                }
                draw(image, lines, cuts, "target/text-lines-" + path.substring(0, path.indexOf('/')) + ".png");
            }
        }
    }

    /**
     * The left margin of a line is the leftmost start among its neighbours, so that a curved left edge
     * does not look like an indent.
     */
    private static boolean[] indented(List<TextLine> lines, double indent) {
        final boolean[] indented = new boolean[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            int margin = Integer.MAX_VALUE;
            for (int j = Math.max(0, i - MARGIN_NEIGHBOURS); j <= Math.min(lines.size() - 1, i + MARGIN_NEIGHBOURS); j++) {
                margin = Math.min(margin, lines.get(j).box().x);
            }
            indented[i] = lines.get(i).box().x - margin > indent;
        }
        return indented;
    }

    private static void draw(ImageData image, List<TextLine> lines, List<Integer> cuts, String out) throws Exception {
        final BufferedImage canvas = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(image.toImage(), 0, 0, null);
        for (TextLine line : lines) {
            graphics.setColor(new Color(255, 0, 0, 90));
            graphics.draw(line.box());
            graphics.setColor(new Color(0, 160, 0));
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(line.baseline());
            graphics.setStroke(new BasicStroke(1));
        }
        graphics.setColor(Color.BLUE);
        graphics.setStroke(new BasicStroke(3));
        for (int i : cuts) {
            final TextLine upper = lines.get(i);
            final TextLine lower = lines.get(i + 1);
            final int width = image.getWidth();
            final double left = upper.baselineAt(0) + CUT * (lower.baselineAt(0) - upper.baselineAt(0));
            final double right = upper.baselineAt(width) + CUT * (lower.baselineAt(width) - upper.baselineAt(width));
            graphics.draw(new java.awt.geom.Line2D.Double(0, left, width, right));
        }
        graphics.dispose();
        ImageIO.write(canvas, "png", new File(out));
    }
}
