package com.github.deripas.image;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Immutable image on the Java heap: 8 bits per channel, gray or BGR, rows without padding.
 * Unlike an OpenCV {@code Mat} it needs no release; OpenCV is used only inside {@link ImageTransform}.
 */
@ToString(exclude = "bytes")
public final class ImageData {

    @Getter
    private final ImageSize size;
    @Getter
    private final ImageDataType type;
    @Getter(AccessLevel.PROTECTED)
    private final byte[] bytes;

    /**
     * @param bytes taken over, not copied
     */
    ImageData(ImageSize size, ImageDataType type, byte[] bytes) {
        validate(size, type, bytes);
        this.size = size;
        this.bytes = bytes;
        this.type = type;
    }

    /**
     * Copies the pixels: a gray image stays gray, any other becomes BGR.
     */
    public static ImageData of(BufferedImage image) {
        final ImageSize size = new ImageSize(image.getWidth(), image.getHeight());
        final ImageDataType type = safeImageType(image);
        final BufferedImage copy = createImage(size, type);
        draw(image, copy);
        return new ImageData(size, type, bytes(copy));
    }

    public int getWidth() {
        return size.width();
    }

    public int getHeight() {
        return size.height();
    }

    /**
     * @return a copy of the pixels, {@code TYPE_BYTE_GRAY} or {@code TYPE_3BYTE_BGR}
     */
    public BufferedImage toImage() {
        final BufferedImage copy = createImage(size, type);
        System.arraycopy(bytes, 0, bytes(copy), 0, bytes.length);
        return copy;
    }

    /**
     * @return a copy of the pixels row by row without padding, one byte per pixel for a gray image
     * and three (BGR) otherwise
     */
    public byte[] pixels() {
        return bytes.clone();
    }

    public boolean isVertical() {
        return size.height() > size.width();
    }

    public boolean isGray() {
        return type == ImageDataType.TYPE_BYTE_GRAY;
    }

    public ImageTransform transform() {
        return new ImageTransform(this);
    }

    private static void draw(BufferedImage from, BufferedImage to) {
        if (from.getType() == to.getType()) {
            from.copyData(to.getRaster());
            return;
        }
        final Graphics2D graphics = to.createGraphics();
        graphics.drawImage(from, 0, 0, null);
        graphics.dispose();
    }

    private static ImageDataType safeImageType(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return ImageDataType.TYPE_BYTE_GRAY;
        }
        return ImageDataType.TYPE_3BYTE_BGR;
    }

    private static void validate(ImageSize size, ImageDataType type, byte[] bytes) {
        final int width = size.width();
        final int height = size.height();
        final double count = width * height * type.getChannels();
        if (bytes.length != count) {
            throw new IllegalArgumentException("Expected " + count + " bytes for "
                    + width + "x" + height + " " + type + ", got " + bytes.length);
        }
    }

    private static byte[] bytes(BufferedImage image) {
        return ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    }

    private static BufferedImage createImage(ImageSize size, ImageDataType type) {
        return new BufferedImage(size.width(), size.height(), imageType(type));
    }

    private static int imageType(ImageDataType type) {
        return switch (type) {
            case TYPE_BYTE_GRAY -> BufferedImage.TYPE_BYTE_GRAY;
            case TYPE_3BYTE_BGR -> BufferedImage.TYPE_3BYTE_BGR;
            default -> throw new IllegalArgumentException("Unsupported image type: " + type);
        };
    }
}
