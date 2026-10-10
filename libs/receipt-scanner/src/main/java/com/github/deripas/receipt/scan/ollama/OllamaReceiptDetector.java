package com.github.deripas.receipt.scan.ollama;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.github.deripas.image.ImageData;
import com.github.deripas.image.ImageSize;
import com.github.deripas.ollama.ModelAnswer;
import com.github.deripas.ollama.OllamaClient;
import com.github.deripas.ollama.OllamaRequest;
import com.github.deripas.ollama.VisionModel;
import com.github.deripas.receipt.scan.ReceiptDetector;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;

import java.awt.Dimension;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;

/**
 * Finds the receipt with a vision model served by Ollama: one request on a downscaled copy for the bounding box
 * of the receipt paper. Works where thresholds fail, e.g. on a busy background, but the box is axis-aligned,
 * so a tilted receipt is not straightened.
 */
@Slf4j
public class OllamaReceiptDetector implements ReceiptDetector {

    static final String LAYOUT_PROMPT = """
            Locate the paper receipt in the image.
            Report the bounding box of the whole receipt paper.
            """;

    /**
     * A smaller box is treated as a model mistake.
     */
    private static final double MIN_AREA = 0.05;


    private final OllamaClient client;
    private final VisionModel model;

    public OllamaReceiptDetector() {
        this(OllamaClient.DEFAULT, VisionModel.QWEN_2_5_VL);
    }

    /**
     * @param client transport to Ollama
     * @param model  model that finds the receipt
     */
    public OllamaReceiptDetector(OllamaClient client, VisionModel model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public Optional<Polygon> detect(ImageData image) {
        try {
            // The model answers in pixels of the image it sees, so it is sent the image in that size
            final Dimension size = model.minImageSize(new Dimension(image.getWidth(), image.getHeight()));
            final ImageData sample = image.transform()
                    .resize(size.width, size.height)
                    .asImage();
            final OllamaRequest request = OllamaRequest.builder()
                    .model(model)
                    .prompt(LAYOUT_PROMPT)
                    .image(sample.toImage())
                    .build();
            final Layout layout = client.ask(request, Layout.class);
            log.info("Receipt layout: {}", layout);
            return toBounds(layout.box(), image.getSize(), sample.getSize())
                    .map(OllamaReceiptDetector::toPolygon);
        } catch (Exception e) {
            log.error("Error detecting receipt", e);
            return Optional.empty();
        }
    }

    /**
     * Scales the box from the sample sent to the model to the photo.
     *
     * @param box        box answered by the model, in pixels of the sample
     * @param imageSize  size of the photo
     * @param sampleSize size of the sample
     * @return receipt bounds in pixels of the photo; empty if the box is malformed or too small
     */
    private static Optional<Rectangle> toBounds(
            List<Integer> box,
            ImageSize imageSize,
            ImageSize sampleSize
    ) {
        if (box == null || box.size() != 4 || box.contains(null)) {
            log.warn("Malformed receipt box {}", box);
            return Optional.empty();
        }
        final int x1 = Math.clamp(box.get(0), 0, sampleSize.width());
        final int y1 = Math.clamp(box.get(1), 0, sampleSize.height());
        final int x2 = Math.clamp(box.get(2), 0, sampleSize.width());
        final int y2 = Math.clamp(box.get(3), 0, sampleSize.height());
        final long area = (long) (x2 - x1) * (y2 - y1);
        if (x2 <= x1 || y2 <= y1 || area < MIN_AREA * sampleSize.area()) {
            log.warn("Receipt box {} is too small", box);
            return Optional.empty();
        }
        final double scaleX = (double) imageSize.width() / sampleSize.width();
        final double scaleY = (double) imageSize.height() / sampleSize.height();
        final int left = (int) Math.floor(x1 * scaleX);
        final int top = (int) Math.floor(y1 * scaleY);
        final int right = Math.min(imageSize.width(), (int) Math.ceil(x2 * scaleX));
        final int bottom = Math.min(imageSize.height(), (int) Math.ceil(y2 * scaleY));
        return Optional.of(new Rectangle(left, top, right - left, bottom - top));
    }

    private static Polygon toPolygon(Rectangle bounds) {
        final int right = bounds.x + bounds.width;
        final int bottom = bounds.y + bounds.height;
        return new Polygon(
                new int[]{bounds.x, right, right, bounds.x},
                new int[]{bounds.y, bounds.y, bottom, bottom},
                4);
    }

    /**
     * Layout answer of the model.
     */
    @ModelAnswer(maxTokens = 64)
    record Layout(
            @JsonPropertyDescription("Bounding box of the receipt paper [x1, y1, x2, y2] in pixels of this image")
            @Size(min = 4, max = 4)
            List<Integer> box
    ) {
    }
}
