package com.github.deripas.tgbot.app.controller;

import com.github.deripas.tgbot.api.WebModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Main page of the Web UI: the first {@link WebModule} section, the others are tabs of the top bar
 * (see {@link LayoutModelAdvice}). Without sections the page tells there are none.
 */
@Controller
@RequiredArgsConstructor
class MainPageController {

    private final List<WebModule> modules;

    @GetMapping("/")
    String index() {
        return modules.isEmpty() ? "index" : "redirect:" + home(modules);
    }

    /**
     * @return path of the first section, or the main page without sections
     */
    static String home(List<WebModule> modules) {
        return modules.isEmpty() ? "/" : modules.getFirst().path();
    }
}
