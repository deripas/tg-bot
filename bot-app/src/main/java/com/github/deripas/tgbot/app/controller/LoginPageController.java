package com.github.deripas.tgbot.app.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Login page: tells to ask the bot for a link; in the {@code dev} profile also offers {@link DevLoginController}.
 */
@Controller
@RequiredArgsConstructor
class LoginPageController {

    private final Environment environment;

    @GetMapping("/login")
    String login(Model model) {
        model.addAttribute("devLogin", environment.matchesProfiles("dev"));
        return "login";
    }
}
