package com.github.deripas.receipt.parse;

import com.github.deripas.TestImages;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Parts are cut from the real receipt {@code receipts/3} the way overlapping windows share items:
 * items 1-20, 16-45 and 29-52.
 */
class ReceiptMergerTest {

    private static final double TOTAL = 11799.26;
    private static final List<ParsedItem> RECEIPT = expected();

    @Test
    void mergesOverlappingWindows() {
        final ReceiptMerger.Result result = ReceiptMerger.merge(parts(), TOTAL);

        assertThat(result.items()).isEqualTo(RECEIPT);
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void keepsItemsRepeatedInsideAPhoto() {
        final List<ParsedItem> merged = ReceiptMerger.merge(parts(), TOTAL).items();

        assertThat(merged).filteredOn(item -> item.name().startsWith("Tričetvrt")).hasSize(4);
        assertThat(merged).filteredOn(item -> item.name().startsWith("Susam")).hasSize(3);
        assertThat(merged).filteredOn(item -> item.name().startsWith("Kefir")).hasSize(2);
    }

    @Test
    void namesWithoutProductCodeStillMatch() {
        final List<List<ParsedItem>> parts = parts();
        parts.set(1, map(parts.get(1), item -> rename(item, item.name().replaceAll("/.*", ""))));

        final List<ParsedItem> merged = ReceiptMerger.merge(parts, TOTAL).items();

        assertThat(merged).hasSize(RECEIPT.size());
        assertThat(ReceiptMerger.sum(merged)).isCloseTo(TOTAL, within(0.001));
    }

    @Test
    void lineMissedInOneCopyOfTheOverlapIsKept() {
        final List<List<ParsedItem>> parts = parts();
        // Kefir (item 17) is in the overlap of windows 1 and 2; window 2 missed it
        parts.get(1).remove(1);

        assertThat(ReceiptMerger.merge(parts, TOTAL).items()).isEqualTo(RECEIPT);
    }

    @Test
    void misreadCopyInTheOverlapIsReplacedByConsistentOne() {
        final List<List<ParsedItem>> parts = parts();
        final ParsedItem sirni = parts.get(1).get(2);
        parts.get(1).set(2, new ParsedItem(sirni.name(), 279.99, 1.0, 279.90));

        assertThat(ReceiptMerger.merge(parts, TOTAL).items()).isEqualTo(RECEIPT);
    }

    @Test
    void ambiguousOverlapInRunOfSameItemsIsResolvedByTotal() {
        final ParsedItem susam = RECEIPT.get(46);
        final List<ParsedItem> receipt = List.of(RECEIPT.get(44), RECEIPT.get(45), susam, susam, susam, susam, RECEIPT.get(49));
        // windows do not overlap: 2 + 2 of 4 identical items; aligning would merge them
        final List<List<ParsedItem>> parts = List.of(receipt.subList(0, 4), receipt.subList(4, 7));
        final double total = ReceiptMerger.sum(receipt);

        final ReceiptMerger.Result result = ReceiptMerger.merge(parts, total);

        assertThat(result.items()).isEqualTo(receipt);
        assertThat(result.warnings()).singleElement().asString().contains("chosen by the total");
        assertThat(ReceiptMerger.merge(parts, null).items()).hasSize(5);
    }

    @Test
    void itemInventedInsideOverlapIsRemovedByTotal() {
        final List<List<ParsedItem>> parts = parts();
        // window 3 starts at Pasirani paradajz (item 29); the model repeated Prokelj (item 30)
        parts.get(2).add(2, RECEIPT.get(29));

        final ReceiptMerger.Result withTotal = ReceiptMerger.merge(parts, TOTAL);

        assertThat(withTotal.items()).isEqualTo(RECEIPT);
        assertThat(withTotal.warnings()).singleElement().asString()
                .contains("seen in only one of overlapping windows: Prokelj");
        assertThat(ReceiptMerger.merge(parts, null).items()).hasSize(RECEIPT.size() + 1);
    }

    @Test
    void partReadBottomToTopIsReversed() {
        final List<List<ParsedItem>> parts = parts();
        parts.set(2, new ArrayList<>(parts.get(2).reversed()));

        final ReceiptMerger.Result result = ReceiptMerger.merge(parts, TOTAL);

        assertThat(result.items()).isEqualTo(RECEIPT);
        assertThat(result.warnings()).containsExactly("items of window 3 were read bottom to top and reversed");
    }

    @Test
    void firstPartReadBottomToTopIsReversed() {
        final List<List<ParsedItem>> parts = parts();
        parts.set(0, new ArrayList<>(parts.get(0).reversed()));

        assertThat(ReceiptMerger.merge(parts, TOTAL).items()).isEqualTo(RECEIPT);
    }

    @Test
    void windowsWithoutOverlapAreConcatenated() {
        final List<List<ParsedItem>> parts = List.of(RECEIPT.subList(0, 12), RECEIPT.subList(12, 20));

        assertThat(ReceiptMerger.merge(parts, null).items()).isEqualTo(RECEIPT.subList(0, 20));
    }

    @Test
    void namesAreComparedIgnoringDiacriticsCaseAndCode() {
        assertThat(ReceiptMerger.nameSimilarity("Domaće grožđe belo 750g pk/0080517 (E)", "Domace grozde belo"))
                .isGreaterThanOrEqualTo(ReceiptMerger.NAME_SIMILARITY);
        assertThat(ReceiptMerger.nameSimilarity("Prokelj/KOM/0082931 (E)", "Salata Mix/KOM/0082157 (E)"))
                .isLessThan(ReceiptMerger.NAME_SIMILARITY);
    }

    private static List<List<ParsedItem>> parts() {
        return new ArrayList<>(List.of(
                new ArrayList<>(RECEIPT.subList(0, 20)),
                new ArrayList<>(RECEIPT.subList(15, 45)),
                new ArrayList<>(RECEIPT.subList(28, 52))));
    }

    private static List<ParsedItem> map(List<ParsedItem> items, UnaryOperator<ParsedItem> mapper) {
        return new ArrayList<>(items.stream().map(mapper).toList());
    }

    private static ParsedItem rename(ParsedItem item, String name) {
        return new ParsedItem(name, item.price(), item.quantity(), item.total());
    }

    static List<ParsedItem> expected() {
        return expected("3");
    }

    /** Items of {@code receipts/<receipt>} read by a human. */
    static List<ParsedItem> expected(String receipt) {
        return JsonMapper.builder().build().readValue(TestImages.file(receipt + "/expected.json").toFile(),
                new TypeReference<List<ParsedItem>>() {
                });
    }
}
