package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.image.ImageData;
import com.github.deripas.ollama.OllamaClient;
import com.github.deripas.ollama.OllamaRequest;
import com.github.deripas.ollama.VisionModel;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.scan.ReceiptScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks how the receipt is cut into windows and what the model is asked, with the model mocked;
 * reading real receipts is checked by {@code OllamaReceiptReaderTester}.
 */
@ExtendWith(MockitoExtension.class)
class OllamaReceiptReaderTest {

    private static final OllamaReceiptReader.Items ITEMS = new OllamaReceiptReader.Items(
            List.of(new OllamaReceipt.Item("Hleb", "100,00", "1", "100,00")));
    private static final OllamaReceipt.Summary SUMMARY = new OllamaReceipt.Summary("LIDL", "10.10.2026", List.of());

    private static final Dimension FRAME = VisionModel.QWEN_2_5_VL.maxImageSize(new Dimension(4, 5));
    private static final String STRIP =
            "This image is a horizontal strip of a long receipt photo; neighbouring strips overlap.";
    private static final String TOP_CUT = "The top edge of the image cuts through the receipt.";
    private static final String BOTTOM_CUT = "The bottom edge of the image cuts through the receipt.";

    @Mock
    private ReceiptScanner scanner;
    @Mock
    private OllamaClient client;
    private OllamaReceiptReader reader;

    @BeforeEach
    void setUp() {
        reader = new OllamaReceiptReader(scanner, client, VisionModel.QWEN_2_5_VL, OllamaReceiptReader.DEFAULT_OVERLAP);
    }

    @Test
    void shortReceiptIsReadAsOneWindow() throws Exception {
        final ImageData receipt = receipt(1300);
        answerItems();
        answerSummary();

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(text.windows()).containsExactly(
                new OllamaReceipt.Window(0, 1300, true, true, ITEMS.items(), SUMMARY));
        verify(client).ask(argThat(request -> request.model() == VisionModel.QWEN_2_5_VL
                        && request.prompt().equals(OllamaReceiptReader.itemsPrompt(true, true))
                        && request.images().size() == 1),
                eq(OllamaReceiptReader.Items.class));
    }

    @Test
    void itemsPromptTellsWhichEdgesCutReceipt() {
        assertThat(OllamaReceiptReader.itemsPrompt(true, true))
                .doesNotContain(STRIP, TOP_CUT, BOTTOM_CUT)
                .endsWith("leftmost.\n\nRead the purchased items from the photo.\n");
        assertThat(OllamaReceiptReader.itemsPrompt(false, false))
                .endsWith("leftmost.\n\n" + STRIP + "\n" + TOP_CUT + "\n" + BOTTOM_CUT
                        + "\nRead the purchased items from the photo.\n");
    }

    @Test
    void longReceiptIsReadInWindows() throws Exception {
        final ImageData receipt = receipt(3000);
        final List<ReceiptWindows.Cut> cuts = ReceiptWindows.cut(3000, FRAME.height,
                OllamaReceiptReader.DEFAULT_OVERLAP);
        final ArgumentCaptor<OllamaRequest> requests = ArgumentCaptor.forClass(OllamaRequest.class);
        when(client.ask(requests.capture(), eq(OllamaReceiptReader.Items.class)))
                .thenReturn(ITEMS);
        answerSummary();

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(cuts).hasSizeGreaterThan(2);
        final List<String> prompts = requests.getAllValues().stream().map(OllamaRequest::prompt).toList();
        assertThat(text.windows()).extracting(OllamaReceipt.Window::top)
                .containsExactlyElementsOf(cuts.stream().map(ReceiptWindows.Cut::top).toList());
        assertThat(text.windows()).extracting(OllamaReceipt.Window::first)
                .containsExactly(true, false, false, false);
        assertThat(text.windows()).extracting(OllamaReceipt.Window::last)
                .containsExactly(false, false, false, true);
        assertThat(prompts.getFirst())
                .contains(BOTTOM_CUT)
                .doesNotContain(TOP_CUT);
        assertThat(prompts.get(1))
                .contains(TOP_CUT, BOTTOM_CUT);
        assertThat(prompts.getLast())
                .contains(TOP_CUT)
                .doesNotContain(BOTTOM_CUT);
    }

    @Test
    void windowIsReadInTwoPartsWhenModelFails() throws Exception {
        final ImageData receipt = receipt(1000);
        when(client.ask(any(), eq(OllamaReceiptReader.Items.class)))
                .thenThrow(new IOException("looped"))
                .thenReturn(ITEMS);
        answerSummary();

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(text.windows()).containsExactly(
                new OllamaReceipt.Window(0, 600, true, false, ITEMS.items(), SUMMARY),
                new OllamaReceipt.Window(400, 1000, false, true, ITEMS.items(), SUMMARY));
    }

    @Test
    void failsWhenPartOfWindowFails() throws Exception {
        final ImageData receipt = receipt(1000);
        when(client.ask(any(), eq(OllamaReceiptReader.Items.class)))
                .thenThrow(new IOException("looped"));

        assertThatThrownBy(() -> reader.read(receipt, Country.SERBIA))
                .isInstanceOf(IOException.class);
    }

    @Test
    void windowWithUnreadTotalsHasNone() throws Exception {
        final ImageData receipt = receipt(1000);
        answerItems();
        when(client.ask(any(), eq(OllamaReceiptReader.Summary.class)))
                .thenThrow(new IOException("looped"));

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(text.windows()).singleElement()
                .extracting(OllamaReceipt.Window::summary)
                .isEqualTo(OllamaReceipt.Summary.EMPTY);
    }

    /**
     * @return scanned receipt of the frame width, returned by the scanner as is
     */
    private ImageData receipt(int height) {
        final ImageData receipt = ImageData.of(
                new BufferedImage(FRAME.width, height, BufferedImage.TYPE_BYTE_GRAY));
        when(scanner.scan(any())).thenReturn(receipt);
        return receipt;
    }

    private void answerItems() throws Exception {
        when(client.ask(any(), eq(OllamaReceiptReader.Items.class)))
                .thenReturn(ITEMS);
    }

    private void answerSummary() throws Exception {
        when(client.ask(any(), eq(OllamaReceiptReader.Summary.class)))
                .thenReturn(new OllamaReceiptReader.Summary(SUMMARY));
    }
}
