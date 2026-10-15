package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.receipt.Country;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Builds the receipt from what was read from each window: parses printed numbers,
 * fills a missing number of an item from the other two, merges overlapping windows
 * and checks that the items sum up to the printed total.
 */
@Slf4j
final class ReceiptAssembler {

    static final double AMOUNT_TOLERANCE = 0.015;

    private ReceiptAssembler() {
    }

    /**
     * @param dateTime      date of the purchase, or {@code null} if not found
     * @param items         merged items
     * @param total         printed total, or {@code null} if not found
     * @param paymentMethod payment line without the amount
     * @param taxTotal      total tax amount
     * @param warnings      problems a human should check
     */
    record Assembly(
            String store,
            LocalDateTime dateTime,
            List<ParsedItem> items,
            Double total,
            String paymentMethod,
            Double taxTotal,
            List<String> warnings
    ) {

        /**
         * @return difference between the items sum and the printed total; {@code null} if the total is unknown
         */
        Double imbalance() {
            return total == null
                    ? null
                    : Math.abs(ReceiptMerger.sum(items) - total);
        }

        boolean balanced() {
            final Double imbalance = imbalance();
            return imbalance != null && imbalance <= AMOUNT_TOLERANCE;
        }

        /**
         * Imbalance relative to the total: a total the model invented for a window without one is usually small
         * (an item amount), so it is far from the items sum relative to itself even when the absolute
         * difference is small.
         */
        double relativeImbalance() {
            return total == 0
                    ? Double.MAX_VALUE
                    : imbalance() / Math.abs(total);
        }
    }

    /**
     * Assembles the receipt from the windows up to the one whose total matches the items above it.
     * A receipt may go on after the total (card slip, discounts repeating items), so windows after it are dropped.
     * The model may also invent a total for a window without one; such a total does not match the items.
     * If no total matches exactly, the window with the total closest to the items sum relative to the total is used.
     *
     * @param windows windows in receipt order
     */
    static Assembly assembleWindows(List<OllamaReceipt.Window> windows, Country country) {
        if (windows.isEmpty()) {
            return assemble(List.of(), OllamaReceipt.Summary.EMPTY, OllamaReceipt.Summary.EMPTY, country);
        }
        final OllamaReceipt.Summary header = windows.getFirst().summary();
        Assembly best = null;
        int bestUsed = 0;
        for (int used = 1; used <= windows.size(); used++) {
            final OllamaReceipt.Window window = windows.get(used - 1);
            if (window.summary().totalsLines().isEmpty()) {
                continue;
            }
            final Assembly assembly = assemble(windows.subList(0, used), header, window.summary(), country);
            if (assembly.imbalance() == null) {
                continue;
            }
            log.info("Window {} of {}: total {}, items sum differs by {}", used, windows.size(),
                    assembly.total(), amount(assembly.imbalance()));
            if (best == null || assembly.relativeImbalance() < best.relativeImbalance()) {
                best = assembly;
                bestUsed = used;
            }
            if (assembly.balanced()) {
                break;
            }
        }
        if (best == null) {
            best = assemble(windows, header, windows.getLast().summary(), country);
            bestUsed = windows.size();
        }
        return withStamp(best, windows, bestUsed - 1);
    }

    /**
     * Takes the date of the receipt from the first window, starting from the one with the total,
     * where it is a real date: it is printed after the totals, maybe in the next window.
     * The model fills the field for any window, copying an amount or inventing them; windows above
     * the total are checked last.
     *
     * @param footer index of the window with the total
     */
    private static Assembly withStamp(Assembly assembly, List<OllamaReceipt.Window> windows, int footer) {
        final List<OllamaReceipt.Window> order = new ArrayList<>(windows.subList(footer, windows.size()));
        order.addAll(windows.subList(0, footer).reversed());
        for (OllamaReceipt.Window window : order) {
            final Optional<LocalDateTime> date = ReceiptDates.parse(window.summary().dateTime());
            if (date.isPresent()) {
                return new Assembly(assembly.store(), date.get(),
                        assembly.items(), assembly.total(), assembly.paymentMethod(), assembly.taxTotal(),
                        assembly.warnings());
            }
        }
        return assembly;
    }

    /**
     * @param windows windows in receipt order
     * @param header  header read from the first window
     * @param footer  totals read from the last window
     */
    static Assembly assemble(List<OllamaReceipt.Window> windows, OllamaReceipt.Summary header,
                             OllamaReceipt.Summary footer, Country country) {
        final char separator = ReceiptNumbers.decimalSeparator(Stream.concat(
                windows.stream().flatMap(window -> window.items().stream())
                        .flatMap(item -> Stream.of(item.price(), item.quantity(), item.total())),
                footer.totalsLines().stream()).toList(), country.getDecimalSeparator());

        final List<List<ParsedItem>> parts = windows.stream()
                .map(window -> window.items().stream()
                        .filter(item -> item.name() != null && !item.name().isBlank())
                        .map(item -> toItem(item, separator))
                        .toList())
                .toList();
        final ReceiptTotals totals = ReceiptTotals.parse(footer.totalsLines(), country.getKeywords(), separator);
        final Double total = totals.total();
        final ReceiptMerger.Result merged = ReceiptMerger.merge(parts, total);

        final List<String> warnings = new ArrayList<>(merged.warnings());
        merged.items().stream()
                .filter(item -> !ReceiptMerger.isConsistent(item))
                .forEach(item -> warnings.add("price × quantity ≠ total: " + item.name()));
        final double sum = ReceiptMerger.sum(merged.items());
        if (total == null) {
            warnings.add("total not found, items sum up to " + amount(sum));
        } else if (Math.abs(sum - total) > AMOUNT_TOLERANCE) {
            warnings.add("items sum up to " + amount(sum) + ", printed total is " + amount(total));
        }
        return new Assembly(
                first(OllamaReceipt.Summary::store, header, footer),
                ReceiptDates.parse(first(OllamaReceipt.Summary::dateTime, footer, header)).orElse(null),
                merged.items(),
                total,
                totals.paymentMethod(),
                totals.taxTotal(),
                List.copyOf(warnings));
    }

    /**
     * Parses printed numbers; a missing or unreadable number is computed from the other two.
     * The line total is trusted most: it is what sums up to the receipt total.
     */
    static ParsedItem toItem(OllamaReceipt.Item printed, char separator) {
        Double price = ReceiptNumbers.parse(printed.price(), separator).orElse(null);
        Double quantity = ReceiptNumbers.parse(printed.quantity(), separator).orElse(null);
        Double total = ReceiptNumbers.parse(printed.total(), separator).orElse(null);
        if (total == null && price != null && quantity != null) {
            total = round(price * quantity, 2);
        } else if (quantity == null && price != null && total != null && price != 0) {
            quantity = round(total / price, 3);
        } else if (price == null && quantity != null && total != null && quantity != 0) {
            price = round(total / quantity, 2);
        }
        return new ParsedItem(printed.name().strip(), price, quantity, total);
    }

    private static String first(Function<OllamaReceipt.Summary, String> field, OllamaReceipt.Summary... summaries) {
        return Stream.of(summaries)
                .map(field)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static double round(double value, int scale) {
        final double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private static String amount(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
