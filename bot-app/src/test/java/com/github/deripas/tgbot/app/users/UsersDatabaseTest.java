package com.github.deripas.tgbot.app.users;

import com.github.deripas.tgbot.app.TestDatabase;
import com.github.deripas.tgbot.app.telegram.TelegramUserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Users and Telegram accounts in the {@code app} schema, migrated by Flyway.
 */
@SpringBootTest(properties = {"tgbot.token=", "tgbot.admin=100"})
@Import(TestDatabase.class)
class UsersDatabaseTest {

    @Autowired
    UserService users;

    @Autowired
    TelegramUserService telegramUsers;

    @Autowired
    JdbcClient jdbc;

    @Test
    void createdUserHasNoCredentials() {
        final User user = users.create(false);

        assertThat(user.hasPassword()).isFalse();
        assertThat(users.findById(user.id())).contains(user);
    }

    @Test
    void credentialsAreSavedAndLoginIsUnique() {
        final User user = users.create(false);
        final User other = users.create(false);

        assertThat(users.updateCredentials(user.id(), "unique-login", "{noop}secret")).isTrue();
        assertThat(users.updateCredentials(other.id(), "unique-login", "{noop}secret")).isFalse();

        assertThat(users.findByLogin("unique-login")).contains(new User(user.id(), false, "unique-login", "{noop}secret"));
        assertThatThrownBy(() -> users.updateCredentials(-1, "nobody", "{noop}secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thereIsOneAdmin() {
        assertThat(users.findAdmin()).isPresent();
        assertThatThrownBy(() -> users.create(true)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void telegramAccountIsLinkedToOneUser() {
        final User user = users.create(false);
        final User other = users.create(false);

        telegramUsers.add(user.id(), 300);
        telegramUsers.add(user.id(), 300);

        assertThat(telegramUsers.findUserId(300)).contains(user.id());
        assertThat(telegramUsers.findUserId(301)).isEmpty();
        assertThatThrownBy(() -> telegramUsers.add(other.id(), 300))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Telegram user 300 is linked to user " + user.id());
    }

    @Test
    void tablesAreInAppSchemaWithItsFlywayHistory() {
        assertThat(jdbc.sql("select table_name from information_schema.tables where table_schema = 'app' order by 1")
                .query(String.class).list())
                .containsExactly("flyway_schema_history", "telegram_user", "users");
    }
}
