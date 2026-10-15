package com.github.deripas.tgbot.receipt.web;

import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.tgbot.receipt.process.ReceiptWorker;
import com.github.deripas.tgbot.receipt.store.DuplicatePhotoException;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.store.StoredReceipt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The starter in an empty application: the page is rendered by Thymeleaf. The worker is replaced,
 * so that the uploaded receipts stay {@code CREATED}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReceiptWebTest {

    private static final MockMultipartFile PHOTO = new MockMultipartFile("photo", "receipt.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @MockitoBean
    private ReceiptWorker worker;

    @MockitoBean
    private ReceiptParser parser;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReceiptService receipts;

    @Test
    void emptyListOffersUpload() throws Exception {
        mvc.perform(get("/receipts"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"photo\"")))
                .andExpect(content().string(containsString("Чеков пока нет")));
    }

    @Test
    void uploadedReceiptIsListed() throws Exception {
        mvc.perform(multipart("/receipts").file(PHOTO))
                .andExpect(redirectedUrl("/receipts"))
                .andExpect(flash().attribute("added", 1L));

        assertThat(receipts.latest(10)).extracting(StoredReceipt::id).containsExactly(1L);
        mvc.perform(get("/receipts").flashAttr("added", 1L))
                .andExpect(content().string(containsString("Чек <b>1</b> добавлен")))
                .andExpect(content().string(containsString("<tr class=\"CREATED\"")))
                .andExpect(content().string(containsString("/receipts/1/delete")));
    }

    @Test
    void receiptIsUploadedByLoggedInUser() throws Exception {
        mvc.perform(multipart("/receipts").file(PHOTO).principal(() -> "5"))
                .andExpect(redirectedUrl("/receipts"));

        assertThat(receipts.latest(10)).extracting(StoredReceipt::userId).containsExactly(5L);
    }

    @Test
    void statusesArePolled() throws Exception {
        final long id = receipts.add(null, "image/jpeg", PHOTO.getBytes()).id();

        mvc.perform(get("/receipts"))
                .andExpect(content().string(containsString("data-id=\"" + id + "\"")))
                .andExpect(content().string(containsString("data-status=\"CREATED\"")))
                .andExpect(content().string(containsString("data-statuses=\"/receipts/statuses\"")));
        mvc.perform(get("/receipts/statuses"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"" + id + "\": \"CREATED\"}"));
    }

    @Test
    void uploadOfSamePhotoIsRejected() throws Exception {
        receipts.add(null, "image/jpeg", PHOTO.getBytes());

        mvc.perform(multipart("/receipts").file(PHOTO))
                .andExpect(redirectedUrl("/receipts"))
                .andExpect(flash().attribute("duplicate", 1L));
        assertThat(receipts.latest(10)).hasSize(1);
    }

    @Test
    void uploadOfNotImageShowsError() throws Exception {
        mvc.perform(multipart("/receipts").file(new MockMultipartFile("photo", "a.pdf", "application/pdf", new byte[]{1})))
                .andExpect(redirectedUrl("/receipts"))
                .andExpect(flash().attribute("error", "Not an image: application/pdf"));
    }

    @Test
    void deletedReceiptIsNotListed() throws Exception {
        final long id = receipts.add(null, "image/jpeg", PHOTO.getBytes()).id();

        mvc.perform(post("/receipts/{id}/delete", id))
                .andExpect(redirectedUrl("/receipts"))
                .andExpect(flash().attribute("deleted", id));

        mvc.perform(get("/receipts"))
                .andExpect(content().string(not(containsString("/receipts/" + id + "/delete"))));
    }

    @SpringBootApplication
    static class TestApplication {
    }
}
