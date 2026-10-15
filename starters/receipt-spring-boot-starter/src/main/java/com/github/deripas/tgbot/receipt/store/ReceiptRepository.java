package com.github.deripas.tgbot.receipt.store;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Storage of receipts and their photos.
 */
interface ReceiptRepository {

    /**
     * Creates a receipt in {@link ReceiptStatus#CREATED} with the photo; checks the hash and stores atomically.
     *
     * @throws DuplicatePhotoException if a receipt with the same photo hash exists
     */
    StoredReceipt create(Long userId, ReceiptPhoto photo) throws DuplicatePhotoException;

    Optional<StoredReceipt> findById(long id);

    /**
     * @return receipts, the last uploaded first
     */
    List<StoredReceipt> findLatest(int limit);

    /**
     * Moves the first uploaded receipt in {@link ReceiptStatus#CREATED} to {@link ReceiptStatus#PROCESSING}.
     *
     * @return the receipt with its photo, empty if there is none
     */
    Optional<PendingReceipt> takeNext();

    /**
     * Sets the status and the read data of the receipt; does nothing if the receipt has been deleted.
     */
    void update(long id, ReceiptResult result);

    /**
     * @return another receipt with the same purchase, not a {@link ReceiptStatus#DUPLICATE} itself
     */
    Optional<StoredReceipt> findSamePurchase(long id, LocalDateTime purchasedAt, BigDecimal total);

    /**
     * Deletes the receipt with its photo.
     *
     * @return {@code false} if there is no such receipt
     */
    boolean delete(long id);
}
