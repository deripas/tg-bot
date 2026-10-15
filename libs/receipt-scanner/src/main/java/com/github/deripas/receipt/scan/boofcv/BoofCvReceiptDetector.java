package com.github.deripas.receipt.scan.boofcv;

import boofcv.abst.distort.FDistort;
import boofcv.alg.filter.binary.BinaryImageOps;
import boofcv.alg.filter.binary.Contour;
import boofcv.alg.filter.binary.ThresholdImageOps;
import boofcv.alg.filter.blur.BlurImageOps;
import boofcv.alg.filter.misc.AverageDownSampleOps;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.struct.ConnectRule;
import boofcv.struct.image.GrayU8;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.scan.ReceiptDetector;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point2D_I32;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the receipt as the largest of the most rectangular regions of a thresholded image, and snaps its sides
 * to straight paper edges. Tries both polarities and many thresholds, since the receipt can be lighter or darker
 * than the background.
 * <p>
 * The same algorithm as the OpenCV detector, in pure Java with BoofCV.
 */
public class BoofCvReceiptDetector implements ReceiptDetector {

    private static final double WORK_SIZE = 800;
    /**
     * Sigma that OpenCV takes for a 5x5 Gaussian kernel.
     */
    private static final double BLUR_SIGMA = 1.1;
    private static final int MIN_THRESHOLD = 40;
    private static final int MAX_THRESHOLD = 220;
    private static final int THRESHOLD_STEP = 10;
    private static final double MIN_AREA = 0.05;
    private static final double MAX_AREA = 0.8;
    private static final double MAX_BORDER_FRACTION = 0.35;
    /**
     * Regions less rectangular than the best one by at most this are considered equal, and the largest wins:
     * a straight piece of a receipt cut off by a threshold is more rectangular than the whole bent receipt.
     */
    private static final double RECTANGULARITY_SLACK = 0.1;

    @Override
    public Optional<Polygon> detect(ImageData image) {
        final GrayU8 gray = ConvertBufferedImage.convertFrom(image.toImage(), (GrayU8) null);
        return findReceipt(gray).map(BoofCvReceiptDetector::toPolygon);
    }

    /**
     * Returns 4 corners of the receipt in the coordinates of the source image.
     */
    static Optional<Point2D_F64[]> findReceipt(GrayU8 image) {
        // 1. Downscale: faster and less noise
        final double scale = WORK_SIZE / Math.max(image.width, image.height);
        final GrayU8 small = new GrayU8(
                Math.max(1, (int) Math.round(image.width * scale)),
                Math.max(1, (int) Math.round(image.height * scale))
        );
        if (scale < 1) {
            AverageDownSampleOps.down(image, small);
        } else {
            new FDistort(image, small).scaleExt().apply();
        }
        final GrayU8 gray = BlurImageOps.gaussian(small, null, BLUR_SIGMA, 2, null);

        // 2. The receipt can be lighter or darker than the background, so try both polarities
        // and many thresholds, keeping the largest region among those closest to a rectangle
        return findReceiptRegion(gray).map(region -> {
            // 3. Fit a quadrilateral, otherwise use the min-area rectangle
            final Point2D_F64[] rough = Polygons.quadrilateral(region.hull())
                    .orElseGet(() -> region.rectangle().corners());

            // 4. The mask may leak into the background, so snap the sides to the real paper edges
            final Point2D_F64[] corners = EdgeRefiner.refine(gray, order(rough));

            // 5. Back to source image coordinates
            for (Point2D_F64 point : corners) {
                point.x /= scale;
                point.y /= scale;
            }
            return corners;
        });
    }

