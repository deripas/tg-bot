package com.github.deripas.tgbot.receipt.web;

import com.github.deripas.tgbot.receipt.persistence.ReceiptRepository;
import com.github.deripas.tgbot.receipt.persistence.ReceiptStats;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Controller
@RequestMapping("/receipts")
public class ReceiptController {

    private static final int LATEST = 100;
    private static final int MONTHS = 12;
    private static final int STORES = 10;

    private final ReceiptRepository repository;

    public ReceiptController(ReceiptRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public String list(Model model) {
        final List<ReceiptStats> months = repository.totalsByMonth(MONTHS);
        model.addAttribute("receipts", repository.findLatest(LATEST));
        model.addAttribute("months", months);
        // для ширины столбиков диаграммы
        model.addAttribute("monthMax", months.stream().map(ReceiptStats::total).max(BigDecimal::compareTo).orElse(BigDecimal.ONE));
        model.addAttribute("stores", repository.totalsByStore(STORES));
        return "receipts/list";
    }

    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable long id, Model model) {
        model.addAttribute("receipt", repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND)));
        return "receipts/detail";
    }

    @GetMapping("/{id:\\d+}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        return repository.findImage(id)
                .map(image -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(image))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable long id) {
        repository.delete(id);
        return "redirect:/receipts";
    }
}
