package com.github.deripas.tgbot.app.users;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * User of the bot: the owner of the data in all skills. Channels such as Telegram keep their own accounts
 * linked to it.
 *
 * @param id           user id, {@code user_id} in the skills; {@code null} until saved
 * @param admin        the administrator, created on startup
 * @param login        login to the Web UI, in lower case; {@code null} until the user chooses it
 *                     on the first login with a link from the bot
 * @param passwordHash hash of the Web UI password with the id of its algorithm; {@code null} with the login
 */
@Table(schema = "app", name = "users")
public record User(@Id Long id, boolean admin, String login, String passwordHash) {

    /**
     * @return whether the user can log in to the Web UI with a password
     */
    public boolean hasPassword() {
        return passwordHash != null;
    }
}
