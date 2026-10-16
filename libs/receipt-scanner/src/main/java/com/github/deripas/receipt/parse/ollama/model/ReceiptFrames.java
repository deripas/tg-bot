package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.parse.ollama.model.ReceiptStrips.Strip;
import lombok.experimental.UtilityClass;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the strips of a receipt together into frames for the model, one after another without overlap:
 * a frame ends between items where it can, so that every item is whole in exactly one frame.
 * The receipt is scaled to the frame width, and a frame ends, in this order:
 * <ol>
 *     <li>at the last item end that fits, if the frame is at least half full;</li>
 *     <li>at the next item end, if the frame is shrunk to fit by {@link #FIT} at most;</li>
 *     <li>at the last item end that fits;</li>
 *     <li>at the next item end, if the frame is shrunk by {@link #MAX_SHRINK} at most: smaller characters
 *     are better than an item cut in two, up to a point;</li>
 *     <li>at the last cut between lines that fits: the cuts between items were not found there, e.g. along
 *     a background left by the crop, and much smaller characters would not be read at all.</li>
 * </ol>
 * A receipt up to {@link #FIT} frames is one frame, and so is a short rest with the frame before it:
 * slightly smaller characters cost less than one more request.
 * The strips are straightened, see {@link Strip#straighten(ImageData)}, so a frame shows straight lines
 * of a skewed or curved receipt.
 */
@UtilityClass
public class ReceiptFrames {

    /**
     * Height of a frame that may be shrunk into the frame instead of being split, relative to the frame.
     */
    public static final double FIT = 1.25;
    /**
     * A frame ending at an item end fills at least this part of the frame, if the next item end is near:
     * otherwise a short frame costs a request.
     */
    private static final double MIN_FILL = 0.5;
    /**
     * A frame may be shrunk this much at most to end at an item end: smaller characters are not read.
     */
    private static final double MAX_SHRINK = 1.5;

    /**
     * @param scan   receipt cut out of the photo and turned upright
     * @param strips the strips of the scan, see {@link ReceiptStrips#cut(ImageData)}
     * @param frame  largest image sent to the model
     * @return frames top to bottom; each fits the frame
     */
    public static List<Frame> assemble(ImageData scan, List<Strip> strips, Dimension frame) {
        // the height of a frame in pixels of the scan
        final double height = (double) frame.height * scan.getWidth() / frame.width;
        final List<List<Strip>> groups = group(strips, height);
        final List<Frame> frames = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            final List<Strip> group = groups.get(i);
            frames.add(new Frame(image(scan, group, frame), top(group), bottom(group), i == 0, i == groups.size() - 1));
        }
        return frames;
    }

    /**
     * @param height height of a frame in pixels of the scan
     */
    static List<List<Strip>> group(List<Strip> strips, double height) {
        final List<List<Strip>> groups = new ArrayList<>();
        if (strips.isEmpty()) {
            return groups;
        }
        if (height(strips) <= height * FIT) {
            groups.add(strips);
            return groups;
        }
        // the first strip of every group, and the end of the strips after the last one
        final List<Integer> starts = new ArrayList<>();
        int start = 0;
        while (start < strips.size()) {
            starts.add(start);
            start = end(strips, start, height) + 1;
        }
        starts.add(strips.size());
        // a short rest goes into the group before it if both fit
        final int count = starts.size() - 1;
        if (count > 1 && height(strips.subList(starts.get(count - 2), strips.size())) <= height * FIT) {
            starts.remove(count - 1);
        }
        for (int i = 0; i + 1 < starts.size(); i++) {
            groups.add(strips.subList(starts.get(i), starts.get(i + 1)));
        }
        return groups;
    }

    /**
     * @return the last strip of the group starting at {@code start}
     */
    private static int end(List<Strip> strips, int start, double height) {
        int fitting = start;
        int sum = 0;
        int lastEnd = -1;
        while (fitting < strips.size() && sum + strips.get(fitting).height() <= height) {
            sum += strips.get(fitting).height();
            if (strips.get(fitting).itemEnd()) {
                lastEnd = fitting;
            }
            fitting++;
        }
        if (fitting == strips.size()) {
            return fitting - 1;
        }
        if (lastEnd >= 0 && height(strips.subList(start, lastEnd + 1)) >= MIN_FILL * height) {
            return lastEnd;
        }
        int nextEnd = fitting;
        while (nextEnd < strips.size() - 1 && !strips.get(nextEnd).itemEnd()) {
            nextEnd++;
        }
        final int toNextEnd = height(strips.subList(start, nextEnd + 1));
        if (toNextEnd <= FIT * height) {
            return nextEnd;
        }
        if (lastEnd >= 0) {
            return lastEnd;
        }
        if (toNextEnd <= MAX_SHRINK * height) {
            return nextEnd;
        }
        // no cut between items near: a cut between lines, which may part the name of an item from its numbers
        return Math.max(start, fitting - 1);
    }

    /**
     * The straightened strips one under another, scaled to the frame width and shrunk to the frame height
     * if they are taller.
     */
    private static ImageData image(ImageData scan, List<Strip> strips, Dimension frame) {
        final int height = Math.max(1, height(strips));
        final BufferedImage image = new BufferedImage(scan.getWidth(), height,
                scan.isGray() ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_3BYTE_BGR);
        final Graphics2D graphics = image.createGraphics();
        int y = 0;
        for (Strip strip : strips) {
            graphics.drawImage(strip.straighten(scan).toImage(), 0, y, null);
            y += strip.height();
        }
        graphics.dispose();
        final double scale = Math.min((double) frame.width / scan.getWidth(), (double) frame.height / height);
        return ImageData.of(image).transform()
                .scale(scale)
                .asImage();
    }

    private static int height(List<Strip> strips) {
        return strips.stream().mapToInt(Strip::height).sum();
    }

    private static int top(List<Strip> strips) {
        final int[] top = strips.getFirst().top();
        return top[top.length / 2];
    }

    private static int bottom(List<Strip> strips) {
        final int[] bottom = strips.getLast().bottom();
        return bottom[bottom.length / 2];
    }

    /**
     * @param image  the frame sent to the model
     * @param top    first row of the frame in the scan, in the middle column: the cut it starts at may be curved,
     *               so a frame starts where the frame before it ends
     * @param bottom row after the last one in the scan, in the middle column
     * @param first  the frame starts at the top of the receipt
     * @param last   the frame ends at the bottom of the receipt
     */
    public record Frame(ImageData image, int top, int bottom, boolean first, boolean last) {
    }
}
