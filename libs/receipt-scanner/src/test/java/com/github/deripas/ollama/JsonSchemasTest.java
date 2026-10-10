package com.github.deripas.ollama;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemasTest {

    @Test
    void testSchemaFollowsAnnotations() {
        assertThat(JsonSchemas.of(Answer.class)).isEqualTo(Map.of(
                "type", "object",
                "properties", Map.of(
                        "store", Map.of("type", "string", "description", "Store name"),
                        "box", Map.of(
                                "type", "array",
                                "items", Map.of("type", "integer"),
                                "minItems", 4,
                                "maxItems", 4),
                        "lines", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of("text", Map.of("type", "string")),
                                        "required", List.of("text")))),
                "required", List.of("store", "box", "lines")));
    }

    @Test
    void testPropertiesKeepDeclarationOrder() {
        @SuppressWarnings("unchecked")
        final Map<String, Object> properties = (Map<String, Object>) JsonSchemas.of(Answer.class).get("properties");

        assertThat(properties.keySet()).containsExactly("store", "box", "lines");
    }

    @Test
    void testSchemaIsCached() {
        assertThat(JsonSchemas.of(Answer.class)).isSameAs(JsonSchemas.of(Answer.class));
    }

    record Answer(
            @JsonPropertyDescription("Store name")
            String store,
            @Size(min = 4, max = 4)
            List<Integer> box,
            List<Line> lines
    ) {
    }

    record Line(String text) {
    }
}
