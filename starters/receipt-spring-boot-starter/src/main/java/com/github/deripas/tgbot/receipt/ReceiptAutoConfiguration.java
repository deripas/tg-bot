package com.github.deripas.tgbot.receipt;

import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.receipt.ReceiptParsers;
import com.github.deripas.tgbot.receipt.bot.BotConfiguration;
import com.github.deripas.tgbot.receipt.process.ProcessConfiguration;
import com.github.deripas.tgbot.receipt.store.StoreConfiguration;
import com.github.deripas.tgbot.receipt.web.WebConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Receipts skill. The modules are the subpackages, each with its configuration:
 * {@code store} keeps the receipts, {@code process} reads them with a vision model one by one,
 * {@code bot} and {@code web} are two channels to upload and view them.
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "tgbot.receipt",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(ReceiptProperties.class)
@Import({StoreConfiguration.class, ProcessConfiguration.class, BotConfiguration.class, WebConfiguration.class})
public class ReceiptAutoConfiguration {

    /**
     * The receipt is taken by its fiscal QR code if readable, otherwise read with the vision model, see
     * {@link ReceiptParsers#qrOrModel(String)}; only the Ollama address is set.
     */
    @Bean
    @ConditionalOnMissingBean
    ReceiptParser receiptParser(ReceiptProperties properties) {
        return ReceiptParsers.qrOrModel(properties.ollamaBaseUrl());
    }
}
