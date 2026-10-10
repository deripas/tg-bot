package com.github.deripas.receipt.read;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Objects;

/**
 * Text read from a receipt as printed, before any parsing: numbers and dates are strings.
 * A long receipt is read in overlapping windows, top to bottom; the same item may be in two neighbouring windows.
 *
 * @param windows windows in receipt order
 */
public record ReceiptText(List<Window> windows) {

    /**
     * What was read from one window of the receipt scan.
     *
     * @param top     top of the window in pixels of the scan scaled to the window width
     * @param bottom  bottom of the window, exclusive
     * @param first   the window starts at the top of the receipt
     * @param last    the window ends at the bottom of the receipt
     * @param items   purchased items from top to bottom
     * @param summary header and totals
     */
    public record Window(int top, int bottom, boolean first, boolean last, List<Item> items, Summary summary) {

        public Window {
            items = items == null ? List.of() : List.copyOf(items);
            summary = summary == null ? Summary.EMPTY : summary;
        }
    }

    /**
     * Item as printed.
     *
     * @param name     item name line with unit, product code and tax label
     * @param price    unit price, e.g. "1.199,99"
     * @param quantity quantity or weight, e.g. "0,379"
     * @param total    line total
     */
    public record Item(
            @JsonPropertyDescription("Item name line exactly as printed, with unit, product code and tax label")
            String name,
            @JsonPropertyDescription("Unit price exactly as printed")
            String price,
            @JsonPropertyDescription("Quantity or weight exactly as printed")
            String quantity,
            @JsonPropertyDescription("Line total exactly as printed, the rightmost number of the item")
            String total
    ) {
    }

    /**
     * Header and totals as printed; a field is null if it is not in the window.
     *
     * @param store       seller or store name
     * @param dateTime    date and time of the purchase
     * @param totalsLines lines after the last item (totals, payment, taxes) with their labels and amounts
     */
    public record Summary(
            @JsonPropertyDescription("Seller or store name from the receipt header; null if not in this photo")
            String store,
            @JsonPropertyDescription("Date and time of the purchase exactly as printed; null if not in this photo")
            String dateTime,
            @JsonPropertyDescription("Every line after the last purchased item (totals, payment, taxes), "
                    + "each with its label and amount exactly as printed, top to bottom; empty if none in this photo")
            List<String> totalsLines
    ) {

        public static final Summary EMPTY = new Summary(null, null, List.of());

        public Summary {
            totalsLines = totalsLines == null ? List.of() : totalsLines.stream().filter(Objects::nonNull).toList();
        }
    }
}
