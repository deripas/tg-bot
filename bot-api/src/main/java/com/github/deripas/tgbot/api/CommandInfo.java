package com.github.deripas.tgbot.api;

import java.util.Objects;

/**
 * Command metadata shown in the bot menu and in {@code /help}.
 *
 * @param name        command name without the leading slash, must match {@link #NAME_REGEX}
 * @param description short human-readable description, 1-256 characters
 */
public record CommandInfo(String name, String description) {

    /**
     * Allowed command name: 1-32 characters from {@code a-z}, {@code 0-9}, {@code _} (Telegram restriction).
     */
    public static final String NAME_REGEX = "[a-z0-9_]{1,32}";

    private static final int MAX_DESCRIPTION_LENGTH = 256;

    /**
     * @throws IllegalArgumentException if the name or the description violates Telegram restrictions
     */
    public CommandInfo {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (!name.matches(NAME_REGEX)) {
            throw new IllegalArgumentException("invalid command name '" + name + "', expected " + NAME_REGEX);
        }
        if (description.isBlank() || description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description of command '" + name + "' must be 1-"
                    + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }
}
