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
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks how the receipt is cut into frames and what the model is asked, with the model mocked;
 * reading real receipts is checked by {@code OllamaReceiptReaderTester}.
 */
@ExtendWith(MockitoExtension.class)
class OllamaReceiptReaderTest {

    private static final OllamaReceiptReader.Items ITEMS = new OllamaReceiptReader.Items(
            List.of(new OllamaReceipt.Item("Hleb", "100,00", "1", "100,00")));
    private static final OllamaReceipt.Summary SUMMARY = new OllamaReceipt.Summary("LIDL", "10.10.2026", List.of());

    private static final Dimension FRAME = VisionModel.QWEN_2_5_VL.maxImageSize(new Dimension(4, 3));
    private static final String PART = "This image is a horizontal part of a long receipt photo.";
    private static final String TOP_CUT = "The top edge of the image cuts through the receipt.";
    private static final String BOTTOM_CUT = "The bottom edge of the image cuts through the receipt.";

    @Mock
    private ReceiptScanner scanner;
    @Mock
    private OllamaClient client;
    private OllamaReceiptReader reader;

    @BeforeEach
    void setUp() {
        reader = new OllamaReceiptReader(scanner, client, VisionModel.QWEN_2_5_VL);
    }

    @Test
    void shortReceiptIsReadAsOneFrame() throws Exception {
        final ImageData receipt = receipt(3);
        answerItems();
        answerSummary();

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(text.windows()).containsExactly(
                new OllamaReceipt.Window(0, receipt.getHeight(), true, true, ITEMS.items(), SUMMARY));
        verify(client).ask(argThat(request -> request.model() == VisionModel.QWEN_2_5_VL
                        && request.prompt().equals(OllamaReceiptReader.itemsPrompt(true, true))
                        && request.images().size() == 1),
                eq(OllamaReceiptReader.Items.class));
    }

    @Test
    void itemsPromptTellsWhichEdgesCutReceipt() {
        assertThat(OllamaReceiptReader.itemsPrompt(true, true))
                .doesNotContain(PART, TOP_CUT, BOTTOM_CUT)
                .endsWith("leftmost.\n\nRead the purchased items from the photo.\n");
        assertThat(OllamaReceiptReader.itemsPrompt(false, false))
                .endsWith("leftmost.\n\n" + PART + "\n" + TOP_CUT + "\n" + BOTTOM_CUT
                        + "\nRead the purchased items from the photo.\n");
    }

    /**
     * A frame of 4:3 holds 0.75 of the width of the receipt: about 6 items of 70 px on 600 px.
     */
    @Test
    void longReceiptIsReadInFramesMeetingEachOther() throws Exception {
        final ImageData receipt = receipt(20);
        final ArgumentCaptor<OllamaRequest> requests = ArgumentCaptor.forClass(OllamaRequest.class);
        when(client.ask(requests.capture(), eq(OllamaReceiptReader.Items.class)))
                .thenReturn(ITEMS);
        answerSummary();

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        final List<OllamaReceipt.Window> windows = text.windows();
        assertThat(windows).hasSizeGreaterThan(2);
        for (int i = 1; i < windows.size(); i++) {
            assertThat(windows.get(i).top()).isEqualTo(windows.get(i - 1).bottom());
        }
        assertThat(windows.getFirst().top()).isZero();
        assertThat(windows.getLast().bottom()).isEqualTo(receipt.getHeight());
        assertThat(windows).extracting(OllamaReceipt.Window::first).containsOnlyOnce(true);
        assertThat(windows.getFirst().first()).isTrue();
        assertThat(windows).extracting(OllamaReceipt.Window::last).containsOnlyOnce(true);
        assertThat(windows.getLast().last()).isTrue();
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.images()).singleElement().satisfies(image -> {
                assertThat(image.getWidth()).isLessThanOrEqualTo(FRAME.width);
                assertThat(image.getHeight()).isLessThanOrEqualTo(FRAME.height);
            });
        });
        final List<String> prompts = requests.getAllValues().stream().map(OllamaRequest::prompt).toList();
        assertThat(prompts.getFirst())
                .contains(BOTTOM_CUT)
                .doesNotContain(TOP_CUT);
        assertThat(prompts.get(1))
                .contains(TOP_CUT, BOTTOM_CUT);
        assertThat(prompts.getLast())
                .contains(TOP_CUT)
                .doesNotContain(BOTTOM_CUT);
    }

    /**
     * The parts overlap, so that a line cut by the edge of one is whole in the other; they are merged as windows
     * of the rows of the frame they cover.
     */
    @Test
    void frameIsReadInTwoOverlappingPartsWhenModelFails() throws Exception {
        final ImageData receipt = receipt(3);
        final int height = receipt.getHeight();
        when(client.ask(any(), eq(OllamaReceiptReader.Items.class)))
                .thenThrow(new IOException("looped"))
                .thenReturn(ITEMS);
        answerSummary();

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(text.windows()).hasSize(2);
        final OllamaReceipt.Window upper = text.windows().getFirst();
        final OllamaReceipt.Window lower = text.windows().getLast();
        assertThat(upper.top()).isZero();
        assertThat(upper.bottom()).isCloseTo((int) (height * 0.6), within(2));
        assertThat(upper.first()).isTrue();
        assertThat(upper.last()).isFalse();
        assertThat(lower.top()).isCloseTo((int) (height * 0.4), within(2));
        assertThat(lower.bottom()).isEqualTo(height);
        assertThat(lower.first()).isFalse();
        assertThat(lower.last()).isTrue();
    }

    @Test
    void failsWhenPartOfFrameFails() throws Exception {
        final ImageData receipt = receipt(3);
        when(client.ask(any(), eq(OllamaReceiptReader.Items.class)))
                .thenThrow(new IOException("looped"));

        assertThatThrownBy(() -> reader.read(receipt, Country.SERBIA))
                .isInstanceOf(IOException.class);
    }

    @Test
    void frameWithUnreadTotalsHasNone() throws Exception {
        final ImageData receipt = receipt(3);
        answerItems();
        when(client.ask(any(), eq(OllamaReceiptReader.Summary.class)))
                .thenThrow(new IOException("looped"));

        final OllamaReceipt text = reader.read(receipt, Country.SERBIA);

        assertThat(text.windows()).singleElement()
                .extracting(OllamaReceipt.Window::summary)
                .isEqualTo(OllamaReceipt.Summary.EMPTY);
    }

    /**
     * @return scanned receipt of the items, returned by the scanner as is
     */
    private ImageData receipt(int items) {
        final ImageData receipt = SyntheticReceipts.items(items, 0);
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
