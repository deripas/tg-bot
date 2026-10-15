package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.ollama.VisionModel;
import com.github.deripas.receipt.Country;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import com.github.deripas.receipt.scan.opencv.OpenCvReceiptDetector;
import lombok.SneakyThrows;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a test receipt with the model and saves every window next to it, in {@code windows/}: the image
 * the model was sent and the text it read from it, to look into what goes wrong at the joins of the windows.
 * The image is cut from the scan by the coordinates of the window, as {@link OllamaReceiptReader} does.
 */
public class OllamaReceiptWindowsTester {

    @SneakyThrows
    static void main(String[] args) {
        final String image = args.length > 0 ? args[0] : "3/PXL_20261011_095819059.jpg";
        final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        final ReceiptScanner scanner = new DefaultReceiptScanner(new OpenCvReceiptDetector());
        final Dimension frame = VisionModel.QWEN_2_5_VL.maxImageSize(new Dimension(4, 5));

        final OllamaReceipt text = new OllamaReceiptReader(scanner).read(TestImages.readData(image), Country.SERBIA);

        final ImageData receipt = scanner.scan(TestImages.readData(image));
        final ImageData scaled = receipt.transform().scale((double) frame.width / receipt.getWidth()).asImage();
        final Path windows = Files.createDirectories(TestImages.file(image).resolveSibling("windows"));
        for (int i = 0; i < text.windows().size(); i++) {
            final OllamaReceipt.Window window = text.windows().get(i);
            final String name = "%02d_%d-%d".formatted(i + 1, window.top(), window.bottom());
            final int height = window.bottom() - window.top();
            Files.write(windows.resolve(name + ".jpg"), scaled.transform()
                    .cropRows(window.top(), window.bottom())
                    .scale(Math.min(1, (double) frame.height / height))
                    .toJpeg());
            mapper.writeValue(windows.resolve(name + ".json").toFile(), window);
            System.out.printf("%s: %d items%n", name, window.items().size());
        }
    }
}
