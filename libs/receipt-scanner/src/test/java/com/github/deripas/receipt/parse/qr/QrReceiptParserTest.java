package com.github.deripas.receipt.parse.qr;

import com.github.deripas.TestImages;
import com.github.deripas.image.QrImages;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.parse.qr.rs.SerbijaQrReceiptLoader;
import com.github.deripas.receipt.parse.qr.rs.suf.FakeSufServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrReceiptParserTest {

    private final FakeSufServer server = new FakeSufServer();
    /**
     * Serbia with the service on localhost.
     */
    private final QrReceiptParser parser = new QrReceiptParser(List.of(new SerbijaQrReceiptLoader(server.client())));

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void receiptIsLoadedByLinkInCode() throws IOException {
        final Receipt receipt = parser.parse(QrImages.of(server.link().toString()), Country.SERBIA);

        assertThat(receipt.valid()).isTrue();
        assertThat(receipt.total()).isEqualTo(new BigDecimal("191.90"));
        assertThat(receipt.items()).hasSize(2);
        assertThat(server.specificationRequests).hasSize(1);
    }

    @Test
    void codeWithoutReceiptLinkFails() {
        assertThatThrownBy(() -> parser.parse(QrImages.of("https://evil.example/v/?vl=A1g4"), Country.SERBIA))
                .isInstanceOf(IOException.class)
                .hasMessage("No receipt loaded by the QR codes in the photo: 1");
        assertThatThrownBy(() -> parser.parse(new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB), Country.SERBIA))
                .isInstanceOf(IOException.class)
                .hasMessage("No receipt loaded by the QR codes in the photo: 0");
        assertThat(server.specificationRequests).isEmpty();
    }

    @Test
    void receiptThatCannotBeLoadedFails() {
        server.specificationAvailable = false;

        assertThatThrownBy(() -> parser.parse(QrImages.of(server.link().toString()), Country.SERBIA))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("No receipt loaded by the QR codes in the photo: 1; ")
                .hasMessageContaining("no specification")
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    /**
     * The photo of the whole receipt 3 has two codes: the fiscal one with the link and a small one of the shop.
     */
    @Test
    void fiscalCodeIsFoundInPhotoOfWholeReceipt() throws IOException {
        final List<String> codes = new ArrayList<>();
        final SerbijaQrReceiptLoader serbia = new SerbijaQrReceiptLoader();
        final QrReceiptParser recording = new QrReceiptParser(List.of(new QrReceiptLoader() {
            @Override
            public Receipt load(String code) {
                codes.add(code);
                return new Receipt(true, "Lidl", null, List.of(), BigDecimal.ONE, Currency.RSD, null, List.of());
            }

            @Override
            public boolean isSupported(String code) {
                return serbia.isSupported(code);
            }
        }));

        assertThat(recording.parse(TestImages.read("3/PXL_20261011_095819059.jpg"), Country.SERBIA).store())
                .isEqualTo("Lidl");
        assertThat(codes).singleElement()
                .satisfies(code -> assertThat(URI.create(code)).hasHost("suf.purs.gov.rs").hasPath("/v/").hasParameter("vl"));
    }
}
