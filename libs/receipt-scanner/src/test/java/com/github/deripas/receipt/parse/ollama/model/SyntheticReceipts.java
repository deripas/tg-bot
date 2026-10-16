package com.github.deripas.receipt.parse.ollama.model;

import com.github.deripas.image.ImageData;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Receipts drawn as the Serbian ones are printed: the name of an item from the left margin, its numbers indented
 * below, 70 px per item, 600 px wide.
 */
final class SyntheticReceipts {

    static final int WIDTH = 600;
    static final int ITEM_HEIGHT = 70;

    private SyntheticReceipts() {
    }

    /**
     * @param angle the receipt turned by this angle, in radians
     */
    static ImageData items(int count, double angle) {
        final BufferedImage image = new BufferedImage(WIDTH, 120 + count * ITEM_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        final Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.rotate(angle, image.getWidth() / 2.0, image.getHeight() / 2.0);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        for (int i = 0; i < count; i++) {
            final int y = 80 + i * ITEM_HEIGHT;
            graphics.drawString("Item number " + i + "/KOM/0082931 (E)", 30, y);
            graphics.drawString("149,99", 110, y + 28);
            graphics.drawString("1", 330, y + 28);
            graphics.drawString("149,99", 470, y + 28);
        }
        graphics.dispose();
        return ImageData.of(image);
    }
}
