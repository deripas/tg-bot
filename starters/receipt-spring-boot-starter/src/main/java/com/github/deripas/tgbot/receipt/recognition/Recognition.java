package com.github.deripas.tgbot.receipt.recognition;

/**
 * Результат распознавания.
 *
 * @param receipt  разобранные данные
 * @param rawJson  ответ модели как есть - для отладки и повторного разбора
 * @param model    модель, которая распознавала
 * @param jpeg     изображение, отправленное модели (уменьшенный JPEG)
 */
public record Recognition(RecognizedReceipt receipt, String rawJson, String model, byte[] jpeg) {
}
