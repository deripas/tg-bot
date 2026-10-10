package com.github.deripas.image.heic.io;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import openize.io.IOSeekMode;
import openize.io.IOStream;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

@RequiredArgsConstructor
public class ImageInputIOStream implements IOStream {

    private final ImageInputStream stream;
    private long length = -1;

    @SneakyThrows
    @Override
    public int read(byte[] bytes) {
        return read(bytes, 0, bytes.length);
    }

    @SneakyThrows
    @Override
    public int read(byte[] bytes, int off, int len) {
        return stream.read(bytes, off, len);
    }

    @Override
    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] bytes, int off, int len) {
        throw new UnsupportedOperationException("write");
    }

    @SneakyThrows
    @Override
    public long setPosition(long position) {
        stream.seek(position);
        return getPosition();
    }

    @SneakyThrows
    @Override
    public long getPosition() {
        return stream.getStreamPosition();
    }

    @Override
    public void seek(long pos, IOSeekMode mode) {
        final long position = switch (mode) {
            case BEGIN -> pos;
            case CURRENT -> getPosition() + pos;
            case END -> getLength() + pos;
        };
        setPosition(position);
    }

    @SneakyThrows
    @Override
    public long getLength() {
        if (length == -1) {
            length = stream.length();

            if (length == -1) {
                length = length(stream);
            }
        }
        return length;
    }

    @Override
    public void setLength(long length) {
        throw new UnsupportedOperationException("setLength");
    }

    @Override
    public void close() {
        // The underlying stream is owned by ImageIO
    }

    /**
     * Reads to the end of the stream and returns the absolute end position, independent of the current position.
     */
    private static long length(ImageInputStream stream) throws IOException {
        final byte[] buffer = new byte[4096];
        stream.mark();
        int count;
        try {
            do {
                count = stream.read(buffer, 0, buffer.length);
            } while (count != -1);
            return stream.getStreamPosition();
        } finally {
            stream.reset();
        }
    }
}
