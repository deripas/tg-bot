package com.github.deripas.tgbot.receipt.store;

/**
 * Processing status of a receipt; {@link StoredReceipt#reason()} explains every status but {@link #CREATED}.
 */
public enum ReceiptStatus {

    /**
     * The photo is stored and waits for processing: the stored receipts in this status are the queue of the worker.
     */
    CREATED,
    /**
     * The receipt is read and adds up; warnings along the way do not matter if the result is reliable.
     */
    SUCCESS,
    /**
     * The receipt is read, but something does not add up, e.g. the items do not sum up to the total;
     * the reason tells what a human should check.
     */
    NEED_REVIEW,
    /**
     * The same purchase (total and time) as another receipt photographed differently; the reason holds its id.
     */
    DUPLICATE,
    /**
     * The receipt could not be processed at all and needs manual work.
     */
    ERROR
}
