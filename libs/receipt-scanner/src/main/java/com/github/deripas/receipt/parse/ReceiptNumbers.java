package com.github.deripas.receipt.parse;

import java.util.Collection;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses numbers copied from a receipt as printed, e.g. {@code "1.199,99"} or {@code "0,379"}.
 * The model is asked to copy numbers as text because it mixes up thousands and decimal separators.
 */
final class ReceiptNumbers {

    private static final Pattern NUMBER = Pattern.compile("-?\\d[\\d.,]*");
    /** Amounts are printed with two decimals, so {@code ",99"} at the end tells the decimal separator. */
    private static final Pattern AMOUNT_END = Pattern.compile("\\d([.,])\\d{2}$");

    private ReceiptNumbers() {
    }

    /**
     * Detects the decimal separator of a receipt from the amounts printed on it.
     *
     * @param fallback separator returned if it cannot be detected
     * @return {@code ','} or {@code '.'}
     */
    static char decimalSeparator(Collection<String> printed, char fallback) {
        int commas = 0;
        int dots = 0;
        for (String text : printed) {
            if (text == null) {
                continue;
            }
            final Matcher matcher = AMOUNT_END.matcher(text.strip());
            if (matcher.find()) {
                if (matcher.group(1).equals(",")) {
                    commas++;
                } else {
                    dots++;
                }
            }
        }
        if (commas == dots) {
            return fallback;
        }
        return commas > dots ? ',' : '.';
    }

    /**
     * @param printed          number as printed, may contain thousands separators and spaces
     * @param decimalSeparator decimal separator of the receipt, see {@link #decimalSeparator(Collection, char)}
     * @return parsed number, or empty if {@code printed} is blank or not a number
     */
    static Optional<Double> parse(String printed, char decimalSeparator) {
        if (printed == null || printed.isBlank()) {
            return Optional.empty();
        }
        final String compact = printed.strip().replaceAll("\\s", "");
        if (!compact.matches("-?\\d[\\d.,]*")) {
            return Optional.empty();
        }
        final char thousandsSeparator = decimalSeparator == ',' ? '.' : ',';
        final String normalized = compact
                .replace(String.valueOf(thousandsSeparator), "")
                .replace(decimalSeparator, '.');
        try {
            return Optional.of(Double.parseDouble(normalized));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Takes the last number from a printed line such as {@code "Укупан износ: 11.799,26"}.
     *
     * @return parsed number, or empty if the line has no number
     */
    static Optional<Double> lastNumber(String line, char decimalSeparator) {
        if (line == null) {
            return Optional.empty();
        }
        final Matcher matcher = NUMBER.matcher(line);
        String last = null;
        while (matcher.find()) {
            last = matcher.group();
        }
        return parse(last, decimalSeparator);
    }
}
