package com.github.deripas.receipt.parse;


import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Merges item lists read from overlapping windows of one long receipt, top to bottom.
 * <p>
 * Consecutive windows overlap: the last items of one window are the first items of the next one.
 * The overlap is found by aligning the tail of the merged list with the head of the next part,
 * allowing a line missed or misread in one of the windows. Only the overlap is removed, so items
 * legitimately printed several times are kept.
 * <p>
 * An overlap inside a run of identical items is ambiguous (did the windows share two or three of them?).
 * If the printed total is known, the overlap lengths that make the items sum up to it win.
 * <p>
 * The model sometimes lists the items of a window from bottom to top. A part that overlaps
 * the neighbouring one much better when reversed is reversed.
 * <p>
 * An item inside an overlap seen in only one of the two windows is doubtful: it may be missed by one window
 * or invented by the model in the other. Doubtful items are kept, unless removing some of them
 * makes the items sum up to the printed total.
 */
final class ReceiptMerger {

    static final double NAME_SIMILARITY = 0.75;
    private static final double SAME_NAME_SIMILARITY = 0.9;
    private static final double AMOUNT_TOLERANCE = 0.015;
    /** Shorter names are too generic to match by prefix: "salata" fits many items. */
    private static final int MIN_PREFIX = 10;

    private static final double MATCH = 2;
    private static final double WEAK_MATCH = 0.5;
    private static final double MISMATCH = -3;
    private static final double GAP = -2;

    /** Overlap lengths tried around the best one when the sum does not match the total. */
    private static final int ALTERNATIVES = 2;
    /** Merge candidates kept after each window; enough for the alternatives checked against the total. */
    private static final int BEAM = 32;
    /** A reversed part must overlap clearly better than the part as read. */
    private static final double REVERSED = -2 * MATCH;
    /** More doubtful items are too many combinations to try against the total. */
    private static final int MAX_DOUBTFUL = 16;

    /**
     * @param items    merged items
     * @param warnings problems a human should check
     */
    record Result(List<ParsedItem> items, List<String> warnings) {
    }

    private ReceiptMerger() {
    }

    /**
     * @param parts items of each window, windows in receipt order
     * @param total printed total, or {@code null} if unknown
     */
    static Result merge(List<List<ParsedItem>> parts, Double total) {
        if (parts.isEmpty()) {
            return new Result(List.of(), List.of());
        }
        final List<Entry> first = parts.getFirst().stream().map(Entry::certain).toList();
        List<Candidate> candidates = new ArrayList<>(List.of(new Candidate(first, 0, List.of())));
        if (parts.size() > 1) {
            candidates.add(new Candidate(first.reversed(), REVERSED, List.of(reversed(1))));
        }
        for (int next = 1; next < parts.size(); next++) {
            candidates = extend(candidates, parts.get(next).stream().map(Entry::certain).toList(), next + 1);
        }
        // sorted by score, the best first
        final Candidate best = candidates.getFirst();
        if (total == null || sumsUp(best.items(), total)) {
            return best.result();
        }
        for (Candidate candidate : candidates) {
            if (sumsUp(candidate.items(), total)) {
                return candidate.result("overlap of windows chosen by the total; check items at window boundaries");
            }
        }
        for (Candidate candidate : candidates) {
            final List<Integer> removed = removalToTotal(candidate.entries(), total);
            if (removed != null) {
                final List<Entry> kept = new ArrayList<>(candidate.entries());
                final List<String> names = new ArrayList<>();
                for (int index : removed.reversed()) {
                    names.add(kept.remove(index).item().name());
                }
                return new Candidate(kept, candidate.score(), candidate.notes()).result(
                        "removed to match the total, seen in only one of overlapping windows: "
                                + String.join(", ", names.reversed()));
            }
        }
        return best.result();
    }

