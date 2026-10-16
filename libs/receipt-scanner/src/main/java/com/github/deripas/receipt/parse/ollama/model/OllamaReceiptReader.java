package com.github.deripas.receipt.parse.ollama.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.github.deripas.image.ImageData;
import com.github.deripas.ollama.ModelAnswer;
import com.github.deripas.ollama.OllamaClient;
import com.github.deripas.ollama.OllamaRequest;
import com.github.deripas.ollama.PromptTemplates;
import com.github.deripas.ollama.VisionModel;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.scan.ReceiptScanner;
import lombok.extern.slf4j.Slf4j;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Reads a receipt with a vision model served by Ollama:
 * <ol>
 *     <li>the {@link ReceiptScanner} cuts the receipt out and turns it upright;</li>
 *     <li>the scan is cut into strips of lines along the gaps between them, see {@link ReceiptStrips};</li>
 *     <li>the strips are put together into frames of the largest image of {@link #WINDOW} proportions the model
 *     is sent, see {@link ReceiptFrames}: a frame holds about a dozen items in characters large enough for the
 *     model, ends between items and does not overlap the next one, so every item is read once;</li>
 *     <li>items and totals lines are read from each frame in two short requests, numbers as printed text.</li>
 * </ol>
 * Separate short requests: on long answers the model starts looping, and when asked for the total on an image
 * without one, it computes or invents it.
 * <p>
 * The model is told which edges of a frame cut through the receipt, so that it skips lines broken by them:
 * a frame ends between lines, but where no cut between items is found, between the name of an item and its
 * numbers.
 * Telling it the last items of the previous frame makes things worse: the model copies them from the request
 * instead of reading the image.
 */
@Slf4j
public class OllamaReceiptReader {

    /**
     * Proportions of a frame. With qwen2.5vl:7b the frame is 1148x868 = 41x31 patches: 1148 px of width give about
     * 28 px per character of a 40-character line, and the height of 0.75 of the receipt width holds about a dozen
     * items; 4:5 held about 20, too many for the model.
     */
    private static final Dimension WINDOW = new Dimension(4, 3);

    /**
     * Height of each of the two parts of a frame the model failed to read, relative to the frame.
     */
    private static final double SPLIT_HEIGHT = 0.6;

    /**
     * Template of the items request, see {@link PromptTemplates}: {@code first} and {@code last} tell whether
     * the frame starts at the top and ends at the bottom of the receipt. The model is told which edges cut
     * the receipt, so that it skips lines broken by them.
     */
    private static final String ITEMS_PROMPT = """
            You read purchased items from a photo of a sales receipt.
            The photo may show only a part of a long receipt. Ignore the header, totals, taxes, payment and footer.
            
            An item is usually printed in two lines:
              line 1: item name, often with unit, product code and tax label,
                      e.g. "Kefir 2,8%, 1kg/KOM/0209361 (E)"
              line 2: unit price, quantity (pieces or weight) and line total,
                      e.g. "132,49        1        132,49" or "1.199,99    0,379    454,80"
            Some receipts print an item in one line: name, then the numbers.
            The numbers of an item are printed right below its name, never above it.
            
            Rules:
            - Go from the top edge of the photo down. The first item in your list is the item closest
              to the top edge, the last one is the item closest to the bottom edge.
            - If the photo starts with a line of numbers whose item name is above the top edge, skip those numbers:
              they belong to an item that is not in the photo.
            - If the photo ends with an item name whose numbers are below the bottom edge, skip that item.
            - Every printed item is a separate entry. When the same item is printed several times
              in a row, return it as many times as it is printed. Never merge or skip items.
            - Copy names and numbers exactly as printed, character by character.
              Do not translate, transliterate, compute or correct anything.
            - Numbers are text exactly as printed, with their separators: "1.199,99", "0,379".
            - The line total is the rightmost number of the item; the unit price is the leftmost.
            
            <if(!first || !last)>
            This image is a horizontal part of a long receipt photo.
            <endif>
            <if(!first)>
            The top edge of the image cuts through the receipt.
            <endif>
            <if(!last)>
            The bottom edge of the image cuts through the receipt.
            <endif>
            Read the purchased items from the photo.
            """;

    private static final String SUMMARY_PROMPT = """
            You read the header and the totals of a sales receipt from a photo.
            The photo may show only a part of a long receipt.
            
            Rules:
            - Copy text exactly as printed, character by character. Do not translate or transliterate.
            - Fill a field only if it is printed in this photo. Otherwise return null.
            - totalsLines: every line printed after the last purchased item (total amount, payment, change,
              taxes), top to bottom, each line in full: its label and its amount, e.g. "Total: 1.120,00".
              If the photo has no such lines, return an empty list.
            - Never compute, sum up or guess anything.
            
            Read the receipt header and totals from the photo.
            """;


    private final ReceiptScanner scanner;
    private final OllamaClient client;
    private final VisionModel model;
    /**
     * Size of a frame sent to the model.
     */
    private final Dimension frame;

    public OllamaReceiptReader(ReceiptScanner scanner) {
        this(scanner, OllamaClient.DEFAULT, VisionModel.QWEN_2_5_VL);
    }

    /**
     * @param client transport to Ollama
     * @param model  model that reads the receipt; its largest image gives the frame
     */
    public OllamaReceiptReader(ReceiptScanner scanner, OllamaClient client, VisionModel model) {
        this.scanner = scanner;
        this.client = client;
        this.model = model;
        this.frame = model.maxImageSize(WINDOW);
    }

    public OllamaReceipt read(ImageData image, Country country) throws IOException {
        final ImageData receipt = scanner.scan(image);
        final List<ReceiptFrames.Frame> frames = ReceiptFrames.assemble(receipt, ReceiptStrips.cut(receipt), frame);
        final List<OllamaReceipt.Window> windows = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            final ReceiptFrames.Frame next = frames.get(i);
            windows.addAll(
                    readWindow(
                            next.image(),
                            next.top(),
                            next.bottom(),
                            String.valueOf(i + 1),
                            next.first(),
                            next.last(),
                            true
                    )
            );
        }
        return new OllamaReceipt(windows);
    }

    /**
     * Reads items and totals of a frame. If the model fails on the items (loops or hits the token limit),
     * the frame is split into two smaller overlapping parts: shorter answers loop less.
     * If it fails on the totals, the frame is taken as having none.
     *
     * @param image  the frame
     * @param top    first row of the frame in the scan
     * @param bottom row after the last one in the scan
     * @param name   frame number for messages, e.g. "2" or "2.1" for a part of a split frame
     * @param split  whether the frame may be split on failure
     */
    private List<OllamaReceipt.Window> readWindow(
            ImageData image,
            int top,
            int bottom,
            String name,
            boolean first,
            boolean last,
            boolean split
    ) throws IOException {
        final BufferedImage window = image.toImage();

        final List<OllamaReceipt.Item> items;
        try {
            items = ask(itemsPrompt(first, last), Items.class, window, "items of frame " + name).items();
        } catch (IOException e) {
            if (!split) {
                throw e;
            }
            log.warn("Failed to read items of frame {}, reading it in two parts: {}", name, e.getMessage());
            final int height = (int) Math.round(image.getHeight() * SPLIT_HEIGHT);
            // the rows of the scan the parts cover, in proportion to the frame
            final int rows = (int) Math.round((double) (bottom - top) * height / image.getHeight());
            final List<OllamaReceipt.Window> part1 = readWindow(
                    image.transform().cropRows(0, height).asImage(),
                    top, top + rows, name + ".1", first, false, false);
            final List<OllamaReceipt.Window> part2 = readWindow(
                    image.transform().cropRows(image.getHeight() - height, image.getHeight()).asImage(),
                    bottom - rows, bottom, name + ".2", false, last, false);

            return union(part1, part2);
        }
        OllamaReceipt.Summary summary;
        try {
            summary = ask(SUMMARY_PROMPT, Summary.class, window, "totals of frame " + name).summary();
        } catch (IOException e) {
            log.warn("Failed to read totals of frame {}, taking it as having none: {}", name, e.getMessage());
            summary = OllamaReceipt.Summary.EMPTY;
        }
        log.info("Frame {} [{}, {}): {} items, summary {}", name, top, bottom,
                items == null ? 0 : items.size(), summary);
        return List.of(new OllamaReceipt.Window(top, bottom, first, last, items, summary));
    }

    private <T> T ask(String prompt, Class<T> type, BufferedImage window, String what) throws IOException {
        try {
            final OllamaRequest request = OllamaRequest.builder()
                    .model(model)
                    .prompt(prompt)
                    .image(window)
                    .build();
            final T answer = client.ask(request, type);
            return requireNonNull(answer, "empty answer");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted reading " + what, e);
        } catch (IOException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            // a request that does not fit the context is a mistake in the code, not a failure of the model
            throw e;
        } catch (RuntimeException e) {
            // malformed JSON: the model looped or hit the token limit
            throw new IOException("Model returned malformed " + what + ": " + e.getMessage(), e);
        }
    }

    static String itemsPrompt(boolean first, boolean last) {
        return PromptTemplates.render(ITEMS_PROMPT, Map.of("first", first, "last", last));
    }

    private static <T> List<T> union(List<T> part1, List<T> part2) {
        final List<T> parts = new ArrayList<>(part1.size() + part2.size());
        parts.addAll(part1);
        parts.addAll(part2);
        return parts;
    }


    /**
     * Items answer of the model.
     */
    @ModelAnswer(maxTokens = 2048)
    record Items(
            @JsonPropertyDescription("Purchased items from top to bottom, each printed item separately")
            List<OllamaReceipt.Item> items
    ) {
    }

    /**
     * Header and totals answer of the model: the fields of {@link OllamaReceipt.Summary} at the top level.
     */
    @ModelAnswer(maxTokens = 512)
    record Summary(@JsonUnwrapped OllamaReceipt.Summary summary) {
    }
}
