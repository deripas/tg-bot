package com.github.deripas.tgbot.receipt.process;

import com.github.deripas.image.ImageUtils;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.ReceiptParser;
import com.github.deripas.tgbot.receipt.store.PendingReceipt;
import com.github.deripas.tgbot.receipt.store.ReceiptCreated;
import com.github.deripas.tgbot.receipt.store.ReceiptResult;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.store.ReceiptStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.ConnectException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reads the uploaded receipts one at a time, minutes each. The queue is the stored receipts in
 * {@link ReceiptStatus#CREATED}: {@link ReceiptCreated} and the start of the application only wake the worker up.
 * <p>
 * A receipt the model could not be asked about, because Ollama is not available, goes back to the queue
 * and waits for the next wake-up; any other failure is an {@link ReceiptStatus#ERROR} for a human.
 */
@Slf4j
public class ReceiptWorker implements DisposableBean {

    private final ReceiptService receipts;
    private final ReceiptParser parser;
    private final Country country;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("receipt-worker").factory()
    );

    /**
     * A drain is submitted and has not started yet: wake-ups before it starts are served by it.
     */
    private final AtomicBoolean scheduled = new AtomicBoolean();

    public ReceiptWorker(ReceiptService receipts, ReceiptParser parser, Country country) {
        this.receipts = receipts;
        this.parser = parser;
        this.country = country;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        wakeUp();
    }

    @EventListener
    public void onCreated(ReceiptCreated event) {
        wakeUp();
    }

    void wakeUp() {
        if (scheduled.compareAndSet(false, true)) {
            executor.execute(this::drain);
        }
    }

    private void drain() {
        scheduled.set(false);
        Optional<PendingReceipt> next;
        while ((next = receipts.takeNext()).isPresent()) {
            if (!process(next.get())) {
                return;
            }
        }
    }

    /**
     * @return {@code false} if the receipt is returned to the queue and the rest should wait too
     */
    private boolean process(PendingReceipt pending) {
        final long start = System.nanoTime();
        ReceiptResult result;
        try {
            final BufferedImage image = ImageUtils.load(pending.photo());
            result = result(parser.parse(image, country));
        } catch (ConnectException e) {
            log.warn("Receipt {} waits: the model is not available: {}", pending.id(), e.toString());
            receipts.release(pending.id());
            return false;
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to read receipt {}", pending.id(), e);
            result = ReceiptResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        receipts.complete(pending.id(), result);
        log.info("Receipt {} is read in {} s: {}", pending.id(), (System.nanoTime() - start) / 1_000_000_000,
                result.status());
        return true;
    }

    /**
     * A receipt whose items do not sum up to the total, or with other problems, needs a review;
     * warnings of a valid receipt are kept as the reason.
     */
    static ReceiptResult result(Receipt receipt) {
        final String problems = receipt.errors() == null || receipt.errors().isEmpty()
                ? null
                : String.join("; ", receipt.errors());
        return new ReceiptResult(
                receipt.valid() ? ReceiptStatus.SUCCESS : ReceiptStatus.WARNING,
                problems,
                receipt.store(),
                receipt.dateTime(),
                receipt.total());
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
