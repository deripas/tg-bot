package com.github.deripas.image;

import lombok.experimental.UtilityClass;
import org.opencv.core.Point;

import java.awt.Polygon;
import java.util.Comparator;
import java.util.List;

/**
 * Corners of a quadrilateral, e.g. of a receipt on a photo.
 */
@UtilityClass
public class Corners {

    /**
     * @return the 4 corners ordered as top-left, top-right, bottom-right, bottom-left
     */
    public static Point[] toPoints(Polygon polygon) {
        final Point[] points = new Point[polygon.npoints];
        for (int i = 0; i < polygon.npoints; i++) {
            points[i] = new Point(polygon.xpoints[i], polygon.ypoints[i]);
        }
        return order(points);
    }

    /**
     * @return the 4 corners ordered as top-left, top-right, bottom-right, bottom-left
     */
    public static Polygon toPolygon(Point[] points) {
        final Polygon polygon = new Polygon();
        for (Point point : order(points)) {
            polygon.addPoint((int) Math.round(point.x), (int) Math.round(point.y));
        }
        return polygon;
    }

    /**
     * Orders corners as top-left, top-right, bottom-right, bottom-left.
     */
    public static Point[] order(Point[] corners) {
        if (corners.length != 4) {
            throw new IllegalArgumentException("Expected 4 corners, got " + corners.length);
        }
        final Comparator<Point> sum = Comparator.comparingDouble(p -> p.x + p.y);
        final Comparator<Point> diff = Comparator.comparingDouble(p -> p.y - p.x);
        final List<Point> points = List.of(corners);
        return new Point[]{
                points.stream().min(sum).orElseThrow(),
                points.stream().min(diff).orElseThrow(),
                points.stream().max(sum).orElseThrow(),
                points.stream().max(diff).orElseThrow()
        };
    }
}
