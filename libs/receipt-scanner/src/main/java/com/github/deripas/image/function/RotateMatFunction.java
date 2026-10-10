package com.github.deripas.image.function;

import com.github.deripas.image.Rotate;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.opencv.core.Core;
import org.opencv.core.Mat;

import java.util.function.Function;

@ToString
@RequiredArgsConstructor
public class RotateMatFunction implements MatFunction {

    private final Rotate rotate;

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        if (rotate == Rotate.ROTATE_NONE) {
            return response(in, function);
        }
        return withMat(out -> {
            Core.rotate(in, out, switch (rotate) {
                case ROTATE_90_CLOCKWISE -> Core.ROTATE_90_CLOCKWISE;
                case ROTATE_180 -> Core.ROTATE_180;
                case ROTATE_90_COUNTERCLOCKWISE -> Core.ROTATE_90_COUNTERCLOCKWISE;
                case ROTATE_NONE -> throw new IllegalStateException();
            });
            return response(out, function);
        });
    }
}
