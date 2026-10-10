package com.github.deripas.ollama;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OllamaClientTest {

    private static final VisionModel MODEL = VisionModel.QWEN_2_5_VL;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void testAnswerMustBeAnnotated() {
        final OllamaRequest request = OllamaRequest.builder().model(MODEL).prompt("prompt").build();

        assertThatThrownBy(() -> OllamaClient.DEFAULT.ask(request, NotAnswer.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@ModelAnswer");
    }

    /**
     * A receipt window: the largest image, a prompt of the length of the items prompt and the longest answer.
     */
    @Test
    void testLargestImageWithPromptAndAnswerFits() {
        final OllamaRequest request = request("x".repeat(1900), image(896, 1120));

        assertThatCode(() -> OllamaClient.checkContext(request, 2048)).doesNotThrowAnyException();
    }

    @Test
    void testRequestOverContextFails() {
        final OllamaRequest request = request("prompt", image(1792, 1120));

        assertThatThrownBy(() -> OllamaClient.checkContext(request, 2048))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("images 2560");
    }

    /**
     * The body was built as a map before, and Ollama was checked with it.
     */
    @Test
    void testChatRequestJson() {
        final OllamaClient.ChatRequest chat = new OllamaClient.ChatRequest(
                "qwen2.5vl:7b",
                List.of(new OllamaClient.Message("user", "prompt", List.of("jpeg"))),
                false,
                Map.of("type", "object"),
                new OllamaClient.Options(0, 64, 4096));

        assertThat(MAPPER.readTree(MAPPER.writeValueAsString(chat))).isEqualTo(MAPPER.valueToTree(Map.of(
                "model", "qwen2.5vl:7b",
                "stream", false,
                "options", Map.of("temperature", 0.0, "num_predict", 64, "num_ctx", 4096),
                "messages", List.of(Map.of("role", "user", "content", "prompt", "images", List.of("jpeg"))),
                "format", Map.of("type", "object"))));
    }

    @Test
    void testChatResponseIsRead() {
        final OllamaClient.ChatResponse response = MAPPER.readValue("""
                {"model": "qwen2.5vl:7b", "created_at": "2026-10-15T10:00:00Z",
                 "message": {"role": "assistant", "content": "{\\"box\\": [1, 2, 3, 4]}"},
                 "done": true, "total_duration": 1000}""", OllamaClient.ChatResponse.class);

        assertThat(response.message().content()).isEqualTo("{\"box\": [1, 2, 3, 4]}");
    }

    private static OllamaRequest request(String prompt, BufferedImage image) {
        return OllamaRequest.builder().model(MODEL).prompt(prompt).image(image).build();
    }

    private static BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    }

    record NotAnswer(String text) {
    }
}
