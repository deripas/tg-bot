package com.github.deripas.tgbot.receipt.persistence;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;

import javax.sql.DataSource;

/**
 * Миграции навыка: свои скрипты и своя таблица истории Flyway.
 * Так навыки не конфликтуют номерами версий и не мешают Flyway самого приложения.
 */
public class ReceiptSchemaMigrator implements InitializingBean {

    static final String LOCATION = "classpath:db/migration/receipt";
    static final String HISTORY_TABLE = "receipt_flyway_history";

    private final DataSource dataSource;

    public ReceiptSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATION)
                .table(HISTORY_TABLE)
                // в общей БД уже могут быть таблицы других навыков - это не повод отказываться от миграции
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
