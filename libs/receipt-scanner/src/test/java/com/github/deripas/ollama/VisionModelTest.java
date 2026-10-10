package com.github.deripas.ollama;

import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class VisionModelTest {

    private static final VisionModel MODEL = VisionModel.QWEN_2_5_VL;

    @Test
    void testMinImageSizeHasAtLeastMinTokens() {
        final Dimension size = MODEL.minImageSize(new Dimension(3024, 4032));

        assertThat(size).isEqualTo(new Dimension(784, 1036));
        assertOnGrid(size);
        assertThat(MODEL.imageTokens(size)).isGreaterThanOrEqualTo(MODEL.minImageTokens());
    }

    @Test
    void testMaxImageSizeHasAtMostMaxTokens() {
        final Dimension size = MODEL.maxImageSize(new Dimension(3024, 4032));

        assertOnGrid(size);
        assertThat(MODEL.imageTokens(size)).isLessThanOrEqualTo(MODEL.maxImageTokens());
    }

    @Test
    void testMaxImageSizeOfReceiptWindow() {
        assertThat(MODEL.maxImageSize(new Dimension(4, 5))).isEqualTo(new Dimension(896, 1120));
    }

    @Test
    void testImageSizeKeepsProportions() {
        final Dimension min = MODEL.minImageSize(new Dimension(1000, 4000));
        final Dimension max = MODEL.maxImageSize(new Dimension(1000, 4000));

        assertThat((double) min.height / min.width).isCloseTo(4, offset(0.2));
        assertThat((double) max.height / max.width).isCloseTo(4, offset(0.2));
    }

    @Test
    void testImageTokensRoundUpPartialPatches() {
        assertThat(MODEL.imageTokens(new Dimension(29, 28))).isEqualTo(2);
    }

    @Test
    void testMinTokensMustNotExceedMax() {
        assertThatThrownBy(() -> new VisionModel("model", 28, 2000, 1000, 4096))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertOnGrid(Dimension size) {
        assertThat(size.width % MODEL.patch()).isZero();
        assertThat(size.height % MODEL.patch()).isZero();
    }
}
