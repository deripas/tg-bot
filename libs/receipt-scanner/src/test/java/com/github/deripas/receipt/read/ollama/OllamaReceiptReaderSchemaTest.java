package com.github.deripas.receipt.read.ollama;

import com.github.deripas.ollama.JsonSchemas;
import com.github.deripas.receipt.read.ReceiptText;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schemas were written by hand before they were generated, and the model was checked with them.
 */
class OllamaReceiptReaderSchemaTest {

    @Test
    void testItemsSchema() {
        final Map<String, Object> schema = JsonSchemas.of(OllamaReceiptReader.Items.class);

        assertThat(schema).isEqualTo(Map.of(
                "type", "object",
                "properties", Map.of(
                        "items", Map.of(
                                "type", "array",
                                "description", "Purchased items from top to bottom, each printed item separately",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "name", string("Item name line exactly as printed, with unit, product code and tax label"),
                                                "price", string("Unit price exactly as printed"),
                                                "quantity", string("Quantity or weight exactly as printed"),
                                                "total", string("Line total exactly as printed, the rightmost number of the item")),
                                        "required", List.of("name", "price", "quantity", "total")))),
                "required", List.of("items")));
        assertThat(properties(item(schema))).containsExactly("name", "price", "quantity", "total");
    }

    @Test
    void testSummarySchema() {
        final Map<String, Object> schema = JsonSchemas.of(OllamaReceiptReader.Summary.class);

        assertThat(schema).isEqualTo(Map.of(
                "type", "object",
                "properties", Map.of(
                        "store", string("Seller or store name from the receipt header; null if not in this photo"),
                        "dateTime", string("Date and time of the purchase exactly as printed; null if not in this photo"),
                        "totalsLines", Map.of(
                                "type", "array",
                                "description", "Every line after the last purchased item (totals, payment, taxes), "
                                        + "each with its label and amount exactly as printed, top to bottom; empty if none in this photo",
                                "items", Map.of("type", "string"))),
                "required", List.of("store", "dateTime", "totalsLines")));
        assertThat(properties(schema)).containsExactly("store", "dateTime", "totalsLines");
    }

    @Test
    void testSummaryIsReadAtTopLevel() {
        final OllamaReceiptReader.Summary answer = JsonMapper.builder().build().readValue(
                "{\"store\": \"LIDL\", \"dateTime\": null, \"totalsLines\": [\"Total: 1.120,00\"]}",
                OllamaReceiptReader.Summary.class);

        assertThat(answer.summary()).isEqualTo(new ReceiptText.Summary("LIDL", null, List.of("Total: 1.120,00")));
    }

    private static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<String> properties(Map<String, Object> schema) {
        return ((Map<String, Object>) schema.get("properties")).keySet();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> schema) {
        final Map<String, Object> items = (Map<String, Object>) ((Map<String, Object>) schema.get("properties")).get("items");
        return (Map<String, Object>) items.get("items");
    }
}
