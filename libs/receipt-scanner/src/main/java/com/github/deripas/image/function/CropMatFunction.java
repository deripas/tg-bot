package com.github.deripas.image.function;

import com.github.deripas.image.Corners;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.awt.Polygon;
import java.util.function.Function;

/**
 * Crops the quadrilateral and maps it onto a straight rectangle; the rectangle keeps the orientation of
 * the quadrilateral in the image, the text may still be turned.
 */
@ToString
@RequiredArgsConstructor
public class CropMatFunction implements MatFunction {

    private final Polygon corners;

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        final Point[] src = Corners.toPoints(corners);
        final Point tl = src[0], tr = src[1], br = src[2], bl = src[3];

        // Target size is the longest of the opposite edges
        final double width = Math.max(distance(tl, tr), distance(bl, br));
        final double height = Math.max(distance(tl, bl), distance(tr, br));
        final Point[] dst = {
                new Point(0, 0),
                new Point(width - 1, 0),
                new Point(width - 1, height - 1),
                new Point(0, height - 1)
        };

        return withMat(perspective(src, dst), transform -> withMat(out -> {
            Imgproc.warpPerspective(
                    in, out, transform,
                    new Size(width, height),
                    Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE
            );
            return response(out, function);
        }));
    }

    private static Mat perspective(Point[] src, Point[] dst) {
        final MatOfPoint2f from = new MatOfPoint2f(src);
        final MatOfPoint2f to = new MatOfPoint2f(dst);
        try {
            return Imgproc.getPerspectiveTransform(from, to);
        } finally {
            from.release();
            to.release();
        }
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }
}
