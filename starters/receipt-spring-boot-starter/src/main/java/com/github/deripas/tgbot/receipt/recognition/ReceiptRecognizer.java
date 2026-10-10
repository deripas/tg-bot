package com.github.deripas.tgbot.receipt.recognition;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;

import java.io.IOException;

/**
 * Распознавание чека vision-моделью с ответом строго по JSON-схеме {@link RecognizedReceipt}.
 * Промпт на английском: на тестовом чеке он дал 9/10 полей против 8/10 у русского.
 */
public class ReceiptRecognizer {

    static final String SYSTEM_PROMPT = """
            You extract data from photos of sales receipts.
            Use only information from the attached image.

            Follow the provided JSON schema.
            Copy the store and item names exactly as printed;
            do not translate or transliterate them.

            Amounts and quantities are decimal numbers
            with a dot and no thousands separators.
            For example: "1.120,00" -> 1120.00.

            If a field is missing or unreadable, return null. Do not invent data.
            """;

    static final String USER_PROMPT = "Extract the receipt data from the image.";

    private final ChatClient chatClient;
    private final BeanOutputConverter<RecognizedReceipt> converter;
    private final String model;
    private final int maxImageWidth;

    /**
     * @param chatModel модель с опциями {@code format = converter.getJsonSchemaMap()}, см. {@link #converter()}
     */
    public ReceiptRecognizer(ChatModel chatModel, String model, int maxImageWidth) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
        this.converter = converter();
        this.model = model;
        this.maxImageWidth = maxImageWidth;
    }

    public static BeanOutputConverter<RecognizedReceipt> converter() {
        return new BeanOutputConverter<>(RecognizedReceipt.class);
    }

    public Recognition recognize(byte[] image) throws IOException {
        final byte[] jpeg = ReceiptImages.toJpeg(image, maxImageWidth);
        final String json = chatClient.prompt()
                .user(user -> user
                        .text(USER_PROMPT)
                        .media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(jpeg)))
                .call()
                .content();
        if (json == null || json.isBlank()) {
            throw new IOException("Модель " + model + " вернула пустой ответ");
        }
        return new Recognition(converter.convert(json), json, model, jpeg);
    }
}
