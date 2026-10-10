package com.github.deripas.receipt.parse;

import com.github.deripas.receipt.Country;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptTotalsTest {

    private static final Country.Keywords SERBIA = Country.SERBIA.getKeywords();

    @Test
    void serbianFiscalReceipt() {
        final ReceiptTotals totals = ReceiptTotals.parse(List.of(
                "Укупан износ: 11.799,26",
                "Платна картица: 11.799,26",
                "Ознака Име Стопа Порез",
                "Ђ О-ПДВ 20,00% 1.214,79",
                "Е П-ПДВ 10,00% 410,05",
                "Укупан износ пореза: 1.624,84"), SERBIA, ',');

        assertThat(totals).isEqualTo(new ReceiptTotals(11799.26, 1624.84, "Платна картица"));
    }

    @Test
    void latinLabels() {
        final ReceiptTotals totals = ReceiptTotals.parse(List.of(
                "Ukupan iznos: 1.120,00", "Gotovina: 2.000,00", "Povraćaj: 880,00", "Ukupan iznos poreza: 186,67"),
                SERBIA, ',');

        assertThat(totals).isEqualTo(new ReceiptTotals(1120.0, 186.67, "Gotovina"));
    }

    @Test
    void taxMisreadByModelIsStillTax() {
        final ReceiptTotals totals = ReceiptTotals.parse(List.of(
                "Укупан износ: 1.120,00", "Укупан износ нореза: 186,67"), SERBIA, ',');

        assertThat(totals).isEqualTo(new ReceiptTotals(1120.0, 186.67, null));
    }

    @Test
    void paidAmountIsUsedWhenTotalLineIsMissing() {
        assertThat(ReceiptTotals.parse(List.of("Card: 12.50"), SERBIA, '.'))
                .isEqualTo(new ReceiptTotals(12.5, null, "Card"));
    }

    @Test
    void noTotalsLines() {
        assertThat(ReceiptTotals.parse(List.of(), SERBIA, ',')).isEqualTo(new ReceiptTotals(null, null, null));
    }
}
