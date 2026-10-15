package com.github.deripas.tgbot.receipt.web;

import com.github.deripas.tgbot.receipt.store.DuplicatePhotoException;
import com.github.deripas.tgbot.receipt.store.ReceiptService;
import com.github.deripas.tgbot.receipt.store.ReceiptStatus;
import com.github.deripas.tgbot.receipt.store.StoredReceipt;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receipts page: upload a photo, the list of receipts with their status, deletion.
 * The result of an upload or a deletion is shown on the list after the redirect.
 * The receipt is uploaded by the logged-in user, see {@link com.github.deripas.tgbot.api.WebModule};
 * without login (an application without security) it has no user.
 */
@Controller
@RequiredArgsConstructor
@RequestMapping(ReceiptController.PATH)
public class ReceiptController {

    static final String PATH = "/receipts";
    static final int LATEST = 100;

    private final ReceiptService receipts;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("receipts", receipts.latest(LATEST));
        return "receipts/list";
    }

    /**
     * Statuses of the listed receipts: the page polls them while some receipts are being read
     * and reloads when a status changes.
     */
    @GetMapping("/statuses")
    @ResponseBody
    public Map<Long, ReceiptStatus> statuses() {
        final Map<Long, ReceiptStatus> statuses = new LinkedHashMap<>();
        receipts.latest(LATEST).forEach(receipt -> statuses.put(receipt.id(), receipt.status()));
        return statuses;
    }

    @PostMapping
    public String upload(
            @RequestParam("photo") MultipartFile photo,
            Principal principal,
            RedirectAttributes redirect
    ) throws IOException {
        try {
            final Long userId = principal == null ? null : Long.valueOf(principal.getName());
            final StoredReceipt receipt = receipts.add(userId, photo.getContentType(), photo.getBytes());
            redirect.addFlashAttribute("added", receipt.id());
        } catch (DuplicatePhotoException e) {
            redirect.addFlashAttribute("duplicate", e.getReceiptId());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:" + PATH;
    }

    @PostMapping("/{id:\\d+}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        if (receipts.delete(id)) {
            redirect.addFlashAttribute("deleted", id);
        }
        return "redirect:" + PATH;
    }
}
