package com.github.deripas.receipt.scan.boofcv;

import boofcv.alg.feature.detect.edge.CannyEdge;
import boofcv.factory.feature.detect.edge.FactoryEdgeDetectors;
import boofcv.struct.image.GrayS16;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_F64;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Refines rough receipt corners by snapping each side to the straight paper edge found with Canny and a Hough
 * transform limited to the band around the side. The rough corners come from a threshold mask, which may leak
 * into a background of similar brightness.
 */
@UtilityClass
class EdgeRefiner {

    /**
     * Half-width of the search band around a rough side, as a share of the larger image side.
     */
    private static final double BAND = 0.05;
    /**
     * Thresholds of the gradient of BoofCV ([-1, 0, 1]); the Sobel gradient of OpenCV is about 4 times larger.
     */
    private static final float CANNY_LOW = 7.5f;
    private static final float CANNY_HIGH = 22.5f;
    private static final int HOUGH_THRESHOLD = 20;
    private static final double MIN_SEGMENT = 0.1;
    private static final double MAX_ANGLE = Math.toRadians(10);
    private static final double ANGLE_STEP = Math.toRadians(0.5);
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
    static Point2D_F64[] refine(GrayU8 gray, Point2D_F64[] corners) {
        final GrayU8 edges = gray.createSameShape();
        final CannyEdge<GrayU8, GrayS16> canny = FactoryEdgeDetectors.canny(1, false, false, GrayU8.class, GrayS16.class);
        canny.process(gray, CANNY_LOW, CANNY_HIGH, edges);
        final double band = BAND * Math.max(gray.width, gray.height);
        final Point2D_F64 center = center(corners);

        final Line[] sides = new Line[4];
        for (int i = 0; i < 4; i++) {
            final Point2D_F64 a = corners[i];
            final Point2D_F64 b = corners[(i + 1) % 4];
            // A side cut by the frame has no edge to snap to
            sides[i] = onSameBorder(a, b, gray)
                    ? Line.through(a, b)
                    : findEdge(gray, edges, a, b, center, band);
        }

        final Point2D_F64[] refined = new Point2D_F64[4];
        for (int i = 0; i < 4; i++) {
            // Corner i joins the previous side and side i
            final Point2D_F64 corner = sides[(i + 3) % 4].intersect(sides[i]).orElse(null);
            // Nearly parallel lines may intersect far away; keep the rough corner then
            refined[i] = corner != null && corner.distance(corners[i]) <= 2 * band ? corner : corners[i].copy();
        }
        return refined;
    }

    /**
     * Among the rough side and straight lines of edges within the band around it, picks the line with the strongest
     * brightness difference between its inner and outer sides. The paper edge separates the receipt from the
     * background along its whole length, while text columns have paper on both sides and background patterns
     * change the sign of the difference.
     */
    private static Line findEdge(
            GrayU8 gray, GrayU8 edges,
            Point2D_F64 a, Point2D_F64 b, Point2D_F64 center,
            double band
    ) {
        final Line rough = Line.through(a, b);
        Line best = rough;
        double bestContrast = contrast(gray, rough, a, b, center);
        for (Line candidate : lines(edges, a, b, band)) {
            final double contrast = contrast(gray, candidate, a, b, center);
            if (contrast > bestContrast) {
                bestContrast = contrast;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Hough transform of the edge pixels within the band around the side a-b, only for directions close to it:
     * every line with enough edge pixels on it is a candidate.
     */
    private static List<Line> lines(
            GrayU8 edges,
            Point2D_F64 a, Point2D_F64 b,
            double band
    ) {
        final double direction = Math.atan2(b.y - a.y, b.x - a.x);
        final int angles = (int) Math.round(MAX_ANGLE / ANGLE_STEP);
        final double[] sin = new double[2 * angles + 1];
        final double[] cos = new double[2 * angles + 1];
        for (int k = 0; k < sin.length; k++) {
            final double angle = direction + (k - angles) * ANGLE_STEP;
            sin[k] = Math.sin(angle);
            cos[k] = Math.cos(angle);
        }
        // offsets of a line from the point a along its normal, one bin per pixel
        final int offsets = (int) Math.ceil(2 * band + a.distance(b) * Math.sin(MAX_ANGLE));
        final int[][] votes = new int[sin.length][2 * offsets + 1];

        final int minX = Math.max(0, (int) Math.floor(Math.min(a.x, b.x) - band));
        final int maxX = Math.min(edges.width - 1, (int) Math.ceil(Math.max(a.x, b.x) + band));
        final int minY = Math.max(0, (int) Math.floor(Math.min(a.y, b.y) - band));
        final int maxY = Math.min(edges.height - 1, (int) Math.ceil(Math.max(a.y, b.y) + band));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (edges.unsafe_get(x, y) == 0 || distanceToSegment(x, y, a, b) > band) {
                    continue;
                }
                for (int k = 0; k < sin.length; k++) {
                    final double offset = -(x - a.x) * sin[k] + (y - a.y) * cos[k];
                    final int bin = (int) Math.round(offset) + offsets;
                    if (bin >= 0 && bin < votes[k].length) {
                        votes[k][bin]++;
                    }
                }
            }
        }

        final double minVotes = Math.max(HOUGH_THRESHOLD, Math.max(10, MIN_SEGMENT * a.distance(b)));
        final List<Line> lines = new ArrayList<>();
        for (int k = 0; k < votes.length; k++) {
            for (int bin = 0; bin < votes[k].length; bin++) {
                if (votes[k][bin] >= minVotes) {
                    final double offset = bin - offsets;
                    lines.add(new Line(a.x - offset * sin[k], a.y + offset * cos[k], cos[k], sin[k]));
                }
            }
        }
        return lines;
    }

    /**
     * Mean brightness difference between the inner and outer sides of the line, sampled along the span of the
     * rough side a-b. The sign is kept while averaging, so a line that is lighter inside on one half and darker
     * on the other scores close to zero.
     */
    private static double contrast(GrayU8 gray, Line line, Point2D_F64 a, Point2D_F64 b, Point2D_F64 center) {
        final Point2D_F64 from = line.project(a);
        final Point2D_F64 to = line.project(b);
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
            final int inner = pixel(gray, x + CONTRAST_OFFSET * nx, y + CONTRAST_OFFSET * ny);
            final int outer = pixel(gray, x - CONTRAST_OFFSET * nx, y - CONTRAST_OFFSET * ny);
            if (inner >= 0 && outer >= 0) {
                sum += inner - outer;
                count++;
            }
        }
        return count == 0 ? 0 : Math.abs(sum / count);
    }

