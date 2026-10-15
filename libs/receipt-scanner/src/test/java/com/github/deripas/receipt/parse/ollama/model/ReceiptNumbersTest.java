package com.github.deripas.receipt.parse.ollama.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptNumbersTest {

    @Test
    void parsesEuropeanFormat() {
        assertThat(ReceiptNumbers.parse("1.199,99", ',')).contains(1199.99);
        assertThat(ReceiptNumbers.parse("0,379", ',')).contains(0.379);
        assertThat(ReceiptNumbers.parse(" 11.799,26 ", ',')).contains(11799.26);
        assertThat(ReceiptNumbers.parse("3", ',')).contains(3.0);
    }

    @Test
    void parsesDotFormat() {
        assertThat(ReceiptNumbers.parse("1,199.99", '.')).contains(1199.99);
        assertThat(ReceiptNumbers.parse("0.379", '.')).contains(0.379);
    }

    @Test
    void notANumberIsEmpty() {
        assertThat(ReceiptNumbers.parse(null, ',')).isEmpty();
        assertThat(ReceiptNumbers.parse("", ',')).isEmpty();
        assertThat(ReceiptNumbers.parse("KOM", ',')).isEmpty();
        assertThat(ReceiptNumbers.parse("12a", ',')).isEmpty();
    }

    @Test
    void detectsDecimalSeparatorFromAmounts() {
        assertThat(ReceiptNumbers.decimalSeparator(List.of("1.199,99", "0,379", "454,80", "1"), '.')).isEqualTo(',');
        assertThat(ReceiptNumbers.decimalSeparator(List.of("1,199.99", "0.379", "454.80"), ',')).isEqualTo('.');
    }

    @Test
    void fallsBackToCountrySeparator() {
        assertThat(ReceiptNumbers.decimalSeparator(Arrays.asList(null, "3"), ',')).isEqualTo(',');
        assertThat(ReceiptNumbers.decimalSeparator(List.of(), '.')).isEqualTo('.');
    }

    @Test
    void takesLastNumberOfLine() {
        assertThat(ReceiptNumbers.lastNumber("Укупан износ: 11.799,26", ',')).contains(11799.26);
        assertThat(ReceiptNumbers.lastNumber("Total 20% 1.214,79", ',')).contains(1214.79);
        assertThat(ReceiptNumbers.lastNumber("Платна картица:", ',')).isEmpty();
        assertThat(ReceiptNumbers.lastNumber(null, ',')).isEmpty();
    }
}
