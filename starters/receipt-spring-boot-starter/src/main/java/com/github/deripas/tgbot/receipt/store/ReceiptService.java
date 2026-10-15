package com.github.deripas.tgbot.receipt.store;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Receipts uploaded via the bot or the Web UI. A receipt is created with its photo and returned at once:
 * reading it takes minutes, the status is checked later by its id. The worker is told about a new receipt
 * with {@link ReceiptCreated} and takes the receipts one by one with {@link #takeNext()}.
 */
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository repository;
    private final ApplicationEventPublisher events;

    /**
     * @param userId   user who uploads the receipt, {@code null} if unknown
     * @param mimeType type of the photo, must be an image
     * @param content  photo file
     * @return created receipt in {@link ReceiptStatus#CREATED}
     * @throws DuplicatePhotoException  if the same file has already been uploaded
     * @throws IllegalArgumentException if the file is empty or not an image
     */
    public StoredReceipt add(Long userId, String mimeType, byte[] content) throws DuplicatePhotoException {
        if (mimeType == null || !mimeType.startsWith("image/")) {
            throw new IllegalArgumentException("Not an image: " + mimeType);
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("Empty file");
        }
        final StoredReceipt receipt = repository.create(userId, new ReceiptPhoto(mimeType, content, sha256(content)));
        events.publishEvent(new ReceiptCreated(receipt.id()));
        return receipt;
    }

    /**
     * Takes the first uploaded receipt waiting for processing and moves it to {@link ReceiptStatus#PROCESSING}.
     *
     * @return the receipt with its photo, empty if there is none
     */
    public Optional<PendingReceipt> takeNext() {
        return repository.takeNext();
    }

    /**
     * Returns the receipt to the queue: it could not be processed now, e.g. the model is not available.
     */
    public void release(long id) {
        repository.update(id, new ReceiptResult(ReceiptStatus.CREATED, null, null, null, null));
    }

    /**
     * Saves the result of processing. A receipt read well enough, with the same purchase time and total
     * as another one, is a {@link ReceiptStatus#DUPLICATE}: the same receipt photographed again;
     * the problems found in it stay in the reason.
     */
    public void complete(long id, ReceiptResult result) {
        final boolean read = result.status() == ReceiptStatus.SUCCESS || result.status() == ReceiptStatus.WARNING;
        final Optional<StoredReceipt> original = read && result.purchasedAt() != null && result.total() != null
                ? repository.findSamePurchase(id, result.purchasedAt(), result.total())
                : Optional.empty();
        repository.update(id, original
                .map(receipt -> new ReceiptResult(ReceiptStatus.DUPLICATE,
                        "Duplicate of receipt " + receipt.id() + (result.reason() == null ? "" : "; " + result.reason()),
                        result.storeName(), result.purchasedAt(), result.total()))
                .orElse(result));
    }

    public Optional<StoredReceipt> find(long id) {
        return repository.findById(id);
    }

    /**
     * @return receipts, the last uploaded first
     */
    public List<StoredReceipt> latest(int limit) {
        return repository.findLatest(limit);
    }

    /**
     * @return {@code false} if there is no such receipt
     */
    public boolean delete(long id) {
        return repository.delete(id);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
