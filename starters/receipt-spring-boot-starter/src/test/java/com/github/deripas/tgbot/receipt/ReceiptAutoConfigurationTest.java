package com.github.deripas.tgbot.receipt;

import com.github.deripas.tgbot.api.WebModule;
import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.tgbot.receipt.bot.BillCommand;
import com.github.deripas.tgbot.receipt.process.ReceiptWorker;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.web.ReceiptController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ReceiptAutoConfiguration.class));

    @Test
    void enabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed()
                    .hasSingleBean(ReceiptService.class)
                    .hasSingleBean(BillCommand.class)
                    .hasSingleBean(ReceiptController.class)
                    .hasSingleBean(ReceiptParser.class)
                    .hasSingleBean(ReceiptWorker.class);
            assertThat(context.getBean(WebModule.class)).isEqualTo(new WebModule("Чеки", "/receipts"));
            assertThat(context.getBean(ReceiptProperties.class).ollamaBaseUrl()).isEqualTo("http://localhost:11434");
        });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("tgbot.receipt.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .doesNotHaveBean(ReceiptService.class)
                        .doesNotHaveBean(BillCommand.class)
                        .doesNotHaveBean(ReceiptWorker.class));
    }
}
