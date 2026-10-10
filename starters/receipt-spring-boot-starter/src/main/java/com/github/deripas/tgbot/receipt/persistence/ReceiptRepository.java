package com.github.deripas.tgbot.receipt.persistence;

import com.github.deripas.tgbot.receipt.recognition.Recognition;
import com.github.deripas.tgbot.receipt.recognition.RecognizedReceipt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public class ReceiptRepository {

    private static final String RECEIPT_COLUMNS = """
            id, store, tax_id, date_time_raw, purchased_at, total, payment_method,
            vat_amount, receipt_number, model, created_at""";

    private final JdbcClient jdbc;

    /** {@code migrator} - только для порядка инициализации: таблицы должны существовать до первого запроса. */
    public ReceiptRepository(JdbcClient jdbc, ReceiptSchemaMigrator migrator) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long save(long chatId, long userId, Recognition recognition) {
        final RecognizedReceipt receipt = recognition.receipt();
        final long id = jdbc.sql("""
                        insert into receipt (chat_id, user_id, store, tax_id, date_time_raw, purchased_at, total,
                                             payment_method, vat_amount, receipt_number, model, raw_json)
                        values (:chatId, :userId, :store, :taxId, :dateTimeRaw, :purchasedAt, :total,
                                :paymentMethod, :vatAmount, :receiptNumber, :model, :rawJson)
                        returning id""")
                .param("chatId", chatId)
                .param("userId", userId)
                .param("store", receipt.store())
                .param("taxId", receipt.taxId())
                .param("dateTimeRaw", receipt.dateTime())
                .param("purchasedAt", ReceiptDates.parse(receipt.dateTime()).map(Timestamp::valueOf).orElse(null))
                .param("total", money(receipt.total()))
                .param("paymentMethod", receipt.paymentMethod())
                .param("vatAmount", money(receipt.vatAmount()))
                .param("receiptNumber", receipt.receiptNumber())
                .param("model", recognition.model())
                .param("rawJson", recognition.rawJson())
                .query(Long.class)
                .single();

        final List<RecognizedReceipt.Item> items = receipt.items();
        for (int position = 0; position < items.size(); position++) {
            final RecognizedReceipt.Item item = items.get(position);
            jdbc.sql("""
                            insert into receipt_item (receipt_id, position, name, price, quantity, total)
                            values (:receiptId, :position, :name, :price, :quantity, :total)""")
                    .param("receiptId", id)
                    .param("position", position + 1)
                    .param("name", item.name())
                    .param("price", money(item.price()))
                    .param("quantity", item.quantity() == null ? null : BigDecimal.valueOf(item.quantity()))
                    .param("total", money(item.total()))
                    .update();
        }
        jdbc.sql("insert into receipt_image (receipt_id, mime_type, content) values (:receiptId, 'image/jpeg', :content)")
                .param("receiptId", id)
                .param("content", recognition.jpeg())
                .update();
        return id;
    }

    public List<StoredReceipt> findLatest(int limit) {
        return jdbc.sql("select " + RECEIPT_COLUMNS + " from receipt order by coalesce(purchased_at, created_at::timestamp) desc, id desc limit :limit")
                .param("limit", limit)
                .query((rs, row) -> receipt(rs, List.of()))
                .list();
    }

    public Optional<StoredReceipt> findById(long id) {
        final List<StoredReceipt.Item> items = jdbc.sql("""
                        select position, name, price, quantity, total
                        from receipt_item where receipt_id = :id order by position""")
                .param("id", id)
                .query((rs, row) -> new StoredReceipt.Item(rs.getInt("position"), rs.getString("id"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("quantity"), rs.getBigDecimal("total")))
                .list();
        return jdbc.sql("select " + RECEIPT_COLUMNS + " from receipt where id = :id")
                .param("id", id)
                .query((rs, row) -> receipt(rs, items))
                .optional();
    }

    public Optional<byte[]> findImage(long id) {
        return jdbc.sql("select content from receipt_image where receipt_id = :id")
                .param("id", id)
                .query(byte[].class)
                .optional();
    }

    public boolean delete(long id) {
        return jdbc.sql("delete from receipt where id = :id").param("id", id).update() > 0;
    }

    /** Расходы по месяцам; дата покупки с чека, а если не распознана - дата добавления. */
    public List<ReceiptStats> totalsByMonth(int months) {
        return jdbc.sql("""
                        select to_char(date_trunc('month', coalesce(purchased_at, created_at::timestamp)), 'YYYY-MM') as label,
                               count(*) as count, coalesce(sum(total), 0) as total
                        from receipt
                        group by 1 order by 1 desc limit :months""")
                .param("months", months)
                .query(ReceiptRepository::stats)
                .list();
    }

    public List<ReceiptStats> totalsByStore(int limit) {
        return jdbc.sql("""
                        select coalesce(store, '?') as label, count(*) as count, coalesce(sum(total), 0) as total
                        from receipt
                        group by 1 order by 3 desc limit :limit""")
                .param("limit", limit)
                .query(ReceiptRepository::stats)
                .list();
    }

    private static ReceiptStats stats(ResultSet rs, int row) throws SQLException {
        return new ReceiptStats(rs.getString("label"), rs.getLong("count"), rs.getBigDecimal("total"));
    }

    private static StoredReceipt receipt(ResultSet rs, List<StoredReceipt.Item> items) throws SQLException {
        final Timestamp purchasedAt = rs.getTimestamp("purchased_at");
        return new StoredReceipt(
                rs.getLong("id"),
                rs.getString("store"),
                rs.getString("tax_id"),
                rs.getString("date_time_raw"),
                purchasedAt == null ? null : purchasedAt.toLocalDateTime(),
                rs.getBigDecimal("total"),
                rs.getString("payment_method"),
                rs.getBigDecimal("vat_amount"),
                rs.getString("receipt_number"),
                rs.getString("model"),
                rs.getTimestamp("created_at").toInstant(),
                items);
    }

    private static BigDecimal money(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
