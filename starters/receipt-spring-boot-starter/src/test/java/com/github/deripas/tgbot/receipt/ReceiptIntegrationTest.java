package com.github.deripas.tgbot.receipt;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.WebModule;
import com.github.deripas.tgbot.receipt.persistence.ReceiptRepository;
import com.github.deripas.tgbot.receipt.persistence.ReceiptStats;
import com.github.deripas.tgbot.receipt.recognition.Recognition;
import com.github.deripas.tgbot.receipt.recognition.RecognizedReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Стартер в «пустом» приложении: автоконфигурация, миграции Flyway, хранение и Web UI на настоящем Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReceiptIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    @Autowired
    private ReceiptRepository repository;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void registersCommandAndWebModule() {
        assertThat(context.getBeansOfType(Command.class).values()).extracting(Command::info).contains("add_bill");
        assertThat(context.getBeansOfType(WebModule.class).values()).contains(new WebModule("Чеки", "/receipts"));
    }

    @Test
    void savedReceiptIsShownInWebUi() throws Exception {
        final long id = repository.save(7L, 100L, new Recognition(new RecognizedReceipt(
                "PEPCO d.o.o.", "111295900", "25.09.2026. 18:53:38",
                List.of(new RecognizedReceipt.Item("T-shirt majica", 1000.0, 1.0, 1000.0),
                        new RecognizedReceipt.Item("poklon kesa", 120.0, 1.0, 120.0)),
                1120.0, "card", 186.67, "SRPW4AYN-SRPW4AYN-132368"), "{}", "qwen2.5vl:7b", JPEG));

        assertThat(repository.findById(id)).hasValueSatisfying(receipt -> {
            assertThat(receipt.purchasedAt()).hasToString("2026-09-25T18:53:38");
            assertThat(receipt.total()).isEqualByComparingTo("1120.00");
            assertThat(receipt.items()).extracting(item -> item.name()).containsExactly("T-shirt majica", "poklon kesa");
        });
        assertThat(repository.totalsByMonth(12)).contains(new ReceiptStats("2026-09", 1, new BigDecimal("1120.00")));

        mvc.perform(get("/receipts"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PEPCO d.o.o.")));
        mvc.perform(get("/receipts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("poklon kesa")));
        mvc.perform(get("/receipts/{id}/image", id))
                .andExpect(status().isOk())
                .andExpect(content().bytes(JPEG));
        mvc.perform(get("/receipts/receipts.css"))
                .andExpect(status().isOk());

        mvc.perform(post("/receipts/{id}/delete", id))
                .andExpect(redirectedUrl("/receipts"));
        assertThat(repository.findById(id)).isEmpty();
        mvc.perform(get("/receipts/{id}", id))
                .andExpect(status().isNotFound());
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
