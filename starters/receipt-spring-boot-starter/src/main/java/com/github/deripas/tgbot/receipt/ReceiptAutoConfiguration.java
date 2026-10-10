package com.github.deripas.tgbot.receipt;

import com.github.deripas.tgbot.api.WebModule;
import com.github.deripas.tgbot.receipt.persistence.ReceiptRepository;
import com.github.deripas.tgbot.receipt.persistence.ReceiptSchemaMigrator;
import com.github.deripas.tgbot.receipt.recognition.ReceiptRecognizer;
import com.github.deripas.tgbot.receipt.web.ReceiptController;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

/** Навык «чеки»: команда /add_bill, хранение в Postgres, Web UI на /receipts. */
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, JdbcClientAutoConfiguration.class})
@ConditionalOnProperty(prefix = "tgbot.receipt", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ReceiptProperties.class)
@Import(ReceiptController.class)
public class ReceiptAutoConfiguration {

    @Bean
    ReceiptSchemaMigrator receiptSchemaMigrator(DataSource dataSource) {
        return new ReceiptSchemaMigrator(dataSource);
    }

    @Bean
    ReceiptRepository receiptRepository(JdbcClient jdbcClient, ReceiptSchemaMigrator migrator) {
        return new ReceiptRepository(jdbcClient, migrator);
    }

    /** Модель создаётся внутри навыка, а не бином: у других навыков может быть своя модель и свои настройки. */
    @Bean
    public ReceiptRecognizer receiptRecognizer(ReceiptProperties properties) {
        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(properties.readTimeout());
        final OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(properties.ollamaBaseUrl())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        final OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder()
                        .model(properties.model())
                        .temperature(0.0)
                        .numCtx(properties.numCtx())
                        .numPredict(properties.maxTokens())
                        .format(ReceiptRecognizer.converter().getJsonSchemaMap())
                        .disableThinking()
                        .build())
                // без повторов: повтор долгого запроса только удваивает ожидание
                .retryTemplate(new RetryTemplate(RetryPolicy.withMaxRetries(0)))
                .build();
        return new ReceiptRecognizer(chatModel, properties.model(), properties.maxImageWidth());
    }

    @Bean
    AddBillCommand addBillCommand(ReceiptRecognizer recognizer, ReceiptRepository repository) {
        return new AddBillCommand(recognizer, repository);
    }

    @Bean
    WebModule receiptsWebModule() {
        return new WebModule("Чеки", "/receipts");
    }
}
