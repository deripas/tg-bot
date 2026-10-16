package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.TestImages;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.Receipt;
import com.github.deripas.receipt.parse.ollama.OllamaReceiptParser;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.opencv.OpenCvReceiptDetector;
import lombok.SneakyThrows;
import org.jspecify.annotations.NonNull;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads the test receipts with the model and saves the text next to them as {@code text.json}
 * for {@link OllamaReceiptParser} tests.
 */
public class OllamaReceiptReaderTester {

    @SneakyThrows
    static void main(String[] args) {
        final JsonMapper mapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        final OllamaReceiptReader reader = new OllamaReceiptReader(new DefaultReceiptScanner(new OpenCvReceiptDetector()));
        final OllamaReceiptParser parser = new OllamaReceiptParser(reader);
        final OllamaReceiptConverter parserFunction = new OllamaReceiptConverter();
        final List<String> images = args.length > 0
                ? List.of(args)
                : List.of("1/IMG_4177.jpg", "2/IMG_4189.jpg", "3/PXL_20261011_095819059.jpg");

        for (String image : images) {
            final long start = System.nanoTime();
            final OllamaReceipt text = reader.read(TestImages.readData(image), Country.SERBIA);
            final long seconds = (System.nanoTime() - start) / 1_000_000_000;
//            mapper.writeValue(TestImages.file(image).resolveSibling("text.json").toFile(), text);

            final Receipt receipt = parserFunction.apply(text, Country.SERBIA);
            System.out.printf("%s: %d s, %d frames, %d items, total %s, sum %s, date %s, store %s, errors %s%n",
                    image, seconds, text.windows().size(), receipt.items().size(), receipt.total(),
                    sum(receipt), receipt.dateTime(), receipt.store(), receipt.errors());
            final Path expected = TestImages.file(image).resolveSibling("expected.json");
            if (Files.exists(expected)) {
                final List<BigDecimal> totals = mapper.readValue(expected.toFile(),
                                new TypeReference<List<Receipt.Item>>() {
                                }).stream()
                        .map(item -> item.total().setScale(2, RoundingMode.HALF_UP))
                        .toList();
                final List<BigDecimal> read = receipt.items().stream()
                        .map(Receipt.Item::total)
                        .filter(Objects::nonNull)
                        .toList();
                System.out.printf("  expected %d items: missing %s, extra %s%n", totals.size(),
                        difference(totals, read), difference(read, totals));
            }
        }
    }

    /**
     * @return the amounts of {@code from} left after taking out those of {@code taken}, one for one
     */
    private static List<BigDecimal> difference(List<BigDecimal> from, List<BigDecimal> taken) {
        final List<BigDecimal> left = new ArrayList<>(from);
        taken.forEach(amount -> left.stream()
                .filter(value -> value.compareTo(amount) == 0)
                .findFirst()
                .ifPresent(left::remove));
        return left;
    }

    private static @NonNull BigDecimal sum(Receipt receipt) {
        return receipt.items().stream()
                .map(Receipt.Item::total)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
