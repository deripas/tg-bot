package com.github.deripas.receipt.scan.boofcv;

import georegression.struct.point.Point2D_F64;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Geometry of contours that BoofCV does not offer in the form the detector needs: the min-area rectangle and
 * the Douglas-Peucker approximation with a given tolerance, as in OpenCV.
 */
@UtilityClass
class Polygons {

    /**
     * @return area of the closed polygon
     */
    static double area(List<Point2D_F64> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            final Point2D_F64 a = points.get(i);
            final Point2D_F64 b = points.get((i + 1) % points.size());
            sum += a.x * b.y - b.x * a.y;
        }
        return Math.abs(sum) / 2;
    }

    static double perimeter(List<Point2D_F64> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            sum += points.get(i).distance(points.get((i + 1) % points.size()));
        }
        return sum;
    }

    /**
     * Andrew's monotone chain.
     *
     * @return the convex hull counterclockwise in image coordinates, without collinear points
     */
    static List<Point2D_F64> convexHull(List<Point2D_F64> points) {
        final List<Point2D_F64> sorted = points.stream()
                .sorted(Comparator.<Point2D_F64>comparingDouble(p -> p.x).thenComparingDouble(p -> p.y))
                .toList();
        if (sorted.size() < 3) {
            return sorted;
        }
        final List<Point2D_F64> hull = new ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            final int start = hull.size();
            for (int i = 0; i < sorted.size(); i++) {
                final Point2D_F64 p = sorted.get(pass == 0 ? i : sorted.size() - 1 - i);
                while (hull.size() >= start + 2 && cross(hull.get(hull.size() - 2), hull.getLast(), p) <= 0) {
                    hull.removeLast();
                }
                hull.add(p);
            }
            // the last point of a chain is the first of the other one
            hull.removeLast();
        }
        return hull;
    }

    /**
     * Rotating calipers: the min-area rectangle has a side on a side of the convex hull.
     */
    static Rectangle minAreaRectangle(List<Point2D_F64> hull) {
        Rectangle best = null;
        for (int i = 0; i < hull.size(); i++) {
            final Point2D_F64 a = hull.get(i);
            final Point2D_F64 b = hull.get((i + 1) % hull.size());
            final double length = a.distance(b);
            if (length == 0) {
                continue;
            }
            final double ux = (b.x - a.x) / length;
            final double uy = (b.y - a.y) / length;
            double minU = Double.MAX_VALUE;
            double maxU = -Double.MAX_VALUE;
            double minV = Double.MAX_VALUE;
            double maxV = -Double.MAX_VALUE;
            for (Point2D_F64 p : hull) {
                final double u = (p.x - a.x) * ux + (p.y - a.y) * uy;
                final double v = -(p.x - a.x) * uy + (p.y - a.y) * ux;
                minU = Math.min(minU, u);
                maxU = Math.max(maxU, u);
                minV = Math.min(minV, v);
                maxV = Math.max(maxV, v);
            }
            final double area = (maxU - minU) * (maxV - minV);
            if (best == null || area < best.area()) {
                best = new Rectangle(new Point2D_F64[]{
                        point(a, ux, uy, minU, minV), point(a, ux, uy, maxU, minV),
                        point(a, ux, uy, maxU, maxV), point(a, ux, uy, minU, maxV)}, area);
            }
        }
        if (best == null) {
            // a degenerate hull: all points coincide
            final Point2D_F64 p = hull.isEmpty() ? new Point2D_F64() : hull.getFirst();
            return new Rectangle(new Point2D_F64[]{p.copy(), p.copy(), p.copy(), p.copy()}, 0);
        }
        return best;
    }

    /**
     * Approximates the convex hull with 4 points, increasing the tolerance until it fits.
     */
    static Optional<Point2D_F64[]> quadrilateral(List<Point2D_F64> hull) {
        final double perimeter = perimeter(hull);
        for (double epsilon = 0.01; epsilon <= 0.1; epsilon += 0.01) {
            final List<Point2D_F64> approx = approximate(hull, epsilon * perimeter);
            if (approx.size() == 4) {
                return Optional.of(approx.toArray(Point2D_F64[]::new));
            }
            if (approx.size() < 4) {
                break;
            }
        }
        return Optional.empty();
    }

    /**
     * Douglas-Peucker for a closed polygon: split at two points far from each other, as OpenCV does,
     * then simplify both chains.
     */
    static List<Point2D_F64> approximate(List<Point2D_F64> polygon, double tolerance) {
        if (polygon.size() < 3) {
            return polygon;
        }
        // a few steps to the farthest point lead to two points nearly a diameter apart
        int start = 0;
        int far = farthest(polygon, start);
        for (int i = 0; i < 3; i++) {
            start = far;
            far = farthest(polygon, start);
        }
        final List<Point2D_F64> closed = new ArrayList<>(polygon.size() + 1);
        for (int i = 0; i <= polygon.size(); i++) {
            closed.add(polygon.get((start + i) % polygon.size()));
        }
        final int split = Math.floorMod(far - start, polygon.size());
        final List<Point2D_F64> result = new ArrayList<>();
        simplify(closed, 0, split, tolerance, result);
        simplify(closed, split, closed.size() - 1, tolerance, result);
        return result;
    }

    private static int farthest(List<Point2D_F64> points, int from) {
        final Point2D_F64 origin = points.get(from);
        int far = from;
        for (int i = 0; i < points.size(); i++) {
            if (origin.distance2(points.get(i)) > origin.distance2(points.get(far))) {
                far = i;
            }
        }
        return far;
    }

    /**
     * Adds the kept points of the chain from {@code from} inclusive to {@code to} exclusive.
     */
    private static void simplify(List<Point2D_F64> points, int from, int to, double tolerance, List<Point2D_F64> result) {
        final Point2D_F64 a = points.get(from);
        final Point2D_F64 b = points.get(to);
        int farthest = -1;
        double max = tolerance;
        for (int i = from + 1; i < to; i++) {
            final double distance = distanceToSegment(points.get(i), a, b);
            if (distance > max) {
                max = distance;
                farthest = i;
            }
        }
        if (farthest < 0) {
            result.add(a);
        } else {
            simplify(points, from, farthest, tolerance, result);
            simplify(points, farthest, to, tolerance, result);
        }
    }

    private static double distanceToSegment(Point2D_F64 p, Point2D_F64 a, Point2D_F64 b) {
        final double dx = b.x - a.x;
        final double dy = b.y - a.y;
        final double length2 = dx * dx + dy * dy;
        if (length2 == 0) {
            return p.distance(a);
        }
        final double t = Math.clamp(((p.x - a.x) * dx + (p.y - a.y) * dy) / length2, 0, 1);
        return Math.hypot(p.x - a.x - t * dx, p.y - a.y - t * dy);
    }

    private static double cross(Point2D_F64 o, Point2D_F64 a, Point2D_F64 b) {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x);
    }

    private static Point2D_F64 point(Point2D_F64 origin, double ux, double uy, double u, double v) {
        return new Point2D_F64(origin.x + u * ux - v * uy, origin.y + u * uy + v * ux);
    }

    /**
     * @param corners 4 corners in order around the rectangle
     */
    record Rectangle(Point2D_F64[] corners, double area) {
    }
}
