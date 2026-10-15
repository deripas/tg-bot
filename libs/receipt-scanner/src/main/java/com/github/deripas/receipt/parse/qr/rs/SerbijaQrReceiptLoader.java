package com.github.deripas.receipt.parse.qr.rs;

import com.github.deripas.receipt.Currency;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.parse.qr.QrReceiptLoader;
import com.github.deripas.receipt.parse.qr.rs.suf.SufClient;
import com.github.deripas.receipt.parse.qr.rs.suf.SufInvoice;
import com.github.deripas.receipt.parse.qr.rs.suf.SufItem;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * Loads a Serbian receipt from the verification service of the Tax Administration by the link of its fiscal
 * QR code: the items, the total and the time exactly as fiscalized.
 */
@Slf4j
public class SerbijaQrReceiptLoader implements QrReceiptLoader {

    /**
     * The service gives the time in UTC; the receipt prints it in the local time.
     */
    private static final ZoneId BELGRADE = ZoneId.of("Europe/Belgrade");

    private final SufClient client;

    public SerbijaQrReceiptLoader() {
        this(new SufClient());
    }

    public SerbijaQrReceiptLoader(SufClient client) {
        this.client = client;
    }

    @Override
    public Receipt load(String code) throws IOException {
        try {
            final URI link = URI.create(code.strip());
            final SufInvoice invoice = client.invoice(link);
            final List<SufItem> items = client.specification(link, invoice.invoiceResult().invoiceNumber());
            log.info("Receipt {}: {} items", invoice.invoiceResult().invoiceNumber(), items.size());
            return receipt(invoice, items);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while loading the receipt");
        } catch (RuntimeException e) {
            // an unexpected answer of the service
            throw new IOException("Receipt is not loaded: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isSupported(String code) {
        return client.isVerificationLink(code);
    }

    static Receipt receipt(SufInvoice invoice, List<SufItem> specification) {
        final List<Receipt.Item> items = specification.stream()
                .map(item -> new Receipt.Item(item.name(), money(item.unitPrice()), item.quantity(), money(item.total())))
                .toList();
        final BigDecimal total = money(invoice.invoiceResult().totalAmount());
        final BigDecimal sum = items.stream()
                .map(Receipt.Item::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final List<String> errors = items.isEmpty()
                ? List.of("the receipt has no items")
                : sum.compareTo(total) == 0 ? List.of() : List.of("items sum up to " + sum + ", the total is " + total);
        return new Receipt(
                errors.isEmpty(),
                invoice.invoiceRequest().businessName(),
                // printed to the second
                invoice.invoiceResult().sdcTime().atZone(BELGRADE).toLocalDateTime().truncatedTo(ChronoUnit.SECONDS),
                items,
                total,
                Currency.RSD,
                invoice.journal(),
                errors
        );
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
