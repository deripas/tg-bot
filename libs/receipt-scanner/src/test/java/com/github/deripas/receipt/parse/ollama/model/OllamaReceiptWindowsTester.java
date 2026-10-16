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
import java.util.List;

/**
 * Reads a test receipt with the model and saves every frame next to it, in {@code frames/}: the image
 * the model was sent and the text it read from it, to look into what goes wrong at the joins of the frames.
 * The frames are put together again as {@link OllamaReceiptReader} does; a frame the model read in two parts
 * gets the text of both parts.
 */
public class OllamaReceiptWindowsTester {

    @SneakyThrows
    static void main(String[] args) {
        final String image = args.length > 0 ? args[0] : "3/PXL_20261011_095819059.jpg";
        final JsonMapper mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();
        final ReceiptScanner scanner = new DefaultReceiptScanner(new OpenCvReceiptDetector());
        final Dimension size = VisionModel.QWEN_2_5_VL.maxImageSize(new Dimension(4, 3));

        final OllamaReceipt text = new OllamaReceiptReader(scanner).read(TestImages.readData(image), Country.SERBIA);

        final ImageData receipt = scanner.scan(TestImages.readData(image));
        final List<ReceiptFrames.Frame> frames = ReceiptFrames.assemble(receipt, ReceiptStrips.cut(receipt), size);
        final Path directory = Files.createDirectories(TestImages.file(image).resolveSibling("frames"));
        for (int i = 0; i < frames.size(); i++) {
            final ReceiptFrames.Frame frame = frames.get(i);
            final List<OllamaReceipt.Window> windows = text.windows().stream()
                    .filter(window -> window.top() >= frame.top() && window.bottom() <= frame.bottom())
                    .toList();
            final String name = "%02d_%d-%d".formatted(i + 1, frame.top(), frame.bottom());
            Files.write(directory.resolve(name + ".jpg"), frame.image().transform().toJpeg());
            mapper.writeValue(directory.resolve(name + ".json").toFile(), windows);
            System.out.printf("%s: %d items%n", name, windows.stream().mapToInt(window -> window.items().size()).sum());
        }
    }
}
