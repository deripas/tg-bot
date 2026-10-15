package com.github.deripas.tgbot.app.controller;

import com.github.deripas.tgbot.api.WebModule;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.Locale;

/**
 * Login and password of the Web UI: chosen on the first login with a link from the bot, changed later.
 * Errors are codes, the page shows their text; once saved, the user goes to the main page, the top bar tells it is saved.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping(AccountController.PATH)
class AccountController {

    static final String PATH = "/account";
    static final String LOGIN_PATTERN = "[a-z0-9_.-]{3,32}";
    static final int MIN_PASSWORD_LENGTH = 8;

    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final List<WebModule> modules;

    @GetMapping
    String account(Principal principal, Model model) {
        model.addAttribute("user", user(principal));
        return "account";
    }

    @PostMapping
    String save(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam String confirmation,
            Principal principal,
            RedirectAttributes redirect
    ) {
        final String normalized = login.strip().toLowerCase(Locale.ROOT);
        final String error = error(normalized, password, confirmation);
        if (error == null && !users.updateCredentials(user(principal).id(), normalized, passwordEncoder.encode(password))) {
            redirect.addFlashAttribute("error", "taken");
        } else if (error != null) {
            redirect.addFlashAttribute("error", error);
        } else {
            redirect.addFlashAttribute("saved", true);
            return "redirect:" + MainPageController.home(modules);
        }
        return "redirect:" + PATH;
    }

    private static String error(String login, String password, String confirmation) {
        if (!login.matches(LOGIN_PATTERN)) {
            return "login";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "password";
        }
        if (!password.equals(confirmation)) {
            return "confirmation";
        }
        return null;
    }

    private User user(Principal principal) {
        final long id = Long.parseLong(principal.getName());
        return users.findById(id).orElseThrow(() -> new IllegalStateException("Unknown user " + id));
    }
}
