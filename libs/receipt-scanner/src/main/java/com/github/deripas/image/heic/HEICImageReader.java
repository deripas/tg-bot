package com.github.deripas.image.heic;

import com.github.deripas.image.heic.io.ImageInputIOStream;
import openize.heic.decoder.HeicImage;
import openize.heic.decoder.HeicImageFrame;
import openize.heic.decoder.PixelFormat;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class HEICImageReader extends ImageReader {

    public static final int IMAGE_TYPE = BufferedImage.TYPE_INT_RGB;
    public static final ImageTypeSpecifier IMAGE_TYPE_SPECIFIER = ImageTypeSpecifier.createFromBufferedImageType(IMAGE_TYPE);

    private HeicImage image;

    protected HEICImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        image = null;
    }

    @Override
    public void reset() {
        super.reset();
        image = null;
    }

    @Override
    public int getNumImages(boolean allowSearch) {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        return (int) frame(imageIndex).getWidth();
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        return (int) frame(imageIndex).getHeight();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return List.of(IMAGE_TYPE_SPECIFIER).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        final HeicImageFrame frame = frame(imageIndex);
        final int width = (int) frame.getWidth();
        final int height = (int) frame.getHeight();
        final int[] pixels;
        try {
            pixels = frame.getInt32Array(PixelFormat.Argb32);
        } catch (RuntimeException e) {
            throw new IIOException("Failed to decode HEIC frame", e);
        }

        final BufferedImage image = new BufferedImage(width, height, IMAGE_TYPE);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static void checkIndex(int imageIndex) {
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("Only one image is supported, index: " + imageIndex);
        }
    }

    private HeicImage image() throws IIOException {
        if (image == null) {
            if (!(input instanceof final ImageInputStream stream)) {
                throw new IllegalStateException("Input not set");
            }
            try {
                image = HeicImage.load(new ImageInputIOStream(stream));
            } catch (RuntimeException e) {
                throw new IIOException("Failed to decode HEIC image", e);
            }
        }
        return image;
    }

    private HeicImageFrame frame(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        final HeicImageFrame frame = image().getDefaultFrame();
        if (frame == null) {
            throw new IIOException("HEIC image has no default frame");
        }
        return frame;
    }

}
