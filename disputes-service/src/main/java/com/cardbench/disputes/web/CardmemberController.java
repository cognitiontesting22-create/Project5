package com.cardbench.disputes.web;

import java.math.BigDecimal;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import com.cardbench.disputes.service.DirectoryService;
import com.cardbench.disputes.service.DisputeService;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CardmemberController {
    private final DirectoryService directory;
    private final DisputeService disputes;

    public CardmemberController(DirectoryService directory, DisputeService disputes) {
        this.directory = directory;
        this.disputes = disputes;
    }

    @GetMapping("/cardmembers/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable String id, Model model) {
        model.addAttribute("cardmember", directory.getCardmember(id));
        model.addAttribute("cards", directory.cardsForCardmember(id));
        model.addAttribute("transactions", directory.listTransactions(id, null));
        model.addAttribute("disputes", disputes.listDisputesForCardmember(id));
        return "cardmember";
    }

    @PostMapping("/cardmembers/{id}/disputes")
    public String fileDispute(
            @PathVariable String id,
            @RequestParam String transactionId,
            @RequestParam BigDecimal amount,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        directory.getCardmember(id);
        var dispute = disputes.fileDispute(transactionId, amount, reason);
        redirectAttributes.addFlashAttribute("notice", "Dispute filed.");
        return "redirect:/analyst/disputes/" + dispute.getId();
    }

    @PostMapping("/cardmembers/{id}/disputes/{disputeId}/withdraw")
    public String withdrawDispute(
            @PathVariable String id,
            @PathVariable Long disputeId,
            RedirectAttributes redirectAttributes) {
        directory.getCardmember(id);
        disputes.withdrawDisputeForCardmember(disputeId, id);
        redirectAttributes.addFlashAttribute("notice", "Dispute withdrawn.");
        return "redirect:/cardmembers/" + id;
    }
}
