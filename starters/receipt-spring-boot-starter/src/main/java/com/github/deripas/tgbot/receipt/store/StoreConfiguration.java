package com.github.deripas.tgbot.receipt.store;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StoreConfiguration {

    @Bean
    ReceiptService receiptService() {
        return new ReceiptService(new InMemoryReceiptRepository());
    }
}
