package com.github.deripas.receipt.read;

import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a receipt scaled to the frame width into windows of the frame height.
 */
@UtilityClass
public class ReceiptWindows {

    /**
     * A receipt up to this many frame heights is shrunk into one frame: slightly smaller characters
     * cost less than a cut, and every cut is a chance to lose or invent an item.
     */
    public static final double FIT = 1.25;

    /**
     * @param height  height of the receipt scaled to the frame width
     * @param frame   frame height
     * @param overlap part of the window height shared with the next window, 0-1
     * @return windows top to bottom, overlapping so that every printed line is whole in at least one of them;
     * the last one is aligned to the bottom
     */
    public static List<Cut> cut(int height, int frame, double overlap) {
        if (height <= frame * FIT) {
            return List.of(new Cut(0, height));
        }
        final int step = Math.max(1, (int) Math.round(frame * (1 - overlap)));
        final List<Cut> cuts = new ArrayList<>();
        for (int top = 0; ; top += step) {
            final int y = Math.min(top, height - frame);
            cuts.add(new Cut(y, y + frame));
            if (y + frame >= height) {
                return cuts;
            }
        }
    }

    /**
     * @param top    first row
     * @param bottom row after the last one
     */
    public record Cut(int top, int bottom) {

        public int height() {
            return bottom - top;
        }
    }
}
