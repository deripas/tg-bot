package com.github.deripas.tgbot.receipt.store;

import java.time.Instant;
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
                ++lastId, userId, Instant.now(), null, ReceiptStatus.CREATED, null, null);
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
    public synchronized boolean delete(long id) {
        final Entry entry = receipts.remove(id);
        if (entry == null) {
            return false;
        }
        idsByHash.remove(entry.photo().hash());
        return true;
    }

    private record Entry(StoredReceipt receipt, ReceiptPhoto photo) {
    }
}
