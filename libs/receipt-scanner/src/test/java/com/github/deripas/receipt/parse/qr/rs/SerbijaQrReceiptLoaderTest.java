package com.github.deripas.receipt.parse.qr.rs;

import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.parse.qr.rs.suf.FakeSufServer;
import com.github.deripas.receipt.parse.qr.rs.suf.SufInvoice;
import com.github.deripas.receipt.parse.qr.rs.suf.SufItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SerbijaQrReceiptLoaderTest {

    private final FakeSufServer server = new FakeSufServer();
    private final SerbijaQrReceiptLoader loader = new SerbijaQrReceiptLoader(server.client());

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void receiptIsLoadedFromService() throws IOException {
        final Receipt receipt = loader.load(server.link().toString());

        assertThat(receipt.valid()).isTrue();
        assertThat(receipt.errors()).isEmpty();
        assertThat(receipt.store()).isEqualTo("IVAN MANDIĆ PREDUZETNIK RADNJA ZA PROMET ROBE NA VELIKO I MALO AMANDA NIŠ");
        // 07:57:46.834 UTC is printed in the local time, UTC+1 in winter, to the second
        assertThat(receipt.dateTime()).isEqualTo(LocalDateTime.of(2026, 1, 6, 8, 57, 46));
        assertThat(receipt.total()).isEqualTo(new BigDecimal("191.90"));
        assertThat(receipt.currency()).isEqualTo(Currency.RSD);
        assertThat(receipt.items()).containsExactly(
                new Receipt.Item("Zitopek beli hleb /kom", new BigDecimal("62.00"), new BigDecimal("1"), new BigDecimal("62.00")),
                new Receipt.Item("Pionir medeno srce 150gr /kom", new BigDecimal("129.90"), new BigDecimal("1"), new BigDecimal("129.90")));
        assertThat(receipt.json()).contains("Pionir medeno srce 150gr /kom (Ђ)");
    }

    @Test
    void onlyLinksOfServiceAreSupported() {
        assertThat(loader.isSupported(server.link().toString())).isTrue();
        assertThat(loader.isSupported("https://evil.example/v/?vl=A1g4")).isFalse();
        assertThat(loader.isSupported("010701HZFKZ6WEHZFKZ6WE000022469510102610:47:511340580")).isFalse();
    }

    @Test
    void receiptWithoutSpecificationFails() {
        server.specificationAvailable = false;

        assertThatThrownBy(() -> loader.load(server.link().toString()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("no specification");
    }

    @Test
    void itemsNotSummingUpToTotalAreReported() {
        final SufInvoice invoice = new SufInvoice(
                new SufInvoice.InvoiceRequest("100340079", "AMANDA", "1162807-AMANDA"),
                new SufInvoice.InvoiceResult(new BigDecimal("200"), "X89FX3FD-C38FDVO0-32340",
                        Instant.parse("2026-07-06T07:57:46Z")),
                "journal");
        final SufItem bread = new SufItem("", "Zitopek beli hleb /kom", BigDecimal.ONE, new BigDecimal("62"),
                new BigDecimal("62"), "Е", BigDecimal.TEN);

        final Receipt receipt = SerbijaQrReceiptLoader.receipt(invoice, List.of(bread));

        assertThat(receipt.valid()).isFalse();
        assertThat(receipt.errors()).containsExactly("items sum up to 62.00, the total is 200.00");
        // UTC+2 in summer
        assertThat(receipt.dateTime()).isEqualTo(LocalDateTime.of(2026, 7, 6, 9, 57, 46));
        assertThat(SerbijaQrReceiptLoader.receipt(invoice, List.of()).errors()).containsExactly("the receipt has no items");
    }
}
