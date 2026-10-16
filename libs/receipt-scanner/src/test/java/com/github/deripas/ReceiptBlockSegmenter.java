package com.github.deripas;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

public class ReceiptBlockSegmenter {

    public static List<Rect> getReceiptBlocks(Mat srcGray) {
        // 1. Адаптивная бинаризация (инвертированная: текст - белый, фон - черный)
        Mat bin = new Mat();
        Imgproc.adaptiveThreshold(srcGray, bin, 255,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 15, 10);

        // 2. Морфологическое ядро:
        // Делаем ядро высоким по вертикали (например, 15px), чтобы "склеить"
        // близко стоящие строки переноса одного товара.
        // И широким по горизонтали (например, 30px), чтобы связать название и цену.
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(20, 20));
        Mat dilated = new Mat();
        Imgproc.dilate(bin, dilated, kernel);

        // 3. Поиск контуров получившихся "блоков"
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        List<Rect> blockBoxes = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            Rect rect = Imgproc.boundingRect(contour);
            // Фильтруем совсем мелкий шум
            if (rect.height > 10 && rect.width > 10) {
                blockBoxes.add(rect);
            }
        }

        // Сортируем блоки сверху вниз (по Y)
        blockBoxes.sort((r1, r2) -> Integer.compare(r1.y, r2.y));

        return blockBoxes;
    }
}