    /**
     * Finds the fewest doubtful entries whose removal makes the items sum up to the total.
     *
     * @return indices of the entries to remove in ascending order, or {@code null} if there are none
     */
    private static List<Integer> removalToTotal(List<Entry> entries, double total) {
        final List<Integer> doubtful = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).doubtful() && entries.get(i).item().total() != null) {
                doubtful.add(i);
            }
        }
        final double excess = sum(entries.stream().map(Entry::item).toList()) - total;
        if (doubtful.isEmpty() || doubtful.size() > MAX_DOUBTFUL || excess <= 0) {
            return null;
        }
        List<Integer> best = null;
        for (int mask = 1; mask < 1 << doubtful.size(); mask++) {
            if (best != null && Integer.bitCount(mask) >= best.size()) {
                continue;
            }
            double removed = 0;
            final List<Integer> indices = new ArrayList<>();
            for (int bit = 0; bit < doubtful.size(); bit++) {
                if ((mask & 1 << bit) != 0) {
                    indices.add(doubtful.get(bit));
                    removed += entries.get(doubtful.get(bit)).item().total();
                }
            }
            if (Math.abs(removed - excess) <= AMOUNT_TOLERANCE) {
                best = indices;
            }
        }
        return best;
    }

    static double sum(List<ParsedItem> items) {
        return items.stream()
                .map(ParsedItem::total)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    /**
     * @return {@code true} if price × quantity matches the line total
     */
    static boolean isConsistent(ParsedItem item) {
        return item.price() != null && item.quantity() != null && item.total() != null
                && Math.abs(item.price() * item.quantity() - item.total()) <= AMOUNT_TOLERANCE;
    }

    private static boolean sumsUp(List<ParsedItem> items, double total) {
        return Math.abs(sum(items) - total) <= AMOUNT_TOLERANCE;
    }

    /**
     * @param doubtful the item is inside an overlap but seen in only one of the windows
     */
    private record Entry(ParsedItem item, boolean doubtful) {

        static Entry certain(ParsedItem item) {
            return new Entry(item, false);
        }
    }

    /**
     * Merged items so far.
     *
     * @param score total alignment score of the overlaps
     * @param notes warnings about how the parts were merged
     */
    private record Candidate(List<Entry> entries, double score, List<String> notes) {

        List<ParsedItem> items() {
            return entries.stream().map(Entry::item).toList();
        }

        Result result(String... more) {
            final List<String> warnings = new ArrayList<>(notes);
            warnings.addAll(List.of(more));
            return new Result(items(), List.copyOf(warnings));
        }
    }

    /**
     * Merges the next part into every candidate, as read and reversed, with several overlap lengths;
     * keeps the {@link #BEAM} best results.
     */
    private static List<Candidate> extend(List<Candidate> candidates, List<Entry> part, int window) {
        final List<Candidate> extended = new ArrayList<>();
        for (Candidate merged : candidates) {
            for (Candidate overlap : overlaps(merged.entries(), part)) {
                extended.add(new Candidate(overlap.entries(), merged.score() + overlap.score(), merged.notes()));
            }
            for (Candidate overlap : overlaps(merged.entries(), part.reversed())) {
                extended.add(new Candidate(overlap.entries(), merged.score() + overlap.score() + REVERSED,
                        concat(merged.notes(), reversed(window))));
            }
        }
        return extended.stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .limit(BEAM)
                .toList();
    }

    private static String reversed(int window) {
        return "items of window " + window + " were read bottom to top and reversed";
    }

    private static List<String> concat(List<String> notes, String note) {
        final List<String> all = new ArrayList<>(notes);
        all.add(note);
        return List.copyOf(all);
    }

    /**
     * Semi-global alignment: any suffix of {@code a} against a prefix of {@code b}.
     *
     * @return merges for the best overlap length and a few lengths around it, including no overlap
     */
    private static List<Candidate> overlaps(List<Entry> a, List<Entry> b) {
        final int n = a.size();
        final int m = b.size();
        final double[][] dp = new double[n + 1][m + 1];
        for (int j = 1; j <= m; j++) {
            dp[0][j] = j * GAP;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                dp[i][j] = Math.max(dp[i - 1][j - 1] + score(a.get(i - 1).item(), b.get(j - 1).item()),
                        Math.max(dp[i - 1][j] + GAP, dp[i][j - 1] + GAP));
            }
        }
        int bestLength = 0;
        for (int j = 1; j <= m; j++) {
            if (dp[n][j] > dp[n][bestLength]) {
                bestLength = j;
            }
        }
        final List<Candidate> candidates = new ArrayList<>();
        for (int j = Math.max(0, bestLength - ALTERNATIVES); j <= Math.min(m, bestLength + ALTERNATIVES); j++) {
            candidates.add(new Candidate(join(a, b, dp, j), dp[n][j], List.of()));
        }
        if (bestLength > ALTERNATIVES) {
            candidates.add(new Candidate(join(a, b, dp, 0), 0, List.of()));
        }
        return candidates;
    }

    /**
     * Traces the alignment back from the end of {@code a} and {@code b[0..overlap)}
     * and joins: head of {@code a}, aligned region, rest of {@code b}.
     * Items of the region seen in only one window are marked doubtful.
     */
    private static List<Entry> join(List<Entry> a, List<Entry> b, double[][] dp, int overlap) {
        final List<Entry> region = new ArrayList<>();
        int i = a.size();
        int j = overlap;
        while (j > 0) {
            final ParsedItem itemB = b.get(j - 1).item();
            if (i > 0 && dp[i][j] == dp[i - 1][j - 1] + score(a.get(i - 1).item(), itemB)) {
                region.add(Entry.certain(better(a.get(i - 1).item(), itemB)));
                i--;
                j--;
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + GAP) {
                region.add(new Entry(a.get(i - 1).item(), true));
                i--;
            } else {
                region.add(new Entry(itemB, true));
                j--;
            }
        }
        final List<Entry> joined = new ArrayList<>(a.subList(0, i));
        joined.addAll(region.reversed());
        joined.addAll(b.subList(overlap, b.size()));
        return joined;
    }

    private static double score(ParsedItem a, ParsedItem b) {
        final double names = nameSimilarity(a.name(), b.name());
        final boolean totalsKnown = a.total() != null && b.total() != null;
        final boolean sameTotal = totalsKnown && Math.abs(a.total() - b.total()) <= AMOUNT_TOLERANCE;
        if (names >= NAME_SIMILARITY && (sameTotal || !totalsKnown)) {
            return MATCH;
        }
        if (names >= SAME_NAME_SIMILARITY || sameTotal && names >= 0.5) {
            // one of the copies is misread
            return WEAK_MATCH;
        }
        return MISMATCH;
    }

    /**
     * Of two copies of the same line, the one whose numbers agree with each other; otherwise the copy from
     * the upper window: at the top edge of a window the model tends to attach numbers to the wrong name.
     */
    private static ParsedItem better(ParsedItem upper, ParsedItem lower) {
        return isConsistent(lower) && !isConsistent(upper) ? lower : upper;
    }

    /**
     * Similarity of full names and of names without the product code: one window may have the code
     * or the end of the name cut off.
     */
    static double nameSimilarity(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        return Math.max(
                similarity(normalize(a), normalize(b)),
                prefixSimilarity(normalize(withoutCode(a)), normalize(withoutCode(b)))
        );
    }

    /** Compares the shorter name with the beginning of the longer one, if the shorter name is long enough. */
    private static double prefixSimilarity(String a, String b) {
        final String shorter = a.length() <= b.length() ? a : b;
        final String longer = a.length() <= b.length() ? b : a;
        final double whole = similarity(shorter, longer);
        if (shorter.length() < MIN_PREFIX) {
            return whole;
        }
        return Math.max(whole, similarity(shorter, longer.substring(0, shorter.length())));
    }

    /** {@code "Kefir 2,8%, 1kg/KOM/0209361 (E)"} -> {@code "Kefir 2,8%, 1kg"} */
    private static String withoutCode(String name) {
        final int slash = name.indexOf('/');
        return slash < 0 ? name : name.substring(0, slash);
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip();
    }

    /** 1 - Levenshtein distance / length of the longer string. */
    private static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                final int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            final int[] swap = previous;
            previous = current;
            current = swap;
        }
        return 1 - (double) previous[b.length()] / Math.max(a.length(), b.length());
    }
}
