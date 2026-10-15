package com.github.deripas.tgbot.receipt.process;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The worker; the {@link ReceiptParser} comes from the auto-configuration, set up by the settings.
 */
@Configuration(proxyBeanMethods = false)
public class ProcessConfiguration {

    /**
     * Receipts of one country so far.
     */
    static final Country COUNTRY = Country.SERBIA;

    @Bean
    ReceiptWorker receiptWorker(ReceiptService receipts, ReceiptParser parser) {
        return new ReceiptWorker(receipts, parser, COUNTRY);
    }
}
