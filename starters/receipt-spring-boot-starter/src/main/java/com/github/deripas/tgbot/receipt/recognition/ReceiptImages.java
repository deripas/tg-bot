package com.github.deripas.tgbot.receipt.recognition;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Подготовка фото для vision-модели: Ollama принимает только JPEG/PNG, большие фото только замедляют. */
final class ReceiptImages {

    private ReceiptImages() {
    }

    /** JPEG шириной не больше {@code maxWidth}; JPEG нужного размера возвращается без перекодирования. */
    static byte[] toJpeg(byte[] image, int maxWidth) throws IOException {
        final BufferedImage original = ImageIO.read(new ByteArrayInputStream(image));
        if (original == null) {
            throw new IOException("Формат изображения не поддерживается (HEIC?) - отправьте чек как фото, а не файлом");
        }
        if (original.getWidth() <= maxWidth && isJpeg(image)) {
            return image;
        }
        final int width = Math.min(original.getWidth(), maxWidth);
        final int height = (int) Math.round((double) original.getHeight() * width / original.getWidth());

        // TYPE_INT_RGB: у PNG бывает альфа-канал, а JPEG его не поддерживает
        final BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        final Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(resized, "jpg", output)) {
                throw new IOException("Не удалось сохранить JPEG");
            }
            return output.toByteArray();
        }
    }

    private static boolean isJpeg(byte[] image) {
        return image.length > 2 && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8;
    }
}
