package com.github.deripas.ollama;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Question to a vision model: one user message with images.
 *
 * @param model  model to ask
 * @param prompt text of the message
 * @param images images attached to the message, sent as JPEG
 */
@Builder
public record OllamaRequest(
        @NonNull VisionModel model,
        @NonNull String prompt,
        @Singular List<BufferedImage> images
) {
}
