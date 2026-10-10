package com.github.deripas.tgbot.receipt.store;

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
     * Deletes the receipt with its photo.
     *
     * @return {@code false} if there is no such receipt
     */
    boolean delete(long id);
}
