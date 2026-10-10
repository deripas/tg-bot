package com.github.deripas.receipt.scan.ollama;

import com.github.deripas.ollama.JsonSchemas;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OllamaReceiptDetectorSchemaTest {

    /**
     * The schema written by hand before it was generated: the model was checked with it.
     */
    @Test
    void testLayoutSchema() {
        assertThat(JsonSchemas.of(OllamaReceiptDetector.Layout.class)).isEqualTo(Map.of(
                "type", "object",
                "properties", Map.of(
                        "box", Map.of(
                                "type", "array",
                                "items", Map.of("type", "integer"),
                                "minItems", 4,
                                "maxItems", 4,
                                "description", "Bounding box of the receipt paper [x1, y1, x2, y2] in pixels of this image")),
                "required", List.of("box")));
    }
}
