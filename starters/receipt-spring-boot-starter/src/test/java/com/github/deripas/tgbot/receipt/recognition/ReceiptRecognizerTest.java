package com.github.deripas.tgbot.receipt.recognition;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptRecognizerTest {

    private static final String JSON = """
            {"store":"PEPCO d.o.o.","taxId":"111295900","dateTime":"25.09.2026 18:53:38",
             "items":[{"name":"T-shirt majica","price":1000.0,"quantity":1.0,"total":1000.0},
                      {"name":"poklon kesa","price":120.0,"quantity":1.0,"total":120.0}],
             "total":1120.0,"paymentMethod":"card","vatAmount":186.67,"receiptNumber":"SRPW4AYN-SRPW4AYN-132368"}
            """;

    private final List<Prompt> prompts = new ArrayList<>();

    @Test
    void parsesModelAnswerAndSendsResizedJpeg() throws IOException {
        final ReceiptRecognizer recognizer = new ReceiptRecognizer(answering(JSON), "qwen2.5vl:7b", 600);

        final Recognition recognition = recognizer.recognize(png(1200, 2400));

        assertThat(recognition.receipt().store()).isEqualTo("PEPCO d.o.o.");
        assertThat(recognition.receipt().items()).extracting(RecognizedReceipt.Item::total).containsExactly(1000.0, 120.0);
        assertThat(recognition.receipt().total()).isEqualTo(1120.0);
        assertThat(recognition.model()).isEqualTo("qwen2.5vl:7b");
        assertThat(recognition.rawJson()).isEqualTo(JSON);

        final BufferedImage sent = ImageIO.read(new ByteArrayInputStream(recognition.jpeg()));
        assertThat(sent.getWidth()).isEqualTo(600);
        assertThat(sent.getHeight()).isEqualTo(1200);

        final UserMessage user = prompts.getFirst().getUserMessage();
        assertThat(user.getMedia()).singleElement()
                .satisfies(media -> assertThat(media.getMimeType().toString()).isEqualTo("image/jpeg"));
        assertThat(prompts.getFirst().getSystemMessage().getText()).contains("JSON schema");
    }

    @Test
    void emptyAnswerIsAnError() {
        final ReceiptRecognizer recognizer = new ReceiptRecognizer(answering(""), "qwen3-vl:8b", 600);

        assertThatThrownBy(() -> recognizer.recognize(png(100, 200)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("qwen3-vl:8b");
    }

    @Test
    void unsupportedImageFormatIsAnError() {
        final ReceiptRecognizer recognizer = new ReceiptRecognizer(answering(JSON), "qwen2.5vl:7b", 600);

        assertThatThrownBy(() -> recognizer.recognize("not an image".getBytes()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HEIC");
    }

    private ChatModel answering(String json) {
        return prompt -> {
            prompts.add(prompt);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        };
    }

    private static byte[] png(int width, int height) throws IOException {
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
