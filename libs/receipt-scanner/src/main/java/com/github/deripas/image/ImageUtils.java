package com.github.deripas.image;

import lombok.experimental.UtilityClass;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@UtilityClass
public class ImageUtils {

    public static BufferedImage load(byte[] image) throws IOException {
        final BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
        if (decoded == null) {
            throw new IOException("Unsupported image format");
        }
        return decoded;
    }

    public static byte[] toJpeg(BufferedImage image) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // there is no JPEG writer for images with alpha, and write returns false instead of failing
        if (!ImageIO.write(image, "jpg", baos)) {
            throw new IOException("Failed to encode JPEG from image type " + image.getType());
        }
        return baos.toByteArray();
    }

}
