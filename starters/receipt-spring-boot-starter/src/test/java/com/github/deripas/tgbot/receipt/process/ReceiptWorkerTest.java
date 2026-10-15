package com.github.deripas.tgbot.receipt.process;

import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.tgbot.receipt.store.PendingReceipt;
import com.github.deripas.tgbot.receipt.store.ReceiptResult;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.store.ReceiptStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceiptWorkerTest {

    private static final LocalDateTime PURCHASED_AT = LocalDateTime.of(2026, 9, 25, 18, 53, 38);
    private static final long WAIT = Duration.ofSeconds(5).toMillis();

    private final ReceiptService receipts = mock(ReceiptService.class);
    private final ReceiptParser parser = mock(ReceiptParser.class);
    private final ReceiptWorker worker = new ReceiptWorker(receipts, parser, Country.SERBIA);

    @AfterEach
    void stop() {
        worker.destroy();
    }

    @Test
    void readsQueuedReceiptsOneByOne() throws IOException {
        when(receipts.takeNext()).thenReturn(Optional.of(pending(1)), Optional.of(pending(2)), Optional.empty());
        when(parser.parse(any(), eq(Country.SERBIA))).thenReturn(receipt(true, List.of()));

        worker.wakeUp();

        final ReceiptResult success = new ReceiptResult(ReceiptStatus.SUCCESS, null, "PEPCO", PURCHASED_AT,
                new BigDecimal("1120.00"));
        verify(receipts, timeout(WAIT)).complete(1, success);
        verify(receipts, timeout(WAIT)).complete(2, success);
    }

    @Test
    void receiptThatDoesNotAddUpNeedsReview() {
        assertThat(ReceiptWorker.result(receipt(false, List.of("Items sum up to 1000.00", "No date"))))
                .returns(ReceiptStatus.WARNING, ReceiptResult::status)
                .returns("Items sum up to 1000.00; No date", ReceiptResult::reason);
    }

    @Test
    void failureIsError() throws IOException {
        when(receipts.takeNext()).thenReturn(Optional.of(pending(1)), Optional.of(new PendingReceipt(2, "image/jpeg", new byte[]{1})),
                Optional.empty());
        when(parser.parse(any(), any())).thenThrow(new IOException("Model returned malformed items"));

        worker.wakeUp();

        verify(receipts, timeout(WAIT)).complete(1, ReceiptResult.error("Model returned malformed items"));
        verify(receipts, timeout(WAIT)).complete(2, ReceiptResult.error("Unsupported image format"));
    }

    @Test
    void receiptWaitsForModel() throws IOException {
        when(receipts.takeNext()).thenReturn(Optional.of(pending(1)), Optional.of(pending(2)));
        when(parser.parse(any(), any())).thenThrow(new ConnectException("Connection refused"));

        worker.wakeUp();

        verify(receipts, timeout(WAIT)).release(1);
        verify(receipts, never()).complete(anyLong(), any());
        verify(receipts, never()).release(2);
    }

    private static PendingReceipt pending(long id) throws IOException {
        final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "jpg", jpeg);
        return new PendingReceipt(id, "image/jpeg", jpeg.toByteArray());
    }

    private static Receipt receipt(boolean valid, List<String> errors) {
        return new Receipt(valid, "PEPCO", PURCHASED_AT, List.of(), new BigDecimal("1120.00"), Currency.RSD, "{}", errors);
    }
}
