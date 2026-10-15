package com.github.deripas.tgbot.app.controller;

import com.github.deripas.tgbot.api.WebModule;
import com.github.deripas.tgbot.app.users.User;
import com.github.deripas.tgbot.app.users.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.List;

/**
 * Model of the top bar {@code layout/header} (in {@code bot-api}) for the pages of the app and of the skills.
 */
@ControllerAdvice
@RequiredArgsConstructor
class LayoutModelAdvice {

    private final List<WebModule> modules;
    private final UserRepository users;

    @ModelAttribute("modules")
    List<WebModule> modules() {
        return modules;
    }

    /**
     * @return path of the page without the context path, to mark the active tab
     */
    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /**
     * @return login of the user, {@code null} if not chosen yet or not logged in
     */
    @ModelAttribute("currentLogin")
    String currentLogin(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return users.findById(Long.parseLong(principal.getName())).map(User::login).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
