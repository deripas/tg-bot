package com.github.deripas.tgbot.api;

/**
 * Web UI section of a skill. Register it as a Spring bean to get a link on the main page.
 * The Web UI requires login: the name of the authenticated {@link java.security.Principal} is the user id,
 * the same as {@link CommandContext#userId()}.
 *
 * @param title link text
 * @param path  path of the section, e.g. {@code /receipts}
 */
public record WebModule(String title, String path) {
}
