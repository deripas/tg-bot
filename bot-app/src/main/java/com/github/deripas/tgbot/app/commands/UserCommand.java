package com.github.deripas.tgbot.app.commands;

import com.github.deripas.tgbot.api.Command;
import com.github.deripas.tgbot.api.CommandContext;
import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.telegram.TelegramUsers;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserRepository;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * {@code /user add <tg id>}: the administrator creates a user linked to the Telegram account.
 * A person learns the id from the bot: it replies with it to any command while access is denied.
 */
@RequiredArgsConstructor
public class UserCommand implements Command {

    public static final CommandInfo INFO = new CommandInfo("user", "admin: add <telegram id>");
    static final String ADMIN_ONLY = "Only the administrator can manage users.";
    static final String USAGE = "/user add <telegram id> - add a user; the bot tells the id to anyone it denies access";

    private final UserRepository users;
    private final TelegramUsers telegramUsers;

    @Override
    public CommandInfo info() {
        return INFO;
    }

    @Override
    public void handle(CommandContext context) {
        if (users.findById(context.userId()).filter(User::admin).isEmpty()) {
            context.reply(ADMIN_ONLY);
            return;
        }
        final String[] args = context.args().split("\\s+");
        if (args.length != 2 || !args[0].equals("add") || !args[1].matches("\\d{1,18}")) {
            context.reply(USAGE);
            return;
        }
        context.reply(add(Long.parseLong(args[1])));
    }

    private synchronized String add(long tgUserId) {
        final Optional<Long> existing = telegramUsers.findUserId(tgUserId);
        if (existing.isPresent()) {
            return "Telegram user " + tgUserId + " is already user " + existing.get();
        }
        final User user = users.create(false);
        telegramUsers.add(user.id(), tgUserId);
        return "User " + user.id() + " is added for Telegram user " + tgUserId;
    }
}
