package com.github.deripas.receipt;

import com.github.deripas.receipt.parse.FallbackReceiptParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptParsersTest {

    @Test
    void qrCodeFirstModelOtherwise() {
        assertThat(ReceiptParsers.qrOrModel("http://localhost:11434")).isInstanceOf(FallbackReceiptParser.class);
    }
}
