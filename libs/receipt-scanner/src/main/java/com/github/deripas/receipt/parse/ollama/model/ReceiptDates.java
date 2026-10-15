package com.github.deripas.receipt.parse.ollama.model;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Date of the purchase from a line as the model copied it: "25.09.2026. 18:53:38", "2026-09-25 18:53", "25/09/2026". */
final class ReceiptDates {

    private static final Pattern DAY_FIRST = Pattern.compile("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4})");
    private static final Pattern YEAR_FIRST = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?");

    private ReceiptDates() {
    }

    static Optional<LocalDateTime> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return date(text).map(date -> date.atTime(time(text)));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> date(String text) {
        final Matcher yearFirst = YEAR_FIRST.matcher(text);
        if (yearFirst.find()) {
            return Optional.of(LocalDate.of(number(yearFirst, 1), number(yearFirst, 2), number(yearFirst, 3)));
        }
        final Matcher dayFirst = DAY_FIRST.matcher(text);
        if (dayFirst.find()) {
            return Optional.of(LocalDate.of(number(dayFirst, 3), number(dayFirst, 2), number(dayFirst, 1)));
        }
        return Optional.empty();
    }

    private static LocalTime time(String text) {
        final Matcher time = TIME.matcher(text);
        if (!time.find()) {
            return LocalTime.MIDNIGHT;
        }
        final int seconds = time.group(3) == null ? 0 : number(time, 3);
        return LocalTime.of(number(time, 1), number(time, 2), seconds);
    }

    private static int number(Matcher matcher, int group) {
        return Integer.parseInt(matcher.group(group));
    }
}
