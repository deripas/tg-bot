package com.github.deripas.image.heic;

import com.github.deripas.image.heic.io.BytesIOStream;
import openize.heic.decoder.HeicImage;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Locale;

public class HEICImageReaderSpi extends ImageReaderSpi {

    public HEICImageReaderSpi() {
        super(
                "openize",
                "26.5",
                new String[]{"heic", "HEIC", "heif", "HEIF"},
                new String[]{"heic", "heif"},
                new String[]{"image/heic", "image/heif"},
                HEICImageReader.class.getCanonicalName(),
                new Class<?>[]{ImageInputStream.class},
                null,
                false,
                null,
                null,
                null, null,
                false,
                null,
                null,
                null, null
        );
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof final ImageInputStream stream)) {
            return false;
        }
        final byte[] header = readHeader(stream);
        try {
            return HeicImage.canLoad(new BytesIOStream(header));
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new HEICImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return HEICImageReader.class.getSimpleName();
    }

    private static byte[] readHeader(ImageInputStream stream) throws IOException {
        byte[] header = new byte[16];
        stream.mark();
        try {
            stream.readFully(header);
        } finally {
            stream.reset();
        }
        return header;
    }
}
