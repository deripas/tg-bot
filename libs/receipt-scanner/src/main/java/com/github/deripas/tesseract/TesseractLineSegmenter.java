package com.github.deripas.tesseract;

import com.github.deripas.image.ImageData;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.tesseract.PageIterator;
import org.bytedeco.tesseract.TessBaseAPI;

import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;

import static org.bytedeco.tesseract.global.tesseract.PSM_SINGLE_BLOCK;
import static org.bytedeco.tesseract.global.tesseract.RIL_TEXTLINE;
import static org.bytedeco.tesseract.global.tesseract.TessPageIteratorDelete;

/**
 * Finds the text lines with the layout analysis of Tesseract, without recognizing the text: no language data
 * is needed, and a call takes far less time than recognition. Works on the receipt cut out of the photo
 * and turned upright.
 * <p>
 * Calls are synchronized: Tesseract is not thread-safe.
 */
@Slf4j
public class TesseractLineSegmenter implements AutoCloseable {

    /**
     * Photos carry no resolution, and Tesseract falls back to 70 dpi with a warning; set it to avoid the warning.
     */
    private static final int RESOLUTION = 70;

    private final TessBaseAPI api = new TessBaseAPI();

    public TesseractLineSegmenter() {
        api.InitForAnalysePage();
        // a receipt is a single column of lines
        api.SetPageSegMode(PSM_SINGLE_BLOCK);
    }

    /**
     * @return the text lines top to bottom, in the coordinates of the image
     */
    public synchronized List<TextLine> lines(ImageData image) {
        final int width = image.getWidth();
        final int height = image.getHeight();
        final int channels = image.getType().getChannels();
        api.SetImage(image.pixels(), width, height, channels, width * channels);
        api.SetSourceResolution(RESOLUTION);

        final PageIterator iterator = api.AnalyseLayout();
        try {
            if (iterator == null) {
                return List.of();
            }
            final List<TextLine> lines = new ArrayList<>();
            final int[] left = new int[1];
            final int[] top = new int[1];
            final int[] right = new int[1];
            final int[] bottom = new int[1];
            final int[] x1 = new int[1];
            final int[] y1 = new int[1];
            final int[] x2 = new int[1];
            final int[] y2 = new int[1];

            do {
                if (iterator.BoundingBox(RIL_TEXTLINE, left, top, right, bottom)) {
                    final Rectangle box = new Rectangle(left[0], top[0], right[0] - left[0], bottom[0] - top[0]);
                    final Line2D baseline = iterator.Baseline(RIL_TEXTLINE, x1, y1, x2, y2)
                            ? new Line2D.Double(x1[0], y1[0], x2[0], y2[0])
                            // no baseline: the bottom of the box
                            : new Line2D.Double(box.getMinX(), box.getMaxY(), box.getMaxX(), box.getMaxY());
                    lines.add(new TextLine(box, baseline));
                }
            } while (iterator.Next(RIL_TEXTLINE));
            log.debug("Text lines: {}", lines.size());
            return lines;
        } finally {
            // the iterator is owned by the caller, and the Java wrapper does not free it
            if (iterator != null) {
                TessPageIteratorDelete(iterator);
            }
            api.Clear();
        }
    }

    @Override
    public synchronized void close() {
        api.End();
        api.close();
    }

    /**
     * @param box      bounding box; on a skewed line it is taller than the line and overlaps the neighbours
     * @param baseline the line the letters stand on, from the left end to the right one: keeps the skew
     */
    public record TextLine(Rectangle box, Line2D baseline) {

        /**
         * @return the height of the baseline at the column x, extended beyond its ends
         */
        public double baselineAt(double x) {
            return baseline.getY1() + slope() * (x - baseline.getX1());
        }

        /**
         * @return the slope of the baseline, rows per column
         */
        public double slope() {
            final double dx = baseline.getX2() - baseline.getX1();
            return dx == 0 ? 0 : (baseline.getY2() - baseline.getY1()) / dx;
        }
    }
}
