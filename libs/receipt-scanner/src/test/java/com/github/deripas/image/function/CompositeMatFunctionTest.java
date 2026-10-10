package com.github.deripas.image.function;

import com.github.deripas.image.Rotate;
import lombok.RequiredArgsConstructor;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_java;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeMatFunctionTest {

    static {
        Loader.load(opencv_java.class);
    }

    @Test
    void testNoStepsPassesInput() {
        final Mat in = image();
        try {
            assertSame(in, new CompositeMatFunction(List.of()).apply(in, mat -> mat));
        } finally {
            in.release();
        }
    }

    @Test
    void testStepsRunInOrder() {
        final List<String> log = new ArrayList<>();
        final CompositeMatFunction function = new CompositeMatFunction(List.of(
                new Record("a", log), new Record("b", log), new Record("c", log)));

        run(function, mat -> log.add("end"));

        assertEquals(List.of("a", "b", "c", "end"), log);
    }

    @Test
    void testNextStepsRunOnEveryCall() {
        final List<String> log = new ArrayList<>();
        final CompositeMatFunction function = new CompositeMatFunction(List.of(
                new Record("a", log), new Twice(), new Record("b", log), new Record("c", log)));

        run(function, mat -> log.add("end"));

        assertEquals(List.of("a", "b", "c", "end", "b", "c", "end"), log);
    }

    @Test
    void testStepGetsResultOfPreviousStep() {
        final CompositeMatFunction function = new CompositeMatFunction(List.of(
                new GrayMatFunction(), ResizeMatFunction.scale(0.5), new RectangleMatFunction(0, 0, 10, 20)));

        final int[] size = run(function, mat -> new int[]{mat.cols(), mat.rows(), mat.channels()});

        assertEquals(10, size[0]);
        assertEquals(20, size[1]);
        assertEquals(1, size[2]);
    }

    @Test
    void testCreatedImagesAreReleased() {
        final List<Mat> seen = new ArrayList<>();
        final CompositeMatFunction function = new CompositeMatFunction(List.of(
                new GrayMatFunction(), new Keep(seen),
                new RectangleMatFunction(0, 0, 10, 20), new Keep(seen),
                new RotateMatFunction(Rotate.ROTATE_180), new Keep(seen)));

        run(function, mat -> null);

        assertEquals(3, seen.size());
        seen.forEach(mat -> assertTrue(mat.empty(), "released"));
    }

    @Test
    void testCreatedImagesAreReleasedOnFailure() {
        final List<Mat> seen = new ArrayList<>();
        final CompositeMatFunction function = new CompositeMatFunction(List.of(
                new GrayMatFunction(), new Keep(seen), new RectangleMatFunction(0, 0, 10, 20), new Keep(seen)));

        assertThrows(IllegalStateException.class, () -> run(function, mat -> {
            throw new IllegalStateException();
        }));

        assertEquals(2, seen.size());
        seen.forEach(mat -> assertTrue(mat.empty(), "released"));
    }

    private static <T> T run(MatFunction function, Function<Mat, T> extractor) {
        final Mat in = image();
        try {
            return function.apply(in, extractor);
        } finally {
            in.release();
        }
    }

    private static Mat image() {
        return new Mat(80, 60, CvType.CV_8UC3);
    }

    /**
     * Logs its name and passes the input on.
     */
    @RequiredArgsConstructor
    private static class Record implements MatFunction {

        private final String name;
        private final List<String> log;

        @Override
        public <T> T apply(Mat in, Function<Mat, T> function) {
            log.add(name);
            return response(in, function);
        }
    }

    /**
     * Passes the input on twice, as a step trying two variants would.
     */
    private static class Twice implements MatFunction {

        @Override
        public <T> T apply(Mat in, Function<Mat, T> function) {
            response(in, function);
            return response(in, function);
        }
    }

    /**
     * Keeps the input to check later that the step which created it has released it.
     */
    @RequiredArgsConstructor
    private static class Keep implements MatFunction {

        private final List<Mat> seen;

        @Override
        public <T> T apply(Mat in, Function<Mat, T> function) {
            seen.add(in);
            return response(in, function);
        }
    }
}
