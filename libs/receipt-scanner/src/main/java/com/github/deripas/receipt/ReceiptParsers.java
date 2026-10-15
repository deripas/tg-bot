package com.github.deripas.receipt;

import com.github.deripas.ollama.OllamaClient;
import com.github.deripas.ollama.VisionModel;
import com.github.deripas.receipt.parse.FallbackReceiptParser;
import com.github.deripas.receipt.parse.ollama.OllamaReceiptParser;
import com.github.deripas.receipt.parse.ollama.model.OllamaReceiptReader;
import com.github.deripas.receipt.parse.qr.QrReceiptParser;
import com.github.deripas.receipt.parse.qr.rs.SerbijaQrReceiptLoader;
import com.github.deripas.receipt.scan.DefaultReceiptScanner;
import com.github.deripas.receipt.scan.opencv.OpenCvReceiptDetector;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Receipt parsers put together from the stages and the tools with their defaults.
 */
@UtilityClass
public class ReceiptParsers {

    /**
     * The receipt is taken from the Tax Administration of Serbia by its fiscal QR code if the code is readable
     * in the photo, otherwise it is read with {@link VisionModel#QWEN_2_5_VL}: the receipt is found by OpenCV
     * and read in windows.
     *
     * @param ollamaBaseUrl address of Ollama, e.g. {@link OllamaClient#DEFAULT_BASE_URL}
     */
    public static ReceiptParser qrOrModel(String ollamaBaseUrl) {
        final OllamaReceiptReader reader = new OllamaReceiptReader(
                new DefaultReceiptScanner(new OpenCvReceiptDetector()),
                new OllamaClient(ollamaBaseUrl, OllamaClient.DEFAULT_TIMEOUT),
                VisionModel.QWEN_2_5_VL,
                OllamaReceiptReader.DEFAULT_OVERLAP
        );
        return new FallbackReceiptParser(
                new QrReceiptParser(List.of(new SerbijaQrReceiptLoader())),
                new OllamaReceiptParser(reader));
    }
}
