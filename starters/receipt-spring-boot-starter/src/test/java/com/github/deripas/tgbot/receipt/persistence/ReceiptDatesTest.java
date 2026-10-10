package com.github.deripas.tgbot.receipt.persistence;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptDatesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "25.09.2026 18:53:38   | 2026-09-25T18:53:38",
            "25.09.2026. 18:53:38  | 2026-09-25T18:53:38",
            "25.09.2026            | 2026-09-25T00:00",
            "5/9/2026 8:05         | 2026-09-05T08:05",
            "2026-09-25 18:53      | 2026-09-25T18:53",
            "2026-09-25T18:53:38   | 2026-09-25T18:53:38",
            "ПФР време: 25.09.2026. 18:53:38 | 2026-09-25T18:53:38"})
    void parsesFormatsWrittenByModel(String text, LocalDateTime expected) {
        assertThat(ReceiptDates.parse(text)).contains(expected);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "вчера", "32.13.2026", "25.09.2026 25:61"})
    void unparseableIsEmpty(String text) {
        assertThat(ReceiptDates.parse(text)).isEmpty();
    }
}
