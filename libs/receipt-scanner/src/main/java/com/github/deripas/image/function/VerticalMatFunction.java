package com.github.deripas.image.function;

import com.github.deripas.image.Rotate;
import lombok.ToString;
import org.opencv.core.Mat;

import java.util.function.Function;

/**
 * Turns a horizontal image clockwise; a vertical one is kept as is.
 */
@ToString
public class VerticalMatFunction extends RotateMatFunction {

    public VerticalMatFunction() {
        super(Rotate.ROTATE_90_CLOCKWISE);
    }

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        if (in.cols() <= in.rows()) {
            return response(in, function);
        }
        return super.apply(in, function);
    }
}
