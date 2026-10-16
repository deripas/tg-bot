package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.receipt.Country;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptAssemblerTest {

    private static final OllamaReceipt.Summary HEADER = new OllamaReceipt.Summary("LIDL SRBIJA", null, List.of());
    private static final OllamaReceipt.Summary FOOTER = new OllamaReceipt.Summary(null, "10.10.2026. 10:47:51", List.of(
            "Укупан износ: 1.474,76",
            "Платна картица: 1.474,76",
            "Ђ О-ПДВ 20,00% 200,00",
            "Укупан износ пореза: 242,47"
    ));

    @Test
    void parsesPrintedNumbersAndMergesWindows() {
        final OllamaReceipt.Window first = window(HEADER,
                printed("Dimljena slanina/KG/7004114 (Ђ)", "1.199,99", "0,379", "454,80"),
                printed("Datteln, Medjoul (O+G)/0080891 (E)", "899,99", "1", "899,99")
        );
        final OllamaReceipt.Window second = window(FOOTER,
                printed("Datteln, Medjoul (O+G)/0080891 (E)", "899,99", "1", "899,99"),
                printed("Pecivo sir-spanać /KOM/7007235 (E)", "39,99", "1", "39,99"),
                printed("Pecivo sir-spanać /KOM/7007235 (E)", "39,99", "1", "39,99"),
                printed("Pecivo sir-spanać /KOM/7007235 (E)", "39,99", "1", "39,99")
        );

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(List.of(first, second), Country.SERBIA);

        assertThat(assembly.items()).extracting(ParsedItem::total)
                .containsExactly(454.80, 899.99, 39.99, 39.99, 39.99);
        assertThat(assembly.items().getFirst())
                .isEqualTo(new ParsedItem("Dimljena slanina/KG/7004114 (Ђ)", 1199.99, 0.379, 454.80));
        assertThat(assembly.store()).isEqualTo("LIDL SRBIJA");
        assertThat(assembly.total()).isEqualTo(1474.76);
        assertThat(assembly.taxTotal()).isEqualTo(242.47);
        assertThat(assembly.paymentMethod()).isEqualTo("Платна картица");
        assertThat(assembly.dateTime()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 47, 51));
        assertThat(assembly.warnings()).isEmpty();
    }

    /**
     * Frames meet at a cut: the same item at the end of one and at the start of the next is printed twice.
     */
    @Test
    void framesMeetingAtCutAreJoinedAsTheyAre() {
        final OllamaReceipt.Item susam = printed("Susam pločice/KOM/0210929 (Ђ)", "59,99", "1", "59,99");
        final List<OllamaReceipt.Window> frames = List.of(
                new OllamaReceipt.Window(0, 500, true, false, List.of(susam), HEADER),
                new OllamaReceipt.Window(500, 1000, false, true, List.of(susam), totals("Укупан износ: 119,98")));

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(frames, Country.SERBIA);

        assertThat(assembly.items()).extracting(ParsedItem::total).containsExactly(59.99, 59.99);
        assertThat(assembly.warnings()).isEmpty();
    }

    /**
     * Overlapping windows, e.g. the parts of a frame read in two, share the item they both show.
     */
    @Test
    void overlappingWindowsAreMerged() {
        final OllamaReceipt.Item kefir = printed("Kefir 2,8%, 1kg/KOM/0209361 (E)", "132,49", "1", "132,49");
        final OllamaReceipt.Item susam = printed("Susam pločice/KOM/0210929 (Ђ)", "59,99", "1", "59,99");
        final List<OllamaReceipt.Window> parts = List.of(
                new OllamaReceipt.Window(0, 600, true, false, List.of(kefir, susam), HEADER),
                new OllamaReceipt.Window(400, 1000, false, true, List.of(susam), totals("Укупан износ: 192,48")));

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(parts, Country.SERBIA);

        assertThat(assembly.items()).extracting(ParsedItem::total).containsExactly(132.49, 59.99);
    }

    /**
     * The frame under the header holds one item, and the model gave its amount as the total of the frame:
     * it matches the items above it, but the real total, the largest that matches, wins.
     */
    @Test
    void largestMatchingTotalWins() {
        final List<OllamaReceipt.Window> frames = List.of(
                new OllamaReceipt.Window(0, 500, true, false,
                        List.of(printed("Mlečna slatka pavlak/KOM/0251201 (Ђ)", "319,99", "4", "1.279,96")),
                        totals("Укупно: 1.279,96")),
                new OllamaReceipt.Window(500, 1000, false, true,
                        List.of(printed("Kefir 2,8%, 1kg/KOM/0209361 (E)", "132,49", "1", "132,49")),
                        totals("Укупан износ: 1.412,45")));

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(frames, Country.SERBIA);

        assertThat(assembly.total()).isEqualTo(1412.45);
        assertThat(assembly.items()).hasSize(2);
        assertThat(assembly.warnings()).isEmpty();
    }

    /**
     * In a frame without items the model gives the lines of the header and of the taxes as items.
     */
    @Test
    void linesOfHeaderAndTaxesAreNotItems() {
        final OllamaReceipt.Window frame = window(totals("Укупан износ: 1.120,00"),
                printed("Предузеће:", "PEPCO d.o.o.", null, null),
                printed("2201163855560 T-shirt majica za de/kom (Ђ)", "1.000,00", "1.000", "1.000,00"),
                printed("2201759774169 poklon kesa L kolekc/kom (Ђ)", "120,00", "1.000", "120,00"),
                printed("Ђ О-ПДВ 20,00%", null, "1", "186,67"));

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(List.of(frame), Country.SERBIA);

        assertThat(assembly.items()).extracting(ParsedItem::name)
                .containsExactly("2201163855560 T-shirt majica za de/kom (Ђ)", "2201759774169 poklon kesa L kolekc/kom (Ђ)");
        // the quantity is printed with a dot, the amounts with a comma
        assertThat(assembly.items()).extracting(ParsedItem::quantity).containsOnly(1.0);
        assertThat(assembly.warnings()).isEmpty();
    }

    /**
     * The model may misread the name of a tax, e.g. "O-PDB" for "О-ПДВ": a line of the frame of the totals
     * with the amount of a totals line other than the total is a tax.
     */
    @Test
    void taxWithMisreadNameIsKnownByItsAmount() {
        final OllamaReceipt.Window frame = window(new OllamaReceipt.Summary(null, null, List.of(
                        "Укупан износ: 210,00", "Ознака Име Стопа Порез", "О-ПДВ 20,00% 35,00")),
                printed("Kefir 2,8%, 1kg/KOM/0209361 (E)", "210,00", "1", "210,00"),
                printed("O-PDB 20,00%", null, "1", "35,00"));

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(List.of(frame), Country.SERBIA);

        assertThat(assembly.items()).extracting(ParsedItem::total).containsExactly(210.0);
        assertThat(assembly.warnings()).isEmpty();
    }

    @Test
    void missingNumberIsComputedFromTheOtherTwo() {
        assertThat(ReceiptAssembler.toItem(printed("Banane", "150,00", null, "155,10"), ','))
                .isEqualTo(new ParsedItem("Banane", 150.0, 1.034, 155.10));
        assertThat(ReceiptAssembler.toItem(printed("Banane", null, "1,034", "155,10"), ','))
                .isEqualTo(new ParsedItem("Banane", 150.0, 1.034, 155.10));
        assertThat(ReceiptAssembler.toItem(printed("Banane", "150,00", "1,034", "?"), ','))
                .isEqualTo(new ParsedItem("Banane", 150.0, 1.034, 155.10));
    }

    @Test
    void warnsAboutWrongSumAndInconsistentItems() {
        final List<OllamaReceipt.Window> windows = List.of(
                window(FOOTER, printed("Prokelj/KOM/0082931 (E)", "219,99", "1", "179,99"))
        );

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(windows, Country.SERBIA);

        assertThat(assembly.warnings()).containsExactly(
                "price × quantity ≠ total: Prokelj/KOM/0082931 (E)",
                "items sum up to 179.99, printed total is 1474.76");
    }

    @Test
    void warnsWhenTotalIsNotFound() {
        final List<OllamaReceipt.Window> windows = List.of(
                window(OllamaReceipt.Summary.EMPTY, printed("Prokelj", "219,99", "1", "219,99"))
        );

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(windows, Country.SERBIA);

        assertThat(assembly.total()).isNull();
        assertThat(assembly.warnings()).containsExactly("total not found, items sum up to 219.99");
    }

    @Test
    void windowsAfterTheMatchingTotalAreDropped() {
        final List<OllamaReceipt.Window> windows = List.of(
                // the model invented a total for the header window
                window(totals("Total: 1.279,96"), printed("Prokelj", "219,99", "1", "219,99")),
                window(totals("Укупан износ: 379,98"),
                        printed("Prokelj", "219,99", "1", "219,99"), printed("Salata Mix", "159,99", "1", "159,99")),
                // card slip and discounts after the total repeat items
                window(OllamaReceipt.Summary.EMPTY, printed("Salata Mix", "189,99", "1", "189,99"))
        );

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(windows, Country.SERBIA);

        assertThat(assembly.items()).extracting(ParsedItem::name).containsExactly("Prokelj", "Salata Mix");
        assertThat(assembly.total()).isEqualTo(379.98);
        assertThat(assembly.warnings()).isEmpty();
    }

    @Test
    void withoutMatchingTotalWindowWithClosestTotalIsUsed() {
        final List<OllamaReceipt.Window> windows = List.of(
                window(OllamaReceipt.Summary.EMPTY, printed("Prokelj", "219,99", "1", "219,99")),
                window(totals("Укупан износ: 400,00"), printed("Salata Mix", "159,99", "1", "159,99")),
                window(totals("Iznos: 1.000,00 Ukupno"), printed("Banane", "150,00", "1", "150,00"))
        );

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(windows, Country.SERBIA);

        assertThat(assembly.total()).isEqualTo(400.0);
        assertThat(assembly.warnings()).containsExactly("items sum up to 379.98, printed total is 400.00");
    }

    @Test
    void inventedSmallTotalLosesToRealTotalWithWorseItems() {
        final List<OllamaReceipt.Window> windows = List.of(
                // invented: 159.99 off a total of 200.00 is 80%
                window(totals("Total: 200,00"), printed("Salata Mix", "359,99", "1", "359,99")),
                // real: 359.99 off a total of 1.000,00 is 36%, though more in absolute numbers
                window(totals("Укупан износ: 1.000,00"), printed("Prokelj", "280,02", "1", "280,02"))
        );

        assertThat(ReceiptAssembler.assembleWindows(windows, Country.SERBIA).total()).isEqualTo(1000.0);
    }

    @Test
    void dateIsTakenFromTheFirstWindowWithARealDateFromTheTotalOn() {
        final List<OllamaReceipt.Window> windows = List.of(
                window(new OllamaReceipt.Summary("LIDL SRBIJA", "2023-04-15 10:30:00", List.of()),
                        printed("Prokelj", "219,99", "1", "219,99")),
                window(new OllamaReceipt.Summary("Salata", "159,99", List.of("Укупан износ: 379,98")),
                        printed("Salata Mix", "159,99", "1", "159,99")),
                window(new OllamaReceipt.Summary(null, "10.10.2026. 10:47:51", List.of())),
                window(new OllamaReceipt.Summary(null, "10.10.26 10:47.39", List.of()))
        );

        final ReceiptAssembler.Assembly assembly = ReceiptAssembler.assembleWindows(windows, Country.SERBIA);

        assertThat(assembly.store()).isEqualTo("LIDL SRBIJA");
        assertThat(assembly.dateTime()).isEqualTo(LocalDateTime.of(2026, 10, 10, 10, 47, 51));
    }

    private static OllamaReceipt.Summary totals(String line) {
        return new OllamaReceipt.Summary("LIDL SRBIJA", null, List.of(line));
    }

    private static OllamaReceipt.Window window(OllamaReceipt.Summary summary, OllamaReceipt.Item... items) {
        return new OllamaReceipt.Window(0, 0, false, false, List.of(items), summary);
    }

    private static OllamaReceipt.Item printed(String name, String price, String quantity, String total) {
        return new OllamaReceipt.Item(name, price, quantity, total);
    }
}
