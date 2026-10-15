package com.github.deripas.tgbot.app.controller;

import com.github.deripas.tgbot.app.users.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import org.springframework.security.authentication.ott.OneTimeTokenService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Login as the administrator without the bot, for local development: the same one-time link
 * the bot sends on {@code /login}. Only in the {@code dev} profile.
 */
@Slf4j
@Profile("dev")
@Controller
class DevLoginController {

    static final String PATH = "/login/dev";

    private final OneTimeTokenService tokens;
    private final UserService users;

    DevLoginController(OneTimeTokenService tokens, UserService users) {
        this.tokens = tokens;
        this.users = users;
        log.warn("Dev login is enabled: anyone may log in as the administrator at {}", PATH);
    }

    @GetMapping(PATH)
    String login() {
        final long admin = users.findAdmin().orElseThrow().id();
        return "redirect:/login/ott?token=" + tokens.generate(new GenerateOneTimeTokenRequest(String.valueOf(admin)))
                .getTokenValue();
    }
}
