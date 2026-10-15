package com.github.deripas.tgbot.receipt.store;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Receipts in memory, lost on restart: to try out the data model before the database.
 */
final class InMemoryReceiptRepository implements ReceiptRepository {

    private final TreeMap<Long, Entry> receipts = new TreeMap<>();
    private final Map<String, Long> idsByHash = new HashMap<>();
    private long lastId;

    @Override
    public synchronized StoredReceipt create(Long userId, ReceiptPhoto photo) throws DuplicatePhotoException {
        final Long existing = idsByHash.get(photo.hash());
        if (existing != null) {
            throw new DuplicatePhotoException(existing);
        }
        final StoredReceipt receipt = new StoredReceipt(
                ++lastId, userId, Instant.now(), null, ReceiptStatus.CREATED, null, null, null);
        receipts.put(receipt.id(), new Entry(receipt, photo));
        idsByHash.put(photo.hash(), receipt.id());
        return receipt;
    }

    @Override
    public synchronized Optional<StoredReceipt> findById(long id) {
        return Optional.ofNullable(receipts.get(id)).map(Entry::receipt);
    }

    @Override
    public synchronized List<StoredReceipt> findLatest(int limit) {
        return receipts.descendingMap().values().stream()
                .limit(limit)
                .map(Entry::receipt)
                .toList();
    }

    @Override
    public synchronized Optional<PendingReceipt> takeNext() {
        return receipts.values().stream()
                .filter(entry -> entry.receipt().status() == ReceiptStatus.CREATED)
                .findFirst()
                .map(entry -> {
                    final StoredReceipt receipt = entry.receipt();
                    receipts.put(receipt.id(), entry.with(new StoredReceipt(receipt.id(), receipt.userId(),
                            receipt.createdAt(), null, ReceiptStatus.PROCESSING, null, null, null)));
                    return new PendingReceipt(receipt.id(), entry.photo().mimeType(), entry.photo().content());
                });
    }

    @Override
    public synchronized void update(long id, ReceiptResult result) {
        final Entry entry = receipts.get(id);
        if (entry == null) {
            return;
        }
        final StoredReceipt receipt = entry.receipt();
        receipts.put(id, entry.with(new StoredReceipt(receipt.id(), receipt.userId(), receipt.createdAt(),
                result.purchasedAt(), result.status(), result.reason(), result.storeName(), result.total())));
    }

    @Override
    public synchronized Optional<StoredReceipt> findSamePurchase(long id, LocalDateTime purchasedAt, BigDecimal total) {
        return receipts.values().stream()
                .map(Entry::receipt)
                .filter(receipt -> receipt.id() != id
                        && receipt.status() != ReceiptStatus.DUPLICATE
                        && purchasedAt.equals(receipt.purchasedAt())
                        && receipt.total() != null && total.compareTo(receipt.total()) == 0)
                .findFirst();
    }

    @Override
    public synchronized boolean delete(long id) {
        final Entry entry = receipts.remove(id);
        if (entry == null) {
            return false;
        }
        idsByHash.remove(entry.photo().hash());
        return true;
    }

    private record Entry(StoredReceipt receipt, ReceiptPhoto photo) {

        Entry with(StoredReceipt receipt) {
            return new Entry(receipt, photo);
        }
    }
}
