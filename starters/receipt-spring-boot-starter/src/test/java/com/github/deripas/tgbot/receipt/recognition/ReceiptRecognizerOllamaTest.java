package com.github.deripas.tgbot.receipt.recognition;

import com.github.deripas.tgbot.receipt.ReceiptAutoConfiguration;
import com.github.deripas.tgbot.receipt.ReceiptProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Распознавание настоящей моделью. Запускается только с переменной окружения:
 * {@code OLLAMA_BASE_URL=http://localhost:11434 ./mvnw test -Dtest=ReceiptRecognizerOllamaTest}
 */
@EnabledIfEnvironmentVariable(named = "OLLAMA_BASE_URL", matches = ".+")
class ReceiptRecognizerOllamaTest {

    @Test
    void recognizesTestReceipt() throws Exception {
        final ReceiptProperties properties = new ReceiptProperties(true, System.getenv("OLLAMA_BASE_URL"),
                "qwen2.5vl:7b", 1200, 4096, 1024, Duration.ofMinutes(5));
        final ReceiptRecognizer recognizer = new ReceiptAutoConfiguration().receiptRecognizer(properties);

        final Recognition recognition = recognizer.recognize(
                new ClassPathResource("receipts/1/IMG_4177.jpg").getContentAsByteArray());

        System.out.println(recognition.rawJson());
        final RecognizedReceipt receipt = recognition.receipt();
        assertThat(receipt.store()).containsIgnoringCase("pepco");
        assertThat(receipt.total()).isEqualTo(1120.0);
        assertThat(receipt.items()).extracting(RecognizedReceipt.Item::total).containsExactlyInAnyOrder(1000.0, 120.0);
    }
}
