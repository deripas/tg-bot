package com.github.deripas.receipt.parse;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.read.ReceiptReader;
import com.github.deripas.receipt.read.ReceiptText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Checks how the parser connects the reader with the parsing of the text; the parsing itself
 * is checked by {@link DefaultReceiptParserFunctionTest}.
 */
@ExtendWith(MockitoExtension.class)
class DefaultReceiptParserTest {

    private static final BufferedImage IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
    private static final ReceiptText TEXT = new ReceiptText(List.of());
    private static final Receipt RECEIPT = new Receipt(true, "store", null, List.of(), null, Currency.RSD,
            "{}", List.of());

    @Mock
    private ReceiptReader reader;
    @Mock
    private ReceiptParserFunction function;
    @InjectMocks
    private DefaultReceiptParser parser;

    @Test
    void parsesTextOfReader() throws IOException {
        when(reader.read(any(), eq(Country.SERBIA))).thenReturn(TEXT);
        when(function.apply(TEXT, Country.SERBIA)).thenReturn(RECEIPT);

        assertThat(parser.parse(IMAGE, Country.SERBIA)).isEqualTo(RECEIPT);
    }

    @Test
    void failsIfReaderFails() throws IOException {
        final IOException failure = new IOException("model is not available");
        when(reader.read(any(), any())).thenThrow(failure);

        assertThatThrownBy(() -> parser.parse(IMAGE, Country.SERBIA)).isSameAs(failure);
        verifyNoInteractions(function);
    }

    @Test
    void failsIfParsingFails() throws IOException {
        final IllegalStateException failure = new IllegalStateException("broken text");
        when(reader.read(any(), eq(Country.SERBIA))).thenReturn(TEXT);
        when(function.apply(TEXT, Country.SERBIA)).thenThrow(failure);

        assertThatThrownBy(() -> parser.parse(IMAGE, Country.SERBIA)).isSameAs(failure);
    }
}
