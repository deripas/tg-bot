package com.github.deripas.receipt.parse.qr.rs.suf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Fiscal receipt as the verification service returns it for the link of its QR code.
 *
 * @param invoiceRequest seller
 * @param invoiceResult  result of the fiscalization: total, number and time
 * @param journal        text of the receipt as printed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SufInvoice(
        InvoiceRequest invoiceRequest,
        InvoiceResult invoiceResult,
        String journal
) {

    /**
     * @param taxId        tax id of the seller
     * @param businessName name of the seller
     * @param locationName name of the shop
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvoiceRequest(
            String taxId,
            String businessName,
            String locationName
    ) {
    }

    /**
     * @param totalAmount   total amount paid
     * @param invoiceNumber number of the receipt, e.g. {@code X89FX3FD-C38FDVO0-32340}
     * @param sdcTime       time of the fiscalization, printed on the receipt as the PFR time
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InvoiceResult(
            BigDecimal totalAmount,
            String invoiceNumber,
            Instant sdcTime
    ) {
    }
}
