package com.github.deripas.tgbot.receipt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Настройки навыка «чеки».
 *
 * @param enabled       включить навык (команда, Web UI, миграции)
 * @param ollamaBaseUrl адрес Ollama
 * @param model         vision-модель; по исследованию лучшая до 8 ГБ - qwen2.5vl:7b
 * @param maxImageWidth ширина, до которой уменьшается фото перед распознаванием
 * @param numCtx        контекст модели: картинка ~2000 токенов + схема + ответ
 * @param maxTokens     лимит ответа: JSON на несколько сотен токенов, лимит защищает от зацикливания
 * @param readTimeout   распознавание идёт секунды-минуты; клиент по умолчанию обрывает запрос через 10 с
 */
@ConfigurationProperties("tgbot.receipt")
public record ReceiptProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("http://localhost:11434") String ollamaBaseUrl,
        @DefaultValue("qwen2.5vl:7b") String model,
        @DefaultValue("1200") int maxImageWidth,
        @DefaultValue("4096") int numCtx,
        @DefaultValue("1024") int maxTokens,
        @DefaultValue("5m") Duration readTimeout) {
}
