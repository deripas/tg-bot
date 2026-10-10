package com.github.deripas.image.function;

import org.opencv.core.Mat;

import java.util.function.Function;

/**
 * One step of an {@link com.github.deripas.image.ImageTransform}: decides by the image it gets,
 * since its size and type are known only when the steps before it have run.
 * <p>
 * A step passes its result on by calling {@code function} and releases the images it created after that call
 * returns, so each step owns its images and can use as many as it needs.
 */
@FunctionalInterface
public interface MatFunction {

    /**
     * @param in       image of the previous step; must not be modified or released
     * @param function receives the result of this step, which lives only during the call; may be called more
     *                 than once
     * @return what {@code function} returned
     */
    <T> T apply(Mat in, Function<Mat, T> function);

    default <T> T response(Mat mat, Function<Mat, T> function) {
        return function.apply(mat);
    }

    /**
     * Passes a new empty image to {@code function} and releases it after the call.
     */
    default <T> T withMat(Function<Mat, T> function) {
        return withMat(new Mat(), function);
    }

    /**
     * Passes the {@code mat} to {@code function} and releases it after the call.
     */
    default <T> T withMat(Mat mat, Function<Mat, T> function) {
        try {
            return function.apply(mat);
        } finally {
            mat.release();
        }
    }
}
