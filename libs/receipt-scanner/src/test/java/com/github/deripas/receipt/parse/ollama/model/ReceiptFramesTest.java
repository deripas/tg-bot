package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.parse.ollama.model.ReceiptFrames.Frame;
import com.github.deripas.receipt.parse.ollama.model.ReceiptStrips.Strip;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.boofcv.BoofCvReceiptDetector;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptFramesTest {

    /**
     * 4:3, the largest image of qwen2.5vl:7b of these proportions.
     */
    private static final Dimension FRAME = new Dimension(1148, 868);

    @Test
    void shortReceiptIsOneFrame() {
        final List<Strip> strips = strips(new int[]{100, 100, 100}, 1, 2);

        assertThat(ReceiptFrames.group(strips, 260)).containsExactly(strips);
    }

    /**
     * Items of two strips, 50 px each: a frame of 230 px holds 4 items, 200 px.
     */
    @Test
    void framesEndBetweenItems() {
        final int[] heights = new int[20];
        Arrays.fill(heights, 25);
        final List<Strip> strips = strips(heights, 1, 3, 5, 7, 9, 11, 13, 15, 17, 19);

        final List<List<Strip>> groups = ReceiptFrames.group(strips, 230);

        assertThat(groups).extracting(List::size).containsExactly(8, 8, 4);
        assertThat(groups).allSatisfy(group -> assertThat(group.getLast().itemEnd()).isTrue());
        assertThat(groups.stream().flatMap(List::stream)).containsExactlyElementsOf(strips);
    }

    /**
     * No item end fits a frame of 200 px; the next one is 300 px away: the frame is shrunk by 1.5 rather than
     * cut an item.
     */
    @Test
    void frameWithoutItemEndGoesOnToNearItemEnd() {
        final List<Strip> strips = strips(fifties(12), 5, 9, 11);

        final List<List<Strip>> groups = ReceiptFrames.group(strips, 200);

        assertThat(groups).extracting(List::size).containsExactly(6, 4, 2);
    }

    /**
     * The next item end is 400 px away, twice the frame: the characters would be too small, the frame ends
     * at a cut between lines.
     */
    @Test
    void frameWithoutItemEndNearEndsBetweenLines() {
        final List<Strip> strips = strips(fifties(12), 7, 11);

        final List<List<Strip>> groups = ReceiptFrames.group(strips, 200);

        assertThat(groups.getFirst()).hasSize(4);
        assertThat(groups.getFirst().getLast().itemEnd()).isFalse();
    }

    /**
     * The only item end that fits leaves the frame a quarter full; the next one is 250 px away, within
     * 1.25 frames: the frame goes on to it.
     */
    @Test
    void frameDoesNotEndNearlyEmpty() {
        final List<Strip> strips = strips(fifties(12), 0, 4, 8, 11);

        final List<List<Strip>> groups = ReceiptFrames.group(strips, 200);

        assertThat(groups.getFirst()).hasSize(5);
    }

    /**
     * A rest of 60 px goes into the frame before it: 300 + 60 fits 1.25 frames of 300 px.
     */
    @Test
    void shortRestGoesIntoFrameBefore() {
        final List<Strip> strips = strips(new int[]{150, 150, 150, 150, 60}, 1, 3, 4);

        final List<List<Strip>> groups = ReceiptFrames.group(strips, 300);

        assertThat(groups).extracting(List::size).containsExactly(2, 3);
    }

    @Test
    void framesOfReceiptFitFrameAndFollowEachOther() {
        final ImageData scan = new DefaultReceiptScanner(new BoofCvReceiptDetector())
                .scan(TestImages.readData("3/PXL_20261011_095819059.jpg"));

        final List<Frame> frames = ReceiptFrames.assemble(scan, ReceiptStrips.cut(scan), FRAME);

        // 0.75 of the width of the receipt holds about 12 of its items: the receipt of 52 items and a long tail
        assertThat(frames).hasSizeBetween(6, 12);
        assertThat(frames).allSatisfy(frame -> {
            assertThat(frame.image().getWidth()).isLessThanOrEqualTo(FRAME.width);
            assertThat(frame.image().getHeight()).isLessThanOrEqualTo(FRAME.height);
        });
        assertThat(frames.getFirst().first()).isTrue();
        assertThat(frames.getFirst().top()).isZero();
        assertThat(frames.getLast().last()).isTrue();
        assertThat(frames.getLast().bottom()).isEqualTo(scan.getHeight());
        assertThat(frames.stream().filter(frame -> frame.first() || frame.last())).hasSize(2);
    }

    @Test
    void wideFrameIsShrunkToFrameHeight() {
        final ImageData scan = ImageData.of(new BufferedImage(400, 1000, BufferedImage.TYPE_BYTE_GRAY));
        final int[] top = new int[400];
        final int[] bottom = new int[400];
        Arrays.fill(bottom, 1000);

        final List<Frame> frames = ReceiptFrames.assemble(scan, List.of(new Strip(top, bottom, true)), FRAME);

        // a single strip far taller than the frame
        assertThat(frames).singleElement().satisfies(frame -> {
            assertThat(frame.image().getHeight()).isEqualTo(FRAME.height);
            assertThat(frame.image().getWidth()).isLessThan(FRAME.width);
        });
    }

    private static int[] fifties(int count) {
        final int[] heights = new int[count];
        Arrays.fill(heights, 50);
        return heights;
    }

    /**
     * Straight strips of the given heights, 4 columns wide.
     *
     * @param itemEnds indexes of the strips that end an item
     */
    private static List<Strip> strips(int[] heights, int... itemEnds) {
        final List<Strip> strips = new ArrayList<>();
        int y = 0;
        for (int i = 0; i < heights.length; i++) {
            final int[] top = new int[4];
            final int[] bottom = new int[4];
            Arrays.fill(top, y);
            Arrays.fill(bottom, y + heights[i]);
            final int index = i;
            strips.add(new Strip(top, bottom, Arrays.stream(itemEnds).anyMatch(end -> end == index)));
            y += heights[i];
        }
        return strips;
    }
}
