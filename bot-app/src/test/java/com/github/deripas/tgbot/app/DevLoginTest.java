package com.github.deripas.tgbot.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

/**
 * Login without the bot and its token: the administrator exists even without {@code tgbot.admin}.
 */
@SpringBootTest(properties = "tgbot.token=")
@Import(TestDatabase.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DevLoginTest {

    @Autowired
    MockMvc mvc;

    @Test
    void logsInAsAdministrator() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(content().string(containsString("href=\"/login/dev\"")));

        final String location = mvc.perform(get("/login/dev"))
                .andReturn().getResponse().getRedirectedUrl();
        final String token = location.replaceFirst(".*token=", "");

        mvc.perform(post("/login/ott").param("token", token).with(csrf()))
                .andExpect(redirectedUrl("/account"))
                .andExpect(authenticated().withUsername("1").withRoles("USER", "ADMIN"));
    }
}
