package com.github.deripas.receipt.parse;

import com.github.deripas.TestImages;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.read.ReceiptText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parses text read by the model from the test receipts and saved as {@code text.json}
 * by {@code OllamaReceiptReaderTester}: checks the parser without the model.
 */
class DefaultReceiptParserFunctionTest {

    private final DefaultReceiptParserFunction parser = new DefaultReceiptParserFunction();

    @Test
    void shortReceipt() {
        final Receipt receipt = parser.apply(text("1"), Country.SERBIA);

        assertThat(receipt.valid()).isTrue();
        assertThat(receipt.errors()).isEmpty();
        assertThat(receipt.store()).isEqualTo("PEPCO d.o.o.");
        assertThat(receipt.dateTime()).isEqualTo(LocalDateTime.of(2026, 9, 25, 18, 53, 38));
        assertThat(receipt.total()).isEqualTo(new BigDecimal("1120.00"));
        assertThat(receipt.currency()).isEqualTo(Currency.RSD);
        assertThat(receipt.items()).extracting(Receipt.Item::total)
                .containsExactly(new BigDecimal("1000.00"), new BigDecimal("120.00"));
    }

    /**
     * The same long receipt shot as one frame (3) and as a panorama (2); items are compared with
     * the ones read by a human, names as the model copied them may differ in a character.
     */
    @ParameterizedTest
    @CsvSource({"2", "3"})
    void testLidlReceipt(String receipt) {
        final Receipt parsed = parser.apply(text(receipt), Country.SERBIA);
        final List<ParsedItem> expected = ReceiptMergerTest.expected(receipt);

        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.store()).isEqualTo("LIDL SRBIJA");
        assertThat(parsed.dateTime()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 47, 51));
        assertThat(parsed.total()).isEqualTo(new BigDecimal("11799.26"));
        assertThat(parsed.items()).extracting(item -> item.total().doubleValue())
                .containsExactlyElementsOf(expected.stream().map(ParsedItem::total).toList());
        assertThat(parsed.items()).extracting(item -> item.price().doubleValue())
                .containsExactlyElementsOf(expected.stream().map(ParsedItem::price).toList());
        for (int i = 0; i < expected.size(); i++) {
            assertThat(ReceiptMerger.nameSimilarity(parsed.items().get(i).name(), expected.get(i).name()))
                    .as("item %d: %s", i + 1, parsed.items().get(i).name())
                    .isGreaterThanOrEqualTo(ReceiptMerger.NAME_SIMILARITY);
        }
    }

    @Test
    void textIsKeptToParseAgain() {
        final ReceiptText text = text("1");
        final Receipt receipt = parser.apply(text, Country.SERBIA);

        assertThat(readJson(receipt.json()))
                .isEqualTo(text);
    }

    private static ReceiptText text(String receipt) {
        try {
            final String content = Files.readString(TestImages.file(receipt + "/text.json"));
            return JsonMapper.builder().build()
                    .readValue(content, ReceiptText.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return the text stored in {@link Receipt#json()}
     */
    private static ReceiptText readJson(String json) {
        return JsonMapper.builder().build()
                .readValue(json, ReceiptText.class);
    }
}
