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
