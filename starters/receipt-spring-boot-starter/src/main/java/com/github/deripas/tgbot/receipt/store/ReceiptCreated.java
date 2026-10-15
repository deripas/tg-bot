package com.github.deripas.tgbot.receipt.store;

/**
 * A receipt waits for processing: a signal to the worker to look into the queue, the receipts in
 * {@link ReceiptStatus#CREATED}. The queue is the stored receipts, so none is lost if the signal is.
 *
 * @param receiptId created receipt
 */
public record ReceiptCreated(long receiptId) {
}
