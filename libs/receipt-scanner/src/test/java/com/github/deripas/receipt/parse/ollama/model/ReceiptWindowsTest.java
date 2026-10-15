package com.github.deripas.receipt.parse.ollama.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptWindowsTest {

    @Test
    void shortReceiptIsOneWindow() {
        assertThat(ReceiptWindows.cut(1400, 1120, 0.3))
                .containsExactly(new ReceiptWindows.Cut(0, 1400));
    }

    @Test
    void longReceiptIsCutIntoOverlappingWindowsWithTheLastAlignedToTheBottom() {
        assertThat(ReceiptWindows.cut(3000, 1000, 0.3))
                .containsExactly(
                        new ReceiptWindows.Cut(0, 1000),
                        new ReceiptWindows.Cut(700, 1700),
                        new ReceiptWindows.Cut(1400, 2400),
                        new ReceiptWindows.Cut(2000, 3000)
                );
    }
}
