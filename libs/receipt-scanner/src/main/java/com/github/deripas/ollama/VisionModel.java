package com.github.deripas.ollama;

import lombok.Builder;

import java.awt.Dimension;

/**
 * Vision model served by Ollama and the parameters that depend on it; they are set together for each model.
 * <p>
 * The size of an image is measured in image tokens: the model turns every {@link #patch} x {@link #patch} pixel
 * block into one token. An image is sent in a size between {@link #minImageTokens} and {@link #maxImageTokens},
 * with both sides multiples of the patch, so that Ollama does not resize it.
 *
 * @param name           Ollama model tag
 * @param patch          side of the pixel block the model turns into one image token
 * @param minImageTokens Ollama upscales a smaller image to this number of tokens, so a smaller image would not
 *                       be faster, and the model would answer in coordinates of the upscaled image
 * @param maxImageTokens largest image sent: it leaves room for the prompt and the answer in the context
 * @param contextTokens  context of a request ({@code num_ctx}): image, prompt and answer
 */
@Builder
public record VisionModel(
        String name,
        int patch,
        int minImageTokens,
        int maxImageTokens,
        int contextTokens
) {

    /**
     * The best vision model under 8 GB in our tests; the context fits an 8 GB GPU. The largest image is 1280
     * tokens: with a prompt of ~500 tokens and an answer of up to 2048 it fits the context of 4096.
     */
    public static final VisionModel QWEN_2_5_VL = VisionModel.builder()
            .name("qwen2.5vl:7b")
            .patch(28)
            .minImageTokens(1024)
            .maxImageTokens(1280)
            .contextTokens(4096)
            .build();

    public VisionModel {
        if (minImageTokens > maxImageTokens) {
            throw new IllegalArgumentException("Min image tokens " + minImageTokens
                    + " exceed max image tokens " + maxImageTokens);
        }
        if (maxImageTokens > contextTokens) {
            throw new IllegalArgumentException("Max image tokens " + maxImageTokens
                    + " exceed the context of " + contextTokens);
        }
    }

    /**
     * Smallest size the model sees an image of the given proportions in: at least {@link #minImageTokens}.
     * The model answers with coordinates in pixels of the image it sees, so an image sent in this size gets
     * coordinates in its own pixels; a smaller one would be upscaled by Ollama.
     */
    public Dimension minImageSize(Dimension proportions) {
        return gridSize(proportions, minImageTokens, true);
    }

    /**
     * Largest size of an image of the given proportions the model is sent: at most {@link #maxImageTokens}.
     * Gives the most detail, e.g. characters large enough to read.
     */
    public Dimension maxImageSize(Dimension proportions) {
        return gridSize(proportions, maxImageTokens, false);
    }

    /**
     * @return image tokens of an image of this size
     */
    public int imageTokens(Dimension size) {
        return Math.ceilDiv(size.width, patch) * Math.ceilDiv(size.height, patch);
    }

    /**
     * @param up round the sides up to the grid, so that the image has at least {@code tokens};
     *           otherwise down, so that it has at most {@code tokens}
     */
    private Dimension gridSize(Dimension proportions, int tokens, boolean up) {
        // Patches along each side when the area is exactly the given number of tokens
        final double scale = Math.sqrt(tokens / ((double) proportions.width * proportions.height));
        final double columns = proportions.width * scale;
        final double rows = proportions.height * scale;
        final int width = Math.max(1, (int) (up ? Math.ceil(columns) : Math.floor(columns))) * patch;
        final int height = Math.max(1, (int) (up ? Math.ceil(rows) : Math.floor(rows))) * patch;
        return new Dimension(width, height);
    }
}
