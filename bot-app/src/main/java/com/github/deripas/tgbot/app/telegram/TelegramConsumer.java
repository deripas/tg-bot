package com.github.deripas.tgbot.app.telegram;

import com.github.deripas.tgbot.api.CommandInfo;
import com.github.deripas.tgbot.app.services.CommandDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.List;
import java.util.Optional;

/**
 * Receives Telegram updates via long polling and passes commands to the {@link CommandDispatcher}.
 * On start it also publishes the command list to the bot menu.
 * Commands addressed to other bots ({@code /name@other_bot}) are ignored. Only known users may run commands:
 * the sender is found among the {@link TelegramUserService}, the command gets our user id.
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramConsumer extends DefaultLongPollingUpdateConsumer implements SmartLifecycle {

    static final String ACCESS_DENIED = "Access denied. Your Telegram id: %d";
    static final String COMMAND_FAILED = "Failed to execute /%s, see the logs for details.";

    private final String token;
    private final TelegramClient client;
    private final TelegramUserService users;
    private final CommandDispatcher dispatcher;

    private TelegramBotsLongPollingApplication application;
    private String username;

    @Override
    public void start() {
        final List<BotCommand> commands = dispatcher.getCommands().stream()
                .map(command -> new BotCommand(command.name(), command.description()))
                .toList();
        try {
            username = client.execute(new GetMe()).getUserName();
            client.execute(new SetMyCommands(commands));
            application = new TelegramBotsLongPollingApplication();
            application.registerBot(token, this);
            log.info("Telegram bot @{} started, commands: {}", username,
                    dispatcher.getCommands().stream().map(CommandInfo::name).toList());
        } catch (Exception e) {
            throw new IllegalStateException("failed to start Telegram bot", e);
        }
    }

    @Override
    public void stop() {
        if (application == null) {
            return;
        }
        try {
            application.close();
        } catch (Exception e) {
            log.warn("failed to stop Telegram bot", e);
        } finally {
            application = null;
        }
    }

    @Override
    public boolean isRunning() {
        return application != null;
    }

    /**
     * Handles a single update. Called by the library on a separate thread for every update.
     */
    @Override
    public void consume(Update update) {
        final Message message = update.getMessage();
        if (message == null || message.getFrom() == null) {
            return;
        }
        final String text = message.hasText()
                ? message.getText()
                : message.getCaption();
        TelegramParsedCommand.parse(text)
                .filter(command -> command.isAddressedTo(username))
                .ifPresent(command -> dispatch(message, command));
    }

    private void dispatch(Message message, TelegramParsedCommand command) {
        final long tgUserId = message.getFrom().getId();
        final Optional<Long> userId = users.findUserId(tgUserId);
        if (userId.isEmpty()) {
            log.warn("access denied for Telegram user {} to command /{}", tgUserId, command.name());
            replyQuietly(new TelegramCommandContext(client, message, command.args(), 0), ACCESS_DENIED.formatted(tgUserId));
            return;
        }
        log.info("command /{} from user {} (Telegram user {}) in chat {}", command.name(), userId.get(), tgUserId,
                message.getChatId());
        final TelegramCommandContext context = new TelegramCommandContext(client, message, command.args(), userId.get());
        try {
            dispatcher.handle(command.name(), context);
        } catch (Exception e) {
            log.error("failed to handle command /{}", command.name(), e);
            replyQuietly(context, COMMAND_FAILED.formatted(command.name()));
        }
    }

    private static void replyQuietly(TelegramCommandContext context, String text) {
        try {
            context.reply(text);
        } catch (Exception e) {
            log.warn("failed to send error reply", e);
        }
    }
}