    /**
     * @return the brightness of the pixel, or -1 outside of the image
     */
    private static int pixel(GrayU8 gray, double x, double y) {
        final int col = (int) Math.round(x);
        final int row = (int) Math.round(y);
        if (col < 0 || row < 0 || col >= gray.width || row >= gray.height) {
            return -1;
        }
        return gray.unsafe_get(col, row);
    }

    private static Point2D_F64 center(Point2D_F64[] corners) {
        double x = 0;
        double y = 0;
        for (Point2D_F64 corner : corners) {
            x += corner.x;
            y += corner.y;
        }
        return new Point2D_F64(x / corners.length, y / corners.length);
    }

    private static double distanceToSegment(double x, double y, Point2D_F64 a, Point2D_F64 b) {
        final double dx = b.x - a.x;
        final double dy = b.y - a.y;
        final double length2 = dx * dx + dy * dy;
        final double t = length2 == 0 ? 0 : Math.clamp(((x - a.x) * dx + (y - a.y) * dy) / length2, 0, 1);
        return Math.hypot(x - a.x - t * dx, y - a.y - t * dy);
    }

    private static boolean onSameBorder(Point2D_F64 a, Point2D_F64 b, GrayU8 image) {
        final double right = image.width - 2;
        final double bottom = image.height - 2;
        return (a.x <= 1 && b.x <= 1)
                || (a.y <= 1 && b.y <= 1)
                || (a.x >= right && b.x >= right)
                || (a.y >= bottom && b.y >= bottom);
    }

    /**
     * Infinite line through the point (x, y) with the unit direction (dx, dy).
     */
    private record Line(double x, double y, double dx, double dy) {

        static Line through(Point2D_F64 a, Point2D_F64 b) {
            final double length = a.distance(b);
            return new Line(a.x, a.y, (b.x - a.x) / length, (b.y - a.y) / length);
        }

        double signedDistance(Point2D_F64 p) {
            return (p.y - y) * dx - (p.x - x) * dy;
        }

        Point2D_F64 project(Point2D_F64 p) {
            final double t = (p.x - x) * dx + (p.y - y) * dy;
            return new Point2D_F64(x + t * dx, y + t * dy);
        }

        Optional<Point2D_F64> intersect(Line other) {
            final double det = dx * other.dy - dy * other.dx;
            if (Math.abs(det) < 1e-6) {
                return Optional.empty();
            }
            final double t = ((other.x - x) * other.dy - (other.y - y) * other.dx) / det;
            return Optional.of(new Point2D_F64(x + t * dx, y + t * dy));
        }
    }
}
