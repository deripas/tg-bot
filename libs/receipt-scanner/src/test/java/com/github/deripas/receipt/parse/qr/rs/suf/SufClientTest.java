package com.github.deripas.receipt.parse.qr.rs.suf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SufClientTest {

    private final FakeSufServer server = new FakeSufServer();
    private final SufClient client = server.client();

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void invoiceIsAskedForAsJson() throws IOException, InterruptedException {
        final SufInvoice invoice = client.invoice(server.link());

        assertThat(invoice.invoiceRequest().businessName())
                .isEqualTo("IVAN MANDIĆ PREDUZETNIK RADNJA ZA PROMET ROBE NA VELIKO I MALO AMANDA NIŠ");
        assertThat(invoice.invoiceResult().invoiceNumber()).isEqualTo("X89FX3FD-C38FDVO0-32340");
        assertThat(invoice.invoiceResult().totalAmount()).isEqualByComparingTo("191.90");
        assertThat(invoice.invoiceResult().sdcTime()).isEqualTo(Instant.parse("2026-01-06T07:57:46.834Z"));
        assertThat(invoice.journal()).startsWith("============ ФИСКАЛНИ РАЧУН ============");
    }

    @Test
    void specificationIsAskedForWithTokenOfPage() throws IOException, InterruptedException {
        final List<SufItem> items = client.specification(server.link(), "X89FX3FD-C38FDVO0-32340");

        assertThat(items).containsExactly(
                new SufItem("", "Zitopek beli hleb /kom", new BigDecimal("1"), new BigDecimal("62"),
                        new BigDecimal("62"), "Е", new BigDecimal("10")),
                new SufItem("8602300236022", "Pionir medeno srce 150gr /kom", new BigDecimal("1"),
                        new BigDecimal("129.9"), new BigDecimal("129.9"), "Ђ", new BigDecimal("20")));
        assertThat(server.specificationRequests).containsExactly(
                "invoiceNumber=X89FX3FD-C38FDVO0-32340&token=1163a4e0-45af-44b4-be0f-c60d52a08f9e");
    }

    @Test
    void missingSpecificationFails() {
        server.specificationAvailable = false;

        assertThatThrownBy(() -> client.specification(server.link(), "X89FX3FD-C38FDVO0-32340"))
                .isInstanceOf(IOException.class)
                .hasMessage("The service has no specification of receipt X89FX3FD-C38FDVO0-32340");
    }

    @Test
    void onlyLinksToReceiptsOfServiceAreFollowed() {
        final SufClient real = new SufClient();

        assertThat(real.isVerificationLink("https://suf.purs.gov.rs/v/?vl=A1g4OUZYM0ZE%2B")).isTrue();
        assertThat(real.isVerificationLink(" https://suf.purs.gov.rs/v/?vl=A1g4\n")).isTrue();
        assertThat(real.isVerificationLink("http://suf.purs.gov.rs/v/?vl=A1g4")).isFalse();
        assertThat(real.isVerificationLink("https://evil.example/v/?vl=A1g4")).isFalse();
        assertThat(real.isVerificationLink("https://suf.purs.gov.rs/specifications")).isFalse();
        assertThat(real.isVerificationLink("010701HZFKZ6WEHZFKZ6WE000022469510102610:47:511340580")).isFalse();
        assertThatThrownBy(() -> real.invoice(URI.create("https://evil.example/v/?vl=A1g4")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
