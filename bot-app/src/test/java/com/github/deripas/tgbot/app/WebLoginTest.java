package com.github.deripas.tgbot.app;

import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Users are kept in memory for the whole context: every test creates its own.
 */
@SpringBootTest(properties = {"tgbot.token=", "tgbot.admin=100"})
@AutoConfigureMockMvc
class WebLoginTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    OneTimeTokenService tokens;

    @Autowired
    UserRepository users;

    @Test
    void anonymousIsSentToLoginPage() throws Exception {
        mvc.perform(get("/receipts"))
                .andExpect(redirectedUrl("/login"));
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(not(containsString("/login/dev"))));
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void firstLinkFromBotLeadsToAccountPage() throws Exception {
        final String token = token(users.create(false));

        // the link only shows a page with the token: the link preview of Telegram does not use it up
        mvc.perform(get("/login/ott").param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/login/ott\"")))
                .andExpect(unauthenticated());
        mvc.perform(post("/login/ott").param("token", token).with(csrf()))
                .andExpect(redirectedUrl("/account"))
                .andExpect(authenticated());
        mvc.perform(post("/login/ott").param("token", token).with(csrf()))
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated());
    }

    @Test
    void chosenPasswordLogsIn() throws Exception {
        final User user = users.create(false);

        mvc.perform(post("/account").with(user(name(user))).with(csrf())
                        .param("login", " Anna ").param("password", "secret-123").param("confirmation", "secret-123"))
                // once saved, straight to the first section
                .andExpect(redirectedUrl("/receipts"))
                .andExpect(flash().attribute("saved", true));

        assertThat(users.findById(user.id()).orElseThrow().login()).isEqualTo("anna");
        mvc.perform(formLogin().user("ANNA").password("secret-123"))
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(name(user)));
        mvc.perform(formLogin().user("anna").password("wrong-password"))
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());
        // with a password the link from the bot leads to the main page
        mvc.perform(post("/login/ott").param("token", token(user)).with(csrf()))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void sectionsAreTabsOfTopBar() throws Exception {
        final User user = users.create(false);
        users.updateCredentials(user.id(), "boris", "{noop}secret-123");

        mvc.perform(get("/").with(user(name(user))))
                .andExpect(redirectedUrl("/receipts"));
        mvc.perform(get("/receipts").with(user(name(user))))
                .andExpect(content().string(containsString("<a href=\"/receipts\" class=\"tab active\">Чеки</a>")))
                .andExpect(content().string(containsString(">boris</a>")))
                .andExpect(content().string(containsString("action=\"/logout\"")));
        mvc.perform(get("/receipts").with(user(name(user))).flashAttr("saved", true))
                .andExpect(content().string(containsString("Пароль сохранён")));
    }

    @Test
    void wrongCredentialsAreNotSaved() throws Exception {
        final User user = users.create(false);
        final User other = users.create(false);
        users.updateCredentials(other.id(), "taken", "{noop}secret-123");

        assertCredentialsError(user, "ab", "secret-123", "secret-123", "login");
        assertCredentialsError(user, "anna", "short", "short", "password");
        assertCredentialsError(user, "anna", "secret-123", "secret-124", "confirmation");
        assertCredentialsError(user, "Taken", "secret-123", "secret-123", "taken");
        assertThat(users.findById(user.id()).orElseThrow().hasPassword()).isFalse();
    }

    @Test
    void userWithoutPasswordCannotUseForm() throws Exception {
        mvc.perform(formLogin().user(name(users.create(false))).password(""))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void tokenOfUnknownUserIsRejected() throws Exception {
        final String token = tokens.generate(new GenerateOneTimeTokenRequest("999")).getTokenValue();

        mvc.perform(post("/login/ott").param("token", token).with(csrf()))
                .andExpect(redirectedUrl("/login?expired"))
                .andExpect(unauthenticated());
    }

    @Test
    void devLoginIsOffWithoutDevProfile() throws Exception {
        mvc.perform(get("/login/dev"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokensAreNotGeneratedInWebUi() throws Exception {
        mvc.perform(post("/ott/generate").param("username", "1").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void loggedInUserUploadsWithCsrf() throws Exception {
        final MockMultipartFile photo = new MockMultipartFile("photo", "r.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mvc.perform(multipart("/receipts").file(photo).with(user("1")))
                .andExpect(status().isForbidden());
        mvc.perform(multipart("/receipts").file(photo).with(user("1")).with(csrf()))
                .andExpect(redirectedUrl("/receipts"));
        mvc.perform(get("/receipts").with(user("1")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    private void assertCredentialsError(User user, String login, String password, String confirmation, String error)
            throws Exception {
        mvc.perform(post("/account").with(user(name(user))).with(csrf())
                        .param("login", login).param("password", password).param("confirmation", confirmation))
                .andExpect(flash().attribute("error", error));
    }

    private String token(User user) {
        return tokens.generate(new GenerateOneTimeTokenRequest(name(user))).getTokenValue();
    }

    private static String name(User user) {
        return String.valueOf(user.id());
    }
}
