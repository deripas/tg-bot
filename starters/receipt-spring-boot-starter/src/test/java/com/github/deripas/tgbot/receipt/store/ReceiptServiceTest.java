package com.github.deripas.tgbot.receipt.store;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptServiceTest {

    private static final Long USER = 1L;
    private static final byte[] PHOTO = {1, 2, 3};
    private static final byte[] OTHER_PHOTO = {4, 5, 6};
    private static final LocalDateTime PURCHASED_AT = LocalDateTime.of(2026, 9, 25, 18, 53, 38);

    private final List<Object> events = new ArrayList<>();
    private final ReceiptService service = new ReceiptService(new InMemoryReceiptRepository(), events::add);

    @Test
    void addedReceiptIsCreatedAndAnnounced() throws DuplicatePhotoException {
        final StoredReceipt receipt = service.add(USER, "image/jpeg", PHOTO);

        assertThat(receipt.userId()).isEqualTo(USER);
        assertThat(receipt.purchasedAt()).isNull();
        assertThat(receipt.total()).isNull();
        assertThat(receipt.status()).isEqualTo(ReceiptStatus.CREATED);
        assertThat(receipt.reason()).isNull();
        assertThat(receipt.storeName()).isNull();
        assertThat(service.find(receipt.id())).contains(receipt);
        assertThat(events).containsExactly(new ReceiptCreated(receipt.id()));
    }

    @Test
    void samePhotoIsRejectedWithIdOfItsReceipt() throws DuplicatePhotoException {
        final StoredReceipt receipt = service.add(USER, "image/jpeg", PHOTO);

        assertThatThrownBy(() -> service.add(USER, "image/png", PHOTO.clone()))
                .isInstanceOfSatisfying(DuplicatePhotoException.class,
                        e -> assertThat(e.getReceiptId()).isEqualTo(receipt.id()));
        assertThat(service.latest(10)).containsExactly(receipt);
        assertThat(events).hasSize(1);
    }

    @Test
    void photoOfDeletedReceiptCanBeAddedAgain() throws DuplicatePhotoException {
        final StoredReceipt receipt = service.add(USER, "image/jpeg", PHOTO);

        assertThat(service.delete(receipt.id())).isTrue();

        assertThat(service.find(receipt.id())).isEmpty();
        assertThat(service.add(USER, "image/jpeg", PHOTO).id()).isNotEqualTo(receipt.id());
    }

    @Test
    void deletingMissingReceiptReturnsFalse() {
        assertThat(service.delete(42)).isFalse();
    }

    @Test
    void latestAreNewestFirstAndLimited() throws DuplicatePhotoException {
        final StoredReceipt first = service.add(USER, "image/jpeg", PHOTO);
        final StoredReceipt second = service.add(USER, "image/jpeg", OTHER_PHOTO);

        assertThat(service.latest(10)).containsExactly(second, first);
        assertThat(service.latest(1)).containsExactly(second);
    }

    @Test
    void notImageOrEmptyFileIsRejected() {
        assertThatThrownBy(() -> service.add(USER, "application/pdf", PHOTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Not an image: application/pdf");
        assertThatThrownBy(() -> service.add(USER, null, PHOTO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.add(USER, "image/jpeg", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Empty file");
        assertThat(service.latest(10)).isEmpty();
    }

    @Test
    void receiptsAreTakenFirstUploadedFirstOnce() throws DuplicatePhotoException {
        final StoredReceipt first = service.add(USER, "image/jpeg", PHOTO);
        final StoredReceipt second = service.add(USER, "image/heic", OTHER_PHOTO);

        final PendingReceipt pending = service.takeNext().orElseThrow();

        assertThat(pending.id()).isEqualTo(first.id());
        assertThat(pending.photo()).isEqualTo(PHOTO);
        assertThat(service.find(first.id()).orElseThrow().status()).isEqualTo(ReceiptStatus.PROCESSING);
        assertThat(service.takeNext().orElseThrow().mimeType()).isEqualTo("image/heic");
        assertThat(service.takeNext()).isEmpty();
        assertThat(second.status()).isEqualTo(ReceiptStatus.CREATED);
    }

    @Test
    void resultIsSaved() throws DuplicatePhotoException {
        final long id = service.add(USER, "image/jpeg", PHOTO).id();
        service.takeNext();

        service.complete(id, new ReceiptResult(ReceiptStatus.WARNING, "Items sum up to 1000.00",
                "PEPCO", PURCHASED_AT, new BigDecimal("1120.00")));

        final StoredReceipt receipt = service.find(id).orElseThrow();
        assertThat(receipt.status()).isEqualTo(ReceiptStatus.WARNING);
        assertThat(receipt.reason()).isEqualTo("Items sum up to 1000.00");
        assertThat(receipt.storeName()).isEqualTo("PEPCO");
        assertThat(receipt.purchasedAt()).isEqualTo(PURCHASED_AT);
        assertThat(receipt.total()).isEqualTo(new BigDecimal("1120.00"));
    }

    @Test
    void samePurchaseIsDuplicateOfFirstReceipt() throws DuplicatePhotoException {
        final long first = service.add(USER, "image/jpeg", PHOTO).id();
        final long second = service.add(USER, "image/jpeg", OTHER_PHOTO).id();
        final long third = service.add(USER, "image/jpeg", new byte[]{7}).id();

        service.complete(first, read(ReceiptStatus.SUCCESS, "1120.00"));
        service.complete(second, new ReceiptResult(ReceiptStatus.WARNING, "No date", "PEPCO", PURCHASED_AT,
                new BigDecimal("1120.0")));
        service.complete(third, read(ReceiptStatus.SUCCESS, "1120.00"));

        assertThat(service.find(first).orElseThrow().status()).isEqualTo(ReceiptStatus.SUCCESS);
        assertThat(service.find(second).orElseThrow())
                .returns(ReceiptStatus.DUPLICATE, StoredReceipt::status)
                .returns("Duplicate of receipt " + first + "; No date", StoredReceipt::reason);
        assertThat(service.find(third).orElseThrow().reason()).isEqualTo("Duplicate of receipt " + first);
    }

    @Test
    void otherPurchaseOrErrorIsNotDuplicate() throws DuplicatePhotoException {
        final long first = service.add(USER, "image/jpeg", PHOTO).id();
        final long second = service.add(USER, "image/jpeg", OTHER_PHOTO).id();
        final long third = service.add(USER, "image/jpeg", new byte[]{7}).id();
        service.complete(first, read(ReceiptStatus.SUCCESS, "1120.00"));

        service.complete(second, read(ReceiptStatus.SUCCESS, "1121.00"));
        service.complete(third, new ReceiptResult(ReceiptStatus.ERROR, "Failed", null, PURCHASED_AT, new BigDecimal("1120.00")));

        assertThat(service.find(second).orElseThrow().status()).isEqualTo(ReceiptStatus.SUCCESS);
        assertThat(service.find(third).orElseThrow().status()).isEqualTo(ReceiptStatus.ERROR);
    }

    @Test
    void releasedReceiptIsTakenAgain() throws DuplicatePhotoException {
        final long id = service.add(USER, "image/jpeg", PHOTO).id();
        service.takeNext();

        service.release(id);

        assertThat(service.find(id).orElseThrow().status()).isEqualTo(ReceiptStatus.CREATED);
        assertThat(service.takeNext().orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void resultOfDeletedReceiptIsDropped() throws DuplicatePhotoException {
        final long id = service.add(USER, "image/jpeg", PHOTO).id();
        service.takeNext();
        service.delete(id);

        service.complete(id, read(ReceiptStatus.SUCCESS, "1120.00"));

        assertThat(service.find(id)).isEmpty();
    }

    private static ReceiptResult read(ReceiptStatus status, String total) {
        return new ReceiptResult(status, null, "PEPCO", PURCHASED_AT, new BigDecimal(total));
    }
}
