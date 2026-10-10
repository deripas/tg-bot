package com.github.deripas.image.function;

import lombok.ToString;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.function.Function;

@ToString
public class GrayMatFunction implements MatFunction {

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        if (in.channels() == 1) {
            return response(in, function);
        }
        return withMat(out -> {
            Imgproc.cvtColor(in, out, Imgproc.COLOR_BGR2GRAY);
            return response(out, function);
        });
    }
}
