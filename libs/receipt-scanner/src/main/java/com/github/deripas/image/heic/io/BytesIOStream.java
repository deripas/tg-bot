package com.github.deripas.image.heic.io;

import lombok.SneakyThrows;
import openize.io.IOSeekMode;
import openize.io.IOStream;

import java.nio.ByteBuffer;

public class BytesIOStream implements IOStream {

    private final ByteBuffer buffer;

    public BytesIOStream(byte[] bytes) {
        this.buffer = ByteBuffer.wrap(bytes);
    }

    @SneakyThrows
    @Override
    public int read(byte[] bytes) {
        return read(bytes, 0, bytes.length);
    }

    @Override
    public int read(byte[] bytes, int off, int len) {
        final int count = Math.min(len, buffer.remaining());
        if (count == 0) {
            return -1;
        }
        buffer.get(bytes, off, count);
        return count;
    }

    @Override
    public void write(byte[] bytes) {
        write(bytes, 0, bytes.length);
    }

    @Override
    public void write(byte[] bytes, int off, int len) {
        buffer.put(bytes, off, len);
    }

    @Override
    public long setPosition(long position) {
        buffer.position((int) position);
        return getPosition();
    }

    @Override
    public long getPosition() {
        return buffer.position();
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

    @Override
    public long getLength() {
        return buffer.limit();
    }

    @Override
    public void setLength(long length) {
        buffer.limit((int) length);
    }

    @Override
    public void close() {
        buffer.clear();
    }
}
