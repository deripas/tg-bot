package com.github.deripas.receipt.scan.opencv;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.Corners;
import com.github.deripas.receipt.scan.ReceiptDetector;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds the receipt as the largest of the most rectangular regions of a thresholded image, and snaps its sides
 * to straight paper edges. Tries both polarities and many thresholds, since the receipt can be lighter or darker
 * than the background.
 */
public class OpenCvReceiptDetector implements ReceiptDetector {

    private static final double WORK_SIZE = 800;
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
        return image.transform()
                .as(OpenCvReceiptDetector::findReceipt)
                .map(Corners::toPolygon);
    }

    /**
     * Returns 4 corners of the receipt in the coordinates of the source image.
     */
    static Optional<Point[]> findReceipt(Mat image) {
        // 1. Downscale: faster and less noise
        final double scale = WORK_SIZE / Math.max(image.width(), image.height());
        final Mat small = new Mat();
        Imgproc.resize(image, small, new Size(), scale, scale, Imgproc.INTER_AREA);

        final Mat gray = new Mat();
        if (small.channels() == 1) {
            small.copyTo(gray);
        } else {
            Imgproc.cvtColor(small, gray, Imgproc.COLOR_BGR2GRAY);
        }
        Imgproc.GaussianBlur(gray, gray, new Size(5, 5), 0);

        // 2. The receipt can be lighter or darker than the background, so try both polarities
        // and many thresholds, keeping the largest region among those closest to a rectangle
        return findReceiptRegion(gray).map(contour -> {
            // 3. Fit a quadrilateral, otherwise use the min-area rectangle
            final Point[] rough = quadrilateral(contour)
                    .orElseGet(() -> {
                        final Point[] points = new Point[4];
                        Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray())).points(points);
                        return points;
                    });

            // 4. The mask may leak into the background, so snap the sides to the real paper edges
            final Point[] corners = EdgeRefiner.refine(gray, Corners.order(rough));

            // 5. Back to source image coordinates
            for (Point point : corners) {
                point.x /= scale;
                point.y /= scale;
            }
            return corners;
        });
    }

    private static Optional<MatOfPoint> findReceiptRegion(Mat gray) {
        final double total = gray.total();
        final double size = Math.max(gray.width(), gray.height());
        // Close fills the text inside the receipt; open removes background patterns thinner than the kernel
        final Mat close = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(size * 0.02, size * 0.02));
        final Mat open = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(size * 0.04, size * 0.04));

        final List<Region> regions = new ArrayList<>();
        for (int threshold = MIN_THRESHOLD; threshold <= MAX_THRESHOLD; threshold += THRESHOLD_STEP) {
            for (int type : new int[]{Imgproc.THRESH_BINARY, Imgproc.THRESH_BINARY_INV}) {
                final Mat mask = new Mat();
                Imgproc.threshold(gray, mask, threshold, 255, type);
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, close);
                Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, open);

                final List<MatOfPoint> contours = new ArrayList<>();
                Imgproc.findContours(mask, contours, new Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
                for (MatOfPoint contour : contours) {
                    final double area = Imgproc.contourArea(contour);
                    // Too small is noise, too large is the background around the receipt
                    if (area < MIN_AREA * total || area > MAX_AREA * total) {
                        continue;
                    }
                    // Background between the receipt and the frame looks like a perfect rectangle because the frame
                    // edges are straight, but a large part of its perimeter lies on the frame; a receipt is only cut
                    // by the frame on its short sides
                    if (borderFraction(contour, gray) > MAX_BORDER_FRACTION) {
                        continue;
                    }
                    final double rectangularity = area / Imgproc.minAreaRect(new MatOfPoint2f(contour.toArray())).size.area();
                    regions.add(new Region(contour, area, rectangularity));
                }
            }
        }
        final double best = regions.stream().mapToDouble(Region::rectangularity).max().orElse(0);
        return regions.stream()
                .filter(region -> region.rectangularity() >= best - RECTANGULARITY_SLACK)
                .max(Comparator.comparingDouble(Region::area))
                .map(Region::contour);
    }

    /**
     * Returns the share of the bounding box perimeter that lies on the image border.
     * The bounding box is used because the contour itself is jagged after morphology.
     */
    private static double borderFraction(MatOfPoint contour, Mat image) {
        final Rect box = Imgproc.boundingRect(contour);
        double onBorder = 0;
        if (box.x <= 1) {
            onBorder += box.height;
        }
        if (box.y <= 1) {
            onBorder += box.width;
        }
        if (box.x + box.width >= image.width() - 1) {
            onBorder += box.height;
        }
        if (box.y + box.height >= image.height() - 1) {
            onBorder += box.width;
        }
        return onBorder / (2.0 * (box.width + box.height));
    }

    /**
     * Approximates the convex hull with 4 points, increasing the tolerance until it fits.
     */
    private static Optional<Point[]> quadrilateral(MatOfPoint contour) {
        final Point[] array = extractPoints(contour);
        final MatOfPoint2f hull = new MatOfPoint2f(array);
        final double perimeter = Imgproc.arcLength(hull, true);
        final MatOfPoint2f approx = new MatOfPoint2f();
        for (double epsilon = 0.01; epsilon <= 0.1; epsilon += 0.01) {
            Imgproc.approxPolyDP(hull, approx, epsilon * perimeter, true);
            if (approx.total() == 4) {
                return Optional.of(approx.toArray());
            }
            if (approx.total() < 4) {
                break;
            }
        }
        return Optional.empty();
    }

    private static Point[] extractPoints(MatOfPoint contour) {
        final MatOfInt indexes = new MatOfInt();
        Imgproc.convexHull(contour, indexes);
        final Point[] points = contour.toArray();
        return indexes.toList()
                .stream()
                .map(i -> points[i])
                .toArray(Point[]::new);
    }

    /**
     * @param rectangularity area of the region divided by the area of its min-area rectangle
     */
    private record Region(MatOfPoint contour, double area, double rectangularity) {
    }
}
