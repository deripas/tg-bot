package com.github.deripas.tgbot.receipt.bot;

import com.github.deripas.tgbot.api.Attachment;
import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.receipt.store.DuplicatePhotoException;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.store.StoredReceipt;
import lombok.RequiredArgsConstructor;

import java.util.Optional;
import java.util.OptionalLong;

/**
 * {@code /bill <action>}: receipts in the chat, the same as in the Web UI.
 * <ul>
 *     <li>{@code /bill add} or just {@code /bill} with a photo or in reply to one: uploads the receipt
 *     and replies with its id;</li>
 *     <li>{@code /bill status <id>}: status of the receipt;</li>
 *     <li>{@code /bill delete <id>}: deletes the receipt.</li>
 * </ul>
 */
@RequiredArgsConstructor
public class BillCommand implements Command {

    static final CommandInfo INFO = new CommandInfo("bill", "receipts: add, status <id>, delete <id>");
    static final String USAGE = """
            /bill add - upload a receipt: send a photo with this caption or reply to a photo; /bill is enough
            /bill status <id> - status of the receipt
            /bill delete <id> - delete the receipt""";
    static final String NO_PHOTO = "Send a receipt photo with the /bill add caption or reply /bill add to a photo.";

    private final ReceiptService receipts;

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public void handle(CommandContext context) {
        final String[] args = context.args().split("\\s+");
        final String action = args[0];
        final OptionalLong id = args.length == 2 ? id(args[1]) : OptionalLong.empty();
        if (action.equals("add") && args.length == 1) {
            add(context);
        } else if (action.isEmpty() && photo(context).isPresent()) {
            add(context);
        } else if (action.equals("status") && id.isPresent()) {
            context.reply(status(id.getAsLong()));
        } else if (action.equals("delete") && id.isPresent()) {
            context.reply(delete(id.getAsLong()));
        } else {
            context.reply(USAGE);
        }
    }

    private void add(CommandContext context) {
        final Optional<Attachment> photo = photo(context);
        if (photo.isEmpty()) {
            context.reply(NO_PHOTO);
            return;
        }
        try {
            final StoredReceipt receipt = receipts.add(context.userId(), photo.get().mimeType(), photo.get().content());
            context.reply("Receipt " + receipt.id() + " is added. Check it later: /bill status " + receipt.id());
        } catch (DuplicatePhotoException e) {
            context.reply("This photo has already been uploaded: /bill status " + e.getReceiptId());
        }
    }

    private static Optional<Attachment> photo(CommandContext context) {
        return context.attachments().stream()
                .filter(Attachment::isImage)
                .findFirst();
    }

    private String status(long id) {
        return receipts.find(id)
                .map(receipt -> "Receipt " + id + ": " + receipt.status()
                        + (receipt.reason() == null ? "" : "\n" + receipt.reason()))
                .orElse("Receipt " + id + " is not found");
    }

    private String delete(long id) {
        return receipts.delete(id)
                ? "Receipt " + id + " is deleted"
                : "Receipt " + id + " is not found";
    }

    private static OptionalLong id(String arg) {
        try {
            return OptionalLong.of(Long.parseLong(arg));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
