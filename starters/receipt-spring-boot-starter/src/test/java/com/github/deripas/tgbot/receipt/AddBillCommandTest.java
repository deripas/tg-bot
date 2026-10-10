package com.github.deripas.tgbot.receipt;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.Reply;
import com.github.deripas.tgbot.receipt.persistence.ReceiptRepository;
import com.github.deripas.tgbot.receipt.recognition.ReceiptRecognizer;
import com.github.deripas.tgbot.receipt.recognition.Recognition;
import com.github.deripas.tgbot.receipt.recognition.RecognizedReceipt;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AddBillCommandTest {

    private static final byte[] PHOTO = {1, 2, 3};
    private static final RecognizedReceipt RECEIPT = new RecognizedReceipt("PEPCO d.o.o.", "111295900", "25.09.2026 18:53:38",
            List.of(new RecognizedReceipt.Item("T-shirt majica", 1000.0, 1.0, 1000.0),
                    new RecognizedReceipt.Item("poklon kesa", 120.0, 1.0, 120.0)),
            1120.0, "card", 186.67, "SRPW4AYN-SRPW4AYN-132368");

    private final ReceiptRecognizer recognizer = mock(ReceiptRecognizer.class);
    private final ReceiptRepository repository = mock(ReceiptRepository.class);
    private final AddBillCommand command = new AddBillCommand(recognizer, repository);

    @Test
    void recognizesSavesAndEditsReplyWithSummary() throws IOException {
        final Recognition recognition = new Recognition(RECEIPT, "{}", "qwen2.5vl:7b", PHOTO);
        when(recognizer.recognize(PHOTO)).thenReturn(recognition);
        when(repository.save(7L, 100L, recognition)).thenReturn(12L);
        final FakeContext context = new FakeContext(List.of(image()));

        command.handle(context);

        assertThat(context.replies).containsExactly("Распознаю чек…");
        assertThat(context.edits).singleElement().asString()
                .contains("Чек #12 сохранён", "PEPCO d.o.o. · 25.09.2026 18:53:38", "- poklon kesa: 120.00",
                        "Итого: 1120.00 (НДС 186.67)", "http://bot.lan/receipts/12");
    }

    @Test
    void withoutImageAsksForPhoto() {
        final FakeContext context = new FakeContext(List.of());

        command.handle(context);

        assertThat(context.replies).containsExactly(AddBillCommand.NO_IMAGE);
        verifyNoInteractions(recognizer, repository);
    }

    @Test
    void recognitionErrorIsShownAndNothingIsSaved() throws IOException {
        when(recognizer.recognize(any())).thenThrow(new IOException("Модель вернула пустой ответ"));
        final FakeContext context = new FakeContext(List.of(image()));

        command.handle(context);

        assertThat(context.edits).singleElement().asString().contains("Модель вернула пустой ответ");
        verifyNoInteractions(repository);
    }

    @Test
    void summaryWithoutOptionalFields() {
        final RecognizedReceipt receipt = new RecognizedReceipt(null, null, null, null, null, null, null, null);

        assertThat(AddBillCommand.summary(1, receipt, Optional.empty()))
                .isEqualTo("Чек #1 сохранён\nИтого: ?");
    }

    private static Attachment image() {
        return new Attachment() {
            @Override
            public String mimeType() {
                return "image/jpeg";
            }

            @Override
            public String fileName() {
                return "photo.jpg";
            }

            @Override
            public byte[] content() {
                return PHOTO;
            }
        };
    }

    private static class FakeContext implements CommandContext {

        private final List<Attachment> attachments;
        final List<String> replies = new ArrayList<>();
        final List<String> edits = new ArrayList<>();

        FakeContext(List<Attachment> attachments) {
            this.attachments = attachments;
        }

        @Override
        public long chatId() {
            return 7L;
        }

        @Override
        public long userId() {
            return 100L;
        }

        @Override
        public String args() {
            return "";
        }

        @Override
        public List<Attachment> attachments() {
            return attachments;
        }

        @Override
        public Reply reply(String text) {
            replies.add(text);
            return edits::add;
        }

        @Override
        public Optional<String> webLink(String path) {
            return Optional.of("http://bot.lan" + path);
        }
    }
}
