package com.github.deripas.receipt.parse;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FallbackReceiptParserTest {

    private static final BufferedImage IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    private static final Receipt BY_CODE = receipt("by code");
    private static final Receipt BY_MODEL = receipt("by model");

    private final ReceiptParser primary = mock(ReceiptParser.class);
    private final ReceiptParser fallback = mock(ReceiptParser.class);
    private final FallbackReceiptParser parser = new FallbackReceiptParser(primary, fallback);

    @Test
    void primaryWins() throws IOException {
        when(primary.parse(IMAGE, Country.SERBIA)).thenReturn(BY_CODE);

        assertThat(parser.parse(IMAGE, Country.SERBIA)).isSameAs(BY_CODE);
        verifyNoInteractions(fallback);
    }

    @Test
    void failedPrimaryFallsBack() throws IOException {
        when(primary.parse(IMAGE, Country.SERBIA)).thenThrow(new IOException("No fiscal QR code found"));
        when(fallback.parse(IMAGE, Country.SERBIA)).thenReturn(BY_MODEL);

        assertThat(parser.parse(IMAGE, Country.SERBIA)).isSameAs(BY_MODEL);
    }

    @Test
    void failureOfFallbackIsThrown() throws IOException {
        final IOException modelDown = new IOException("Connection refused");
        when(primary.parse(IMAGE, Country.SERBIA)).thenThrow(new IOException("No fiscal QR code found"));
        when(fallback.parse(IMAGE, Country.SERBIA)).thenThrow(modelDown);

        assertThatThrownBy(() -> parser.parse(IMAGE, Country.SERBIA)).isSameAs(modelDown);
    }

    private static Receipt receipt(String store) {
        return new Receipt(true, store, null, List.of(), BigDecimal.ONE, Currency.RSD, "", List.of());
    }
}
