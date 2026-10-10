package com.github.deripas.tgbot.app.controller;

import com.github.deripas.tgbot.api.WebModule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Main page of the Web UI with links to {@link WebModule} sections of the skills.
 */
@Controller
@RequiredArgsConstructor
class MainPageController {

    private final List<WebModule> modules;

    @GetMapping("/")
    String index(Model model) {
        model.addAttribute("modules", modules);
        return "index";
    }
}
