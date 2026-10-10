package com.github.deripas.tgbot.receipt;

import com.github.deripas.tgbot.receipt.bot.BotConfiguration;
import com.github.deripas.tgbot.receipt.store.StoreConfiguration;
import com.github.deripas.tgbot.receipt.web.WebConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * Receipts skill. The modules are the subpackages, each with its configuration:
 * {@code store} keeps the receipts, {@code bot} and {@code web} are two channels to upload and view them.
 */
@AutoConfiguration
@ConditionalOnProperty(
        prefix = "tgbot.receipt",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableConfigurationProperties(ReceiptProperties.class)
@Import({StoreConfiguration.class, BotConfiguration.class, WebConfiguration.class})
public class ReceiptAutoConfiguration {
}
