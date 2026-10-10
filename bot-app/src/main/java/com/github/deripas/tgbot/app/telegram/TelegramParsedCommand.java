package com.github.deripas.tgbot.app.telegram;

import com.github.deripas.tgbot.api.CommandInfo;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Command parsed from message text: {@code /name[@bot] [args]}.
 *
 * @param name    command name in lower case
 * @param botName bot username from {@code /name@bot}, or {@code null} if the command is not addressed to a bot
 * @param args    text after the name, stripped; empty if absent
 */
record TelegramParsedCommand(String name, String botName, String args) {

    /** Name is matched case-insensitively and lower-cased afterwards, so {@code /Help} works too. */
    private static final Pattern COMMAND = Pattern.compile(
            "^/(" + CommandInfo.NAME_REGEX + ")(?:@(\\w+))?(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * @param text message text or caption, may be {@code null}
     * @return parsed command, or empty if the text is not a command
     */
    static Optional<TelegramParsedCommand> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        final Matcher matcher = COMMAND.matcher(text.strip());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        final String name = matcher.group(1).toLowerCase(Locale.ROOT);
        final String args = matcher.group(3) == null ? "" : matcher.group(3).strip();
        return Optional.of(new TelegramParsedCommand(name, matcher.group(2), args));
    }

    /**
     * In group chats {@code /name@other_bot} is meant for another bot and must be ignored.
     *
     * @param username username of this bot
     * @return {@code true} if the command has no bot mention or mentions this bot
     */
    boolean isAddressedTo(String username) {
        return botName == null || botName.equalsIgnoreCase(username);
    }
}
