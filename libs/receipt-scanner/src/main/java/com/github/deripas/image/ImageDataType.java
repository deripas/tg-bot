package com.github.deripas.image;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ImageDataType {
    TYPE_BYTE_GRAY(1),
    TYPE_3BYTE_BGR(3),
    ;

    @Getter
    private final int channels;

}
