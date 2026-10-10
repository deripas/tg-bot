package com.github.deripas;

import com.github.deripas.image.ImageData;
import com.github.deripas.image.ImageUtils;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@UtilityClass
public class TestImages {

    private static final Path TEST_RECEIPTS = findUp(Paths.get("test", "receipts"));

    /**
     * Decoding is slow (a large HEIC takes seconds), and the same images are read by many tests.
     */
    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ImageData> DATA_CACHE = new ConcurrentHashMap<>();

    /**
     * @return decoded image shared between tests; do not modify it
     */
    public static BufferedImage read(String image) {
        final String key = image.replaceFirst("^/", "");
        return CACHE.computeIfAbsent(key, TestImages::load);
    }

    /**
     * @return decoded image shared between tests
     */
    public static ImageData readData(String image) {
        final String key = image.replaceFirst("^/", "");
        return DATA_CACHE.computeIfAbsent(key, _ -> ImageData.of(read(image)));
    }

    /**
     * @return the image turned vertical as the scanner does before detection; shared between tests
     */
    public static ImageData readVertical(String image) {
        final String key = image.replaceFirst("^/", "") + "#vertical";
        return DATA_CACHE.computeIfAbsent(key, _ -> readData(image).transform().vertical().asImage());
    }

    /**
     * @return file of the test receipts, e.g. {@code "3/expected.json"}
     */
    public static Path file(String path) {
        return TEST_RECEIPTS.resolve(path.replaceFirst("^/", ""));
    }

    @SneakyThrows
    private static BufferedImage load(String image) {
        return ImageUtils.load(read(TEST_RECEIPTS.resolve(image)));
    }

    @SneakyThrows
    static byte[] read(Path path) {
        try (final InputStream stream = new FileInputStream(path.toFile())) {
            return Objects.requireNonNull(stream).readAllBytes();
        }
    }

    /**
     * Walks up from the working directory (the module dir under Maven and IDE) to the repository root.
     */
    private static Path findUp(Path relative) {
        final Path currentDirectory = Paths.get("");
        for (Path dir = currentDirectory.toAbsolutePath(); dir != null; dir = dir.getParent()) {
            final Path candidate = dir.resolve(relative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(relative + " not found from " + currentDirectory.toAbsolutePath());
    }
}
