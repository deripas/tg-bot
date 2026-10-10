package com.github.deripas.tgbot.receipt.bot;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.Reply;
import com.github.deripas.tgbot.receipt.store.DuplicatePhotoException;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.store.ReceiptStatus;
import com.github.deripas.tgbot.receipt.store.StoredReceipt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BillCommandTest {

    private static final byte[] PHOTO = {1, 2, 3};

    private final ReceiptService receipts = mock(ReceiptService.class);
    private final BillCommand command = new BillCommand(receipts);

    @Test
    void addStoresPhotoAndRepliesWithId() throws DuplicatePhotoException {
        when(receipts.add(2L, "image/jpeg", PHOTO)).thenReturn(receipt(7, ReceiptStatus.CREATED, null));
        final FakeContext context = new FakeContext("add", List.of(attachment("application/pdf"), attachment("image/jpeg")));

        command.handle(context);

        assertThat(context.replies).containsExactly("Receipt 7 is added. Check it later: /bill status 7");
    }

    @Test
    void billWithPhotoAdds() throws DuplicatePhotoException {
        when(receipts.add(2L, "image/jpeg", PHOTO)).thenReturn(receipt(7, ReceiptStatus.CREATED, null));
        final FakeContext context = new FakeContext("", List.of(attachment("image/jpeg")));

        command.handle(context);

        assertThat(context.replies).containsExactly("Receipt 7 is added. Check it later: /bill status 7");
    }

    @Test
    void addOfUploadedPhotoRepliesWithItsReceipt() throws DuplicatePhotoException {
        when(receipts.add(2L, "image/jpeg", PHOTO)).thenThrow(new DuplicatePhotoException(3));
        final FakeContext context = new FakeContext("add", List.of(attachment("image/jpeg")));

        command.handle(context);

        assertThat(context.replies).containsExactly("This photo has already been uploaded: /bill status 3");
    }

    @Test
    void addWithoutPhotoAsksForIt() {
        final FakeContext context = new FakeContext("add", List.of(attachment("application/pdf")));

        command.handle(context);

        assertThat(context.replies).containsExactly(BillCommand.NO_PHOTO);
        verifyNoInteractions(receipts);
    }

    @Test
    void statusShowsReason() {
        when(receipts.find(5)).thenReturn(Optional.of(receipt(5, ReceiptStatus.NEED_REVIEW, "Items do not sum up to the total")));
        final FakeContext context = new FakeContext("status 5", List.of());

        command.handle(context);

        assertThat(context.replies).containsExactly("Receipt 5: NEED_REVIEW\nItems do not sum up to the total");
    }

    @Test
    void statusOfMissingReceipt() {
        when(receipts.find(5)).thenReturn(Optional.empty());
        final FakeContext context = new FakeContext("status 5", List.of());

        command.handle(context);

        assertThat(context.replies).containsExactly("Receipt 5 is not found");
    }

    @Test
    void deleteRepliesWhetherDeleted() {
        when(receipts.delete(5)).thenReturn(true);
        final FakeContext deleted = new FakeContext("delete 5", List.of());
        final FakeContext missing = new FakeContext("delete 6", List.of());

        command.handle(deleted);
        command.handle(missing);

        assertThat(deleted.replies).containsExactly("Receipt 5 is deleted");
        assertThat(missing.replies).containsExactly("Receipt 6 is not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "show 5", "status", "status x", "delete", "add 5", "delete 5 6"})
    void otherArgumentsShowUsage(String args) {
        final FakeContext context = new FakeContext(args, List.of());

        command.handle(context);

        assertThat(context.replies).containsExactly(BillCommand.USAGE);
        verifyNoInteractions(receipts);
    }

    private static StoredReceipt receipt(long id, ReceiptStatus status, String reason) {
        return new StoredReceipt(id, 2L, Instant.now(), null, status, reason, null);
    }

    private static Attachment attachment(String mimeType) {
        return new Attachment() {
            @Override
            public String mimeType() {
                return mimeType;
            }

            @Override
            public String fileName() {
                return "file";
            }

            @Override
            public byte[] content() {
                return PHOTO;
            }
        };
    }

    private record FakeContext(String args, List<Attachment> attachments, List<String> replies) implements CommandContext {

        FakeContext(String args, List<Attachment> attachments) {
            this(args, attachments, new ArrayList<>());
        }

        @Override
        public boolean isPrivateChat() {
            return true;
        }

        @Override
        public long chatId() {
            return 1;
        }

        @Override
        public long userId() {
            return 2;
        }

        @Override
        public Reply reply(String text) {
            replies.add(text);
            return newText -> {
                throw new UnsupportedOperationException();
            };
        }
    }
}
