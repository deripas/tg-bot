package com.github.deripas.tgbot.receipt;

import com.github.deripas.tgbot.api.*;
import com.github.deripas.tgbot.receipt.persistence.ReceiptRepository;
import com.github.deripas.tgbot.receipt.recognition.Recognition;
import com.github.deripas.tgbot.receipt.recognition.ReceiptRecognizer;
import com.github.deripas.tgbot.receipt.recognition.RecognizedReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** {@code /add_bill}: фото чека → распознавание → БД → сводка в чат. */
public class AddBillCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(AddBillCommand.class);

    static final String NO_IMAGE = "Пришлите фото чека с подписью /add_bill или ответьте /add_bill на сообщение с фото.";

    private final ReceiptRecognizer recognizer;
    private final ReceiptRepository repository;

    public AddBillCommand(ReceiptRecognizer recognizer, ReceiptRepository repository) {
        this.recognizer = recognizer;
        this.repository = repository;
    }

    @Override
    public CommandInfo info() {
        return new CommandInfo("add_bill", "распознать фото чека и сохранить");
    }

    @Override
    public void handle(CommandContext context) {
        final Optional<Attachment> image = context.attachments().stream()
                .filter(Attachment::isImage)
                .findFirst();
        if (image.isEmpty()) {
            context.reply(NO_IMAGE);
            return;
        }
        final Reply reply = context.reply("Распознаю чек…");
        try {
            final Recognition recognition = recognizer.recognize(image.get().content());
            final long id = repository.save(context.chatId(), context.userId(), recognition);
            reply.edit(summary(id, recognition.receipt(), Optional.empty()));
        } catch (Exception e) {
            log.warn("Не удалось распознать чек", e);
            reply.edit("Не удалось распознать чек: " + e.getMessage());
        }
    }

    static String summary(long id, RecognizedReceipt receipt, Optional<String> link) {
        final String header = Stream.of(receipt.store(), receipt.dateTime())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" · "));
        final String items = receipt.items().stream()
                .map(item -> "- " + item.name() + ": " + amount(item.total()))
                .collect(Collectors.joining("\n"));
        final String total = "Итого: " + amount(receipt.total())
                + (receipt.vatAmount() == null ? "" : " (НДС " + amount(receipt.vatAmount()) + ")");
        return Stream.of("Чек #" + id + " сохранён", header, items, total, link.orElse(null))
                .filter(line -> line != null && !line.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static String amount(Double value) {
        return value == null ? "?" : String.format(Locale.ROOT, "%.2f", value);
    }
}
