package com.github.deripas.image.function;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;

import java.util.List;
import java.util.function.Function;

/**
 * Runs the steps in order, each one passing its result to the next.
 */
@Slf4j
@ToString
@RequiredArgsConstructor
public class CompositeMatFunction implements MatFunction {

    private final List<MatFunction> steps;

    @Override
    public <T> T apply(Mat in, Function<Mat, T> function) {
        return apply(0, in, function);
    }

    private <T> T apply(int index, Mat in, Function<Mat, T> function) {
        if (index == steps.size()) {
            return response(in, function);
        }
        final MatFunction step = steps.get(index);
        log.info("step {}", step);
        return step.apply(in, mat -> apply(index + 1, mat, function));
    }
}
