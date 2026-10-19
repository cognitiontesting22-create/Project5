package com.cardbench.disputes.web;

import java.math.BigDecimal;
import java.util.Arrays;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;

import com.cardbench.disputes.service.DirectoryService;
import com.cardbench.disputes.service.DisputeService;
import com.cardbench.disputes.service.DisputeFilingException;
import com.cardbench.disputes.domain.DisputeReason;

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
        var transactions = directory.listTransactions(id, null);
        model.addAttribute("transactions", transactions);
        model.addAttribute("disputes", disputes.listDisputesForCardmember(id));
        model.addAttribute("filingBlockReasons", disputes.filingBlockReasons(transactions));
        model.addAttribute("disputeReasons", Arrays.stream(DisputeReason.values()).toList());
        return "cardmember";
    }

    @PostMapping("/cardmembers/{id}/disputes")
    public String fileDispute(
            @PathVariable String id,
            @RequestParam String transactionId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false) String description,
            RedirectAttributes redirectAttributes) {
        directory.getCardmember(id);
        try {
            var dispute = disputes.fileDispute(transactionId, amount, reasonCode, description);
            redirectAttributes.addFlashAttribute("notice", "Dispute filed.");
            return "redirect:/analyst/disputes/" + dispute.getId();
        } catch (DisputeFilingException exception) {
            redirectAttributes.addFlashAttribute("filingError", exception.getMessage());
            redirectAttributes.addFlashAttribute("filingErrorTransactionId", transactionId);
            return "redirect:/cardmembers/" + id;
        }
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
