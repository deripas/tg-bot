package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.TestImages;
import com.github.deripas.image.ImageData;
import com.github.deripas.receipt.parse.ollama.model.ReceiptStrips.Strip;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.ReceiptScanner;
import com.github.deripas.receipt.scan.boofcv.BoofCvReceiptDetector;
import lombok.SneakyThrows;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Cuts the test receipts into strips. Draws the cuts on the scan in {@code target/strips-N.png}: green - between
 * lines, blue - between items; the straightened strips one under another in {@code target/strips-N-straight.png};
 * and the frames for the model in {@code target/frames-N-K.png}.
 */
public class ReceiptStripsTester {

    /**
     * 4:3, the largest image of qwen2.5vl:7b of these proportions.
     */
    private static final Dimension FRAME = new Dimension(1148, 868);

    @SneakyThrows
    static void main() {
        final ReceiptScanner scanner = new DefaultReceiptScanner(new BoofCvReceiptDetector());
        for (String path : new String[]{"1/IMG_4177.jpg", "2/IMG_4189.jpg", "3/PXL_20261011_095819059.jpg"}) {
            final ImageData scan = scanner.scan(TestImages.readData(path));
            final long start = System.nanoTime();
            final List<Strip> strips = ReceiptStrips.cut(scan);
            final long millis = (System.nanoTime() - start) / 1_000_000;
            System.out.printf("%s %dx%d: %d strips, %d item ends, %d ms%n", path, scan.getWidth(), scan.getHeight(),
                    strips.size(), strips.stream().filter(Strip::itemEnd).count(), millis);

            final String name = "target/strips-" + path.substring(0, path.indexOf('/'));
            ImageIO.write(cuts(scan, strips), "png", new File(name + ".png"));
            ImageIO.write(straight(scan, strips), "png", new File(name + "-straight.png"));

            final List<ReceiptFrames.Frame> frames = ReceiptFrames.assemble(scan, strips, FRAME);
            for (int i = 0; i < frames.size(); i++) {
                final ReceiptFrames.Frame frame = frames.get(i);
                System.out.printf("  frame %d: rows [%d, %d), %dx%d%n", i + 1, frame.top(), frame.bottom(),
                        frame.image().getWidth(), frame.image().getHeight());
                ImageIO.write(frame.image().toImage(), "png",
                        new File(name.replace("strips", "frames") + "-" + (i + 1) + ".png"));
            }
        }
    }

    private static BufferedImage cuts(ImageData scan, List<Strip> strips) {
        final BufferedImage canvas = new BufferedImage(scan.getWidth(), scan.getHeight(), BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = canvas.createGraphics();
        graphics.drawImage(scan.toImage(), 0, 0, null);
        for (Strip strip : strips.subList(0, strips.size() - 1)) {
            graphics.setColor(strip.itemEnd() ? Color.BLUE : new Color(0, 180, 0));
            graphics.setStroke(new BasicStroke(strip.itemEnd() ? 3 : 1));
            final int[] cut = strip.bottom();
            for (int x = 1; x < cut.length; x++) {
                graphics.drawLine(x - 1, cut[x - 1], x, cut[x]);
            }
        }
        graphics.dispose();
        return canvas;
    }

    /**
     * Straightened strips one under another, a blue line under those ending an item.
     */
    private static BufferedImage straight(ImageData scan, List<Strip> strips) {
        final int height = strips.stream().mapToInt(Strip::height).sum();
        final BufferedImage canvas = new BufferedImage(scan.getWidth(), height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = canvas.createGraphics();
        int y = 0;
        for (Strip strip : strips) {
            graphics.drawImage(strip.straighten(scan).toImage(), 0, y, null);
            y += strip.height();
            if (strip.itemEnd()) {
                graphics.setColor(Color.BLUE);
                graphics.drawLine(0, y - 1, scan.getWidth(), y - 1);
            }
        }
        graphics.dispose();
        return canvas;
    }
}
