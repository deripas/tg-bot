package com.github.deripas.tgbot.receipt.store;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReceiptServiceTest {

    private static final Long USER = 1L;
    private static final byte[] PHOTO = {1, 2, 3};
    private static final byte[] OTHER_PHOTO = {4, 5, 6};

    private final ReceiptService service = new ReceiptService(new InMemoryReceiptRepository());

    @Test
    void addedReceiptIsCreated() throws DuplicatePhotoException {
        final StoredReceipt receipt = service.add(USER, "image/jpeg", PHOTO);

        assertThat(receipt.userId()).isEqualTo(USER);
        assertThat(receipt.purchasedAt()).isNull();
        assertThat(receipt.status()).isEqualTo(ReceiptStatus.CREATED);
        assertThat(receipt.reason()).isNull();
        assertThat(receipt.storeName()).isNull();
        assertThat(service.find(receipt.id())).contains(receipt);
    }

    @Test
    void samePhotoIsRejectedWithIdOfItsReceipt() throws DuplicatePhotoException {
        final StoredReceipt receipt = service.add(USER, "image/jpeg", PHOTO);

        assertThatThrownBy(() -> service.add(USER, "image/png", PHOTO.clone()))
                .isInstanceOfSatisfying(DuplicatePhotoException.class,
                        e -> assertThat(e.getReceiptId()).isEqualTo(receipt.id()));
        assertThat(service.latest(10)).containsExactly(receipt);
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
}
