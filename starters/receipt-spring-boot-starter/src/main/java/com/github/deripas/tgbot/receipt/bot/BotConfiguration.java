package com.github.deripas.tgbot.receipt.bot;

import com.github.deripas.tgbot.receipt.store.ReceiptService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BotConfiguration {

    @Bean
    BillCommand billCommand(ReceiptService receipts) {
        return new BillCommand(receipts);
    }
}
