package com.github.deripas.receipt.scan.ollama;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.ollama.OllamaClient;
import com.github.deripas.ollama.VisionModel;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.Point;
import java.awt.Polygon;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against a local Ollama with {@link VisionModel#QWEN_2_5_VL}; skipped if it is not available.
 */
@EnabledIf("ollamaAvailable")
class OllamaReceiptDetectorTest {

    /**
     * The model returns an axis-aligned box, so the corners of a tilted receipt deviate more than with OpenCV.
     */
    private static final double TOLERANCE = 0.05;

    private final OllamaReceiptDetector detector = new OllamaReceiptDetector();

    /**
     * Same reference corners on the images turned vertical as in the OpenCV detector test:
     * top-left, top-right, bottom-right, bottom-left.
     */
    @ParameterizedTest
    @CsvSource({
            "1/IMG_4177.jpg,             137, 0,  1002, 0,  1000, 1533,  195, 1538",
            "2/IMG_4189.jpg,            847, 2665,  2812, 2640,  2857, 14780,  1207, 14862",
            "3/PXL_20261011_095819059.jpg, 1185, 0,  1755, 0,  1845, 4031,  1165, 4031",
    })
    void testDetectFindsReceiptCorners(String path,
                                       int tlx, int tly, int trx, int try_, int brx, int bry, int blx, int bly) {
        final ImageData image = TestImages.readVertical(path);
        final Polygon expected = new Polygon(new int[]{tlx, trx, brx, blx}, new int[]{tly, try_, bry, bly}, 4);
        final double tolerance = TOLERANCE * Math.max(image.getWidth(), image.getHeight());

        final Polygon actual = detector.detect(image).orElseThrow();

        assertThat(actual.npoints).isEqualTo(4);
        for (int i = 0; i < 4; i++) {
            final double distance = Point.distance(
                    actual.xpoints[i], actual.ypoints[i], expected.xpoints[i], expected.ypoints[i]);
            assertThat(distance)
                    .as("corner %d: expected (%d, %d), actual (%d, %d)", i,
                            expected.xpoints[i], expected.ypoints[i], actual.xpoints[i], actual.ypoints[i])
                    .isLessThanOrEqualTo(tolerance);
        }
    }

    static boolean ollamaAvailable() {
        try (HttpClient http = HttpClient.newHttpClient()) {
            final HttpRequest request = HttpRequest.newBuilder(
                            URI.create(OllamaClient.DEFAULT_BASE_URL + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .build();
            final HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    && response.body().contains("\"" + VisionModel.QWEN_2_5_VL.name() + "\"");
        } catch (Exception e) {
            return false;
        }
    }
}
