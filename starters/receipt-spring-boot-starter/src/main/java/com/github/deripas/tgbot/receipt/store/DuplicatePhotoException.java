package com.github.deripas.tgbot.receipt.store;

import lombok.Getter;

/**
 * The photo has already been uploaded: no receipt is created for it.
 */
@Getter
public class DuplicatePhotoException extends Exception {

    /**
     * Receipt the photo was uploaded with.
     */
    private final long receiptId;

    public DuplicatePhotoException(long receiptId) {
        super("The photo has already been uploaded with receipt " + receiptId);
        this.receiptId = receiptId;
    }
}
