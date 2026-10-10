package com.github.deripas.tgbot.receipt.web;

import com.github.deripas.tgbot.api.WebModule;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    @Bean
    ReceiptController receiptController(ReceiptService receipts) {
        return new ReceiptController(receipts);
    }

    @Bean
    WebModule receiptsWebModule() {
        return new WebModule("Чеки", ReceiptController.PATH);
    }
}
