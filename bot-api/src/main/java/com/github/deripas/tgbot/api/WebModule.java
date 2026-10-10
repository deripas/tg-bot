package com.github.deripas.tgbot.api;

/**
 * Web UI section of a skill. Register it as a Spring bean to get a link on the main page.
 *
 * @param title link text
 * @param path  path of the section, e.g. {@code /receipts}
 */
public record WebModule(String title, String path) {
}
