package com.github.deripas.tgbot.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the receipts skill; the rest are the defaults of the receipt-scanner library.
 *
 * @param enabled       enable the skill: the {@code /bill} command and the Web UI
 * @param ollamaBaseUrl Ollama address for reading receipts
 */
@ConfigurationProperties("tgbot.receipt")
public record ReceiptProperties(

        @DefaultValue("true")
        boolean enabled,

        @DefaultValue("http://localhost:11434")
        String ollamaBaseUrl
) {
}