    private static Optional<Region> findReceiptRegion(GrayU8 gray) {
        final double total = (double) gray.width * gray.height;
        final double size = Math.max(gray.width, gray.height);
        // Close fills the text inside the receipt; open removes background patterns thinner than the kernel
        final int close = (int) (size * 0.02);
        final int open = (int) (size * 0.04);

        final GrayU8 mask = gray.createSameShape();
        final Morphology morphology = new Morphology(gray.width, gray.height);
        final List<Region> regions = new ArrayList<>();
        for (int threshold = MIN_THRESHOLD; threshold <= MAX_THRESHOLD; threshold += THRESHOLD_STEP) {
            // not inverted: brighter than the threshold; inverted: not brighter
            for (boolean inverted : new boolean[]{false, true}) {
                ThresholdImageOps.threshold(gray, mask, threshold, inverted);
                morphology.close(mask, close);
                morphology.open(mask, open);

                for (Contour contour : BinaryImageOps.contourExternal(mask, ConnectRule.EIGHT)) {
                    final List<Point2D_F64> points = contour.external.stream()
                            .map(p -> new Point2D_F64(p.x, p.y))
                            .toList();
                    final double area = Polygons.area(points);
                    // Too small is noise, too large is the background around the receipt
                    if (area < MIN_AREA * total || area > MAX_AREA * total) {
                        continue;
                    }
                    // Background between the receipt and the frame looks like a perfect rectangle because the frame
                    // edges are straight, but a large part of its perimeter lies on the frame; a receipt is only cut
                    // by the frame on its short sides
                    if (borderFraction(contour.external, gray) > MAX_BORDER_FRACTION) {
                        continue;
                    }
                    final List<Point2D_F64> hull = Polygons.convexHull(points);
                    final Polygons.Rectangle rectangle = Polygons.minAreaRectangle(hull);
                    regions.add(new Region(hull, rectangle, area, area / rectangle.area()));
                }
            }
        }
        final double best = regions.stream().mapToDouble(Region::rectangularity).max().orElse(0);
        return regions.stream()
                .filter(region -> region.rectangularity() >= best - RECTANGULARITY_SLACK)
                .max(Comparator.comparingDouble(Region::area));
    }

    /**
     * Returns the share of the bounding box perimeter that lies on the image border.
     * The bounding box is used because the contour itself is jagged after morphology.
     */
    private static double borderFraction(List<Point2D_I32> contour, GrayU8 image) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (Point2D_I32 p : contour) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        final int width = maxX - minX + 1;
        final int height = maxY - minY + 1;
        double onBorder = 0;
        if (minX <= 1) {
            onBorder += height;
        }
        if (minY <= 1) {
            onBorder += width;
        }
        if (maxX + 1 >= image.width - 1) {
            onBorder += height;
        }
        if (maxY + 1 >= image.height - 1) {
            onBorder += width;
        }
        return onBorder / (2.0 * (width + height));
    }

    /**
     * Orders corners as top-left, top-right, bottom-right, bottom-left.
     */
    static Point2D_F64[] order(Point2D_F64[] corners) {
        if (corners.length != 4) {
            throw new IllegalArgumentException("Expected 4 corners, got " + corners.length);
        }
        final Comparator<Point2D_F64> sum = Comparator.comparingDouble(p -> p.x + p.y);
        final Comparator<Point2D_F64> diff = Comparator.comparingDouble(p -> p.y - p.x);
        final List<Point2D_F64> points = List.of(corners);
        return new Point2D_F64[]{
                points.stream().min(sum).orElseThrow().copy(),
                points.stream().min(diff).orElseThrow().copy(),
                points.stream().max(sum).orElseThrow().copy(),
                points.stream().max(diff).orElseThrow().copy()
        };
    }

    private static Polygon toPolygon(Point2D_F64[] corners) {
        final Polygon polygon = new Polygon();
        for (Point2D_F64 point : order(corners)) {
            polygon.addPoint((int) Math.round(point.x), (int) Math.round(point.y));
        }
        return polygon;
    }

    /**
     * @param rectangularity area of the region divided by the area of its min-area rectangle
     */
    private record Region(List<Point2D_F64> hull, Polygons.Rectangle rectangle, double area, double rectangularity) {
    }
}
