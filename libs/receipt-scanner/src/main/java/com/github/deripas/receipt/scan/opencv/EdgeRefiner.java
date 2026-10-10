package com.github.deripas.receipt.scan.opencv;

import lombok.experimental.UtilityClass;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Refines rough receipt corners by snapping each side to the straight paper edge found with Canny + HoughLinesP.
 * The rough corners come from a threshold mask, which may leak into a background of similar brightness.
 */
@UtilityClass
class EdgeRefiner {

    /**
     * Half-width of the search band around a rough side, as a share of the larger image side.
     */
    private static final double BAND = 0.05;
    private static final double CANNY_LOW = 30;
    private static final double CANNY_HIGH = 90;
    private static final int HOUGH_THRESHOLD = 20;
    private static final double MIN_SEGMENT = 0.1;
    private static final double MAX_GAP = 0.02;
    private static final double MAX_ANGLE = Math.toRadians(10);
    /**
     * Distance from a line to the points where the brightness is compared on both sides of it.
     */
    private static final double CONTRAST_OFFSET = 3;
    private static final int CONTRAST_SAMPLES = 100;

    /**
     * @param gray    blurred grayscale image
     * @param corners rough corners ordered as top-left, top-right, bottom-right, bottom-left
     * @return refined corners in the same order
     */
    static Point[] refine(Mat gray, Point[] corners) {
        final Mat edges = new Mat();
        Imgproc.Canny(gray, edges, CANNY_LOW, CANNY_HIGH);
        final double band = BAND * Math.max(gray.width(), gray.height());
        final Point center = center(corners);

        final Line[] sides = new Line[4];
        for (int i = 0; i < 4; i++) {
            final Point a = corners[i];
            final Point b = corners[(i + 1) % 4];
            // A side cut by the frame has no edge to snap to
            sides[i] = onSameBorder(a, b, gray)
                    ? Line.through(a, b)
                    : findEdge(gray, edges, a, b, center, band);
        }

        final Point[] refined = new Point[4];
        for (int i = 0; i < 4; i++) {
            // Corner i joins the previous side and side i
            final Point corner = sides[(i + 3) % 4].intersect(sides[i]).orElse(null);
            // Nearly parallel lines may intersect far away; keep the rough corner then
            refined[i] = corner != null && distance(corner, corners[i]) <= 2 * band ? corner : corners[i];
        }
        return refined;
    }

    /**
     * Among the rough side and straight segments within the band around it, picks the line with the strongest
     * brightness difference between its inner and outer sides. The paper edge separates the receipt from the
     * background along its whole length, while text columns have paper on both sides and background patterns
     * change the sign of the difference.
     */
    private static Line findEdge(Mat gray, Mat edges, Point a, Point b, Point center, double band) {
        final Line rough = Line.through(a, b);
        Line best = rough;
        double bestContrast = contrast(gray, rough, a, b, center);
        for (Line candidate : segments(edges, a, b, band)) {
            final double contrast = contrast(gray, candidate, a, b, center);
            if (contrast > bestContrast) {
                bestContrast = contrast;
                best = candidate;
            }
        }
        return best;
    }

    private static List<Line> segments(Mat edges, Point a, Point b, double band) {
        final Mat mask = Mat.zeros(edges.size(), edges.type());
        Imgproc.line(mask, a, b, new Scalar(255), (int) Math.ceil(2 * band));
        final Mat bandEdges = new Mat();
        Core.bitwise_and(edges, mask, bandEdges);

        final double size = Math.max(edges.width(), edges.height());
        final Mat found = new Mat();
        Imgproc.HoughLinesP(bandEdges, found, 1, Math.PI / 180, HOUGH_THRESHOLD,
                Math.max(10, MIN_SEGMENT * distance(a, b)), MAX_GAP * size);

        final double direction = Math.atan2(b.y - a.y, b.x - a.x);
        final List<Line> lines = new ArrayList<>();
        for (int i = 0; i < found.rows(); i++) {
            final double[] l = found.get(i, 0);
            final Point p = new Point(l[0], l[1]);
            final Point q = new Point(l[2], l[3]);
            if (angleBetween(direction, Math.atan2(q.y - p.y, q.x - p.x)) <= MAX_ANGLE) {
                lines.add(Line.through(p, q));
            }
        }
        return lines;
    }

    /**
     * Mean brightness difference between the inner and outer sides of the line, sampled along the span of the
     * rough side a-b. The sign is kept while averaging, so a line that is lighter inside on one half and darker
     * on the other scores close to zero.
     */
    private static double contrast(Mat gray, Line line, Point a, Point b, Point center) {
        final Point from = line.project(a);
        final Point to = line.project(b);
        // Normal pointing towards the receipt center
        final double side = Math.signum(line.signedDistance(center));
        final double nx = -line.dy() * side;
        final double ny = line.dx() * side;

        double sum = 0;
        int count = 0;
        for (int i = 0; i <= CONTRAST_SAMPLES; i++) {
            final double t = (double) i / CONTRAST_SAMPLES;
            final double x = from.x + t * (to.x - from.x);
            final double y = from.y + t * (to.y - from.y);
            final Optional<Double> inner = pixel(gray, x + CONTRAST_OFFSET * nx, y + CONTRAST_OFFSET * ny);
            final Optional<Double> outer = pixel(gray, x - CONTRAST_OFFSET * nx, y - CONTRAST_OFFSET * ny);
            if (inner.isPresent() && outer.isPresent()) {
                sum += inner.get() - outer.get();
                count++;
            }
        }
        return count == 0 ? 0 : Math.abs(sum / count);
    }

    private static Optional<Double> pixel(Mat gray, double x, double y) {
        final int col = (int) Math.round(x);
        final int row = (int) Math.round(y);
        if (col < 0 || row < 0 || col >= gray.width() || row >= gray.height()) {
            return Optional.empty();
        }
        return Optional.of(gray.get(row, col)[0]);
    }

    private static Point center(Point[] corners) {
        double x = 0;
        double y = 0;
        for (Point corner : corners) {
            x += corner.x;
            y += corner.y;
        }
        return new Point(x / corners.length, y / corners.length);
    }

    private static double angleBetween(double a, double b) {
        // Lines have no direction, so compare modulo PI
        final double diff = Math.abs(a - b) % Math.PI;
        return Math.min(diff, Math.PI - diff);
    }

    private static boolean onSameBorder(Point a, Point b, Mat image) {
        final double right = image.width() - 2;
        final double bottom = image.height() - 2;
        return (a.x <= 1 && b.x <= 1)
                || (a.y <= 1 && b.y <= 1)
                || (a.x >= right && b.x >= right)
                || (a.y >= bottom && b.y >= bottom);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }

    /**
     * Infinite line through the point (x, y) with the unit direction (dx, dy).
     */
    private record Line(double x, double y, double dx, double dy) {

        static Line through(Point a, Point b) {
            final double length = EdgeRefiner.distance(a, b);
            return new Line(a.x, a.y, (b.x - a.x) / length, (b.y - a.y) / length);
        }

        double signedDistance(Point p) {
            return (p.y - y) * dx - (p.x - x) * dy;
        }

        Point project(Point p) {
            final double t = (p.x - x) * dx + (p.y - y) * dy;
            return new Point(x + t * dx, y + t * dy);
        }

        Optional<Point> intersect(Line other) {
            final double det = dx * other.dy - dy * other.dx;
            if (Math.abs(det) < 1e-6) {
                return Optional.empty();
            }
            final double t = ((other.x - x) * other.dy - (other.y - y) * other.dx) / det;
            return Optional.of(new Point(x + t * dx, y + t * dy));
        }
    }
}
