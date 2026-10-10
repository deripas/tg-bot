package com.github.deripas.tgbot.receipt.store;

import lombok.RequiredArgsConstructor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Receipts uploaded via the bot or the Web UI. A receipt is created with its photo and returned at once:
 * reading it takes minutes, the status is checked later by its id.
 */
@RequiredArgsConstructor
public class ReceiptService {

    private final ReceiptRepository repository;

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
        return repository.create(userId, new ReceiptPhoto(mimeType, content, sha256(content)));
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
