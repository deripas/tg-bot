package com.github.deripas.receipt.parse.qr.rs.suf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Item of the specification of a fiscal receipt.
 *
 * @param gtin      barcode of the product, empty if none
 * @param name      name as printed, without the tax label
 * @param quantity  pieces or weight
 * @param unitPrice price of one piece or kilogram
 * @param total     line total
 * @param label     tax label, e.g. {@code Ђ}
 * @param labelRate tax rate in percent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SufItem(
        String gtin,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal total,
        String label,
        BigDecimal labelRate
) {
}